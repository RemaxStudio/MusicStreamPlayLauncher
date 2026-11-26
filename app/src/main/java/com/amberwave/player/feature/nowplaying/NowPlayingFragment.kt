package com.amberwave.player.feature.nowplaying

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.amberwave.player.LibraryGraph
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.core.navigation.navigator
import com.amberwave.player.core.playback.UpNextPresenter
import com.amberwave.player.core.playback.bindPlaybackController
import com.amberwave.player.core.playback.playbackConnection
import com.amberwave.player.core.ui.ViewBindingFragment
import com.amberwave.player.databinding.FragmentNowPlayingBinding
import com.amberwave.player.feature.collection.dialog.PlaylistDialogs
import com.amberwave.player.feature.collection.toLibraryTrack
import com.amberwave.player.core.ads.InterstitialPlacement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 全屏播放页。播放状态只来自共享的 MediaController；页面携带的新队列由 ViewModel 保证只写入一次。
 */
class NowPlayingFragment : ViewBindingFragment<FragmentNowPlayingBinding>(FragmentNowPlayingBinding::inflate) {
    private val viewModel: NowPlayingViewModel by viewModels()
    private val libraryRepository by lazy(LazyThreadSafetyMode.NONE) {
        LibraryGraph.repository(requireContext())
    }
    private val playlistDialogs by lazy(LazyThreadSafetyMode.NONE) {
        PlaylistDialogs(this, libraryRepository)
    }
    private var queuePresenter: UpNextPresenter? = null
    private var controller: MediaController? = null
    private var favoriteTrackJob: Job? = null
    private var activeQueue: List<PlayerTrack> = emptyList()
    private var userSeeking = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = renderPlayback(player)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId ?: return
            activeQueue.firstOrNull { it.id == id }?.let(::renderTrack)
            queuePresenter?.updatePlayback(id, controller?.isPlaying == true)
        }

        override fun onPlayerError(error: PlaybackException) {
            // 错误埋点由 AudioPlaybackService 在自动恢复前统一上报，页面只负责前台提示，避免重复事件。
            toast(error.localizedMessage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) EventTracker.screenView(EventTracker.Screen.PLAYER)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.playerRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        queuePresenter = UpNextPresenter(requireContext(), EventTracker.Surface.PLAYER)
        renderTrack(viewModel.state.value.track)
        configureActions()
        viewModel.state.collectWhileStarted { state ->
            binding.favorite.setImageResource(
                if (state.isFavorite) R.drawable.ic_action_favorite_filled else R.drawable.ic_transport_favorite,
            )
            renderPlaybackMode(state.playbackMode)
            controller?.let { applyPlaybackMode(it, state.playbackMode) }
        }
        playbackConnection.failures.collectWhileStarted { toast(R.string.player_invalid_track) }
        SleepTimer.state.collectWhileStarted { renderSleepTimer() }
        bindPlaybackController(playerListener) { connected ->
            controller = connected
            if (connected == null) {
                queuePresenter?.dismiss()
                binding.artwork.stopProgressAnimation()
            } else {
                onControllerConnected(connected)
            }
        }
        startProgressUpdates()
    }

    override fun onDestroyView() {
        binding.artwork.onTextVisibilityChanged = null
        binding.artwork.onFaceStateChanged = null
        // 主动清理 View target，避免解码完成回调继续持有已销毁的视图。
        Glide.with(this).clear(binding.artwork.artwork)
        queuePresenter = null
        controller = null
        super.onDestroyView()
    }

    private fun onControllerConnected(player: MediaController) {
        val requestedQueue = viewModel.consumeRequestedQueue()
        val currentTrack = viewModel.state.value.track
        if (requestedQueue.isEmpty() && player.mediaItemCount == 0) {
            toast(R.string.player_invalid_track)
            navigator.navigateUp()
            return
        }
        if (requestedQueue.isNotEmpty()) {
            activeQueue = requestedQueue
            applyRequestedQueue(player, requestedQueue, currentTrack.id)
        } else {
            // 从 Mini Player 打开或返回前台时沿用 MediaSession 已有队列，绝不重建成单曲队列。
            activeQueue = List(player.mediaItemCount) { index ->
                player.getMediaItemAt(index).toPlayerTrack(
                    if (index == player.currentMediaItemIndex) player.duration.coerceAtLeast(0L) else 0L,
                )
            }
        }
        viewModel.setPlaybackMode(
            when {
                player.repeatMode == Player.REPEAT_MODE_ONE -> PlaybackMode.REPEAT_ONE
                player.shuffleModeEnabled -> PlaybackMode.SHUFFLE
                else -> PlaybackMode.SEQUENTIAL
            },
        )
        activeQueue.firstOrNull { it.id == player.currentMediaItem?.mediaId }?.let(::renderTrack)
            ?: currentTrack.takeIf { it.id.isNotBlank() }?.let(::renderTrack)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        renderPlayback(player)
    }

    /** 从业务列表主动点歌表示播放意图：替换或定位队列后立即播放。 */
    private fun applyRequestedQueue(player: MediaController, queue: List<PlayerTrack>, targetId: String) {
        val targetIndex = queue.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
        val currentIds = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
        when {
            currentIds != queue.map(PlayerTrack::id) -> {
                player.setMediaItems(queue.map(PlayerTrack::toMediaItem), targetIndex, C.TIME_UNSET)
                player.prepare()
            }
            player.currentMediaItemIndex != targetIndex -> player.seekToDefaultPosition(targetIndex)
        }
        player.play()
    }

    private fun configureActions() {
        binding.artwork.onTextVisibilityChanged = { visible ->
            EventTracker.trackAction(
                action = if (visible) EventTracker.TrackAction.SHOW_DETAILS else EventTracker.TrackAction.HIDE_DETAILS,
                surface = EventTracker.Surface.PLAYER,
                platform = currentPlatform(),
            )
            viewModel.setTrackTextVisible(visible)
        }
        binding.artwork.onFaceStateChanged = ::renderFlipHint
        binding.flipHint.setOnClickListener { binding.artwork.performClick() }
        renderFlipHint()
        binding.back.setOnClickListener { navigator.navigateUp() }
        binding.play.setOnClickListener { controller?.let(::togglePlayback) }
        binding.previous.setOnClickListener { skip(EventTracker.PlaybackAction.PREVIOUS) { seekToPrevious() } }
        binding.next.setOnClickListener { skip(EventTracker.PlaybackAction.NEXT) { seekToNext() } }
        binding.shuffle.setOnClickListener {
            viewModel.nextPlaybackMode()
            EventTracker.playback(
                action = viewModel.state.value.playbackMode.toAnalyticsAction(),
                surface = EventTracker.Surface.PLAYER,
                platform = currentPlatform(),
                queueSize = activeQueue.size,
            )
        }
        binding.favorite.setOnClickListener { toggleFavorite() }
        binding.addToPlaylist.setOnClickListener { addToPlaylist() }
        binding.queue.setOnClickListener { controller?.let { queuePresenter?.show(it) } }
        binding.sleepTimer.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(SleepTimerSheet.TAG) == null) {
                SleepTimerSheet().show(childFragmentManager, SleepTimerSheet.TAG)
            }
        }
        binding.share.setOnClickListener {
            EventTracker.playback(EventTracker.PlaybackAction.SHARE, EventTracker.Surface.PLAYER, currentPlatform())
            shareApp()
        }
        configureSeekBar()
    }

    private fun configureSeekBar() = with(binding.seekBar) {
        onSeekStarted = { userSeeking = true }
        onSeekChanged = { fraction ->
            val duration = controller?.duration?.takeIf { it > 0 } ?: viewModel.state.value.track.durationMs
            binding.currentTime.text = formatTime((duration * fraction).toLong())
        }
        onSeekFinished = { fraction ->
            controller?.takeIf { it.duration > 0 }?.let { player ->
                player.seekTo((player.duration * fraction).toLong())
                EventTracker.playback(
                    EventTracker.PlaybackAction.SEEK,
                    EventTracker.Surface.PLAYER,
                    currentPlatform(),
                    activeQueue.size,
                )
            }
            userSeeking = false
        }
        onSeekCancelled = {
            userSeeking = false
            controller?.let(::renderProgress)
        }
    }

    private fun togglePlayback(player: Player) {
        EventTracker.playback(
            if (player.playWhenReady) EventTracker.PlaybackAction.PAUSE else EventTracker.PlaybackAction.PLAY,
            EventTracker.Surface.PLAYER,
            currentPlatform(),
            activeQueue.size,
        )
        if (player.playWhenReady) player.pause() else player.play()
    }

    /**
     * 上一首/下一首是用户明确发起的新播放请求，因此即使当前处于暂停状态也应立即播放。
     * 自动跳过不可播放歌曲的逻辑不复用这里，避免后台错误恢复意外改变用户的暂停意图。
     */
    private fun skip(action: EventTracker.PlaybackAction, skipAction: Player.() -> Unit) {
        EventTracker.playback(action, EventTracker.Surface.PLAYER, currentPlatform(), activeQueue.size)
        controller?.run {
            skipAction()
            play()
        }
    }

    private fun toggleFavorite() {
        val track = viewModel.state.value.track
        if (track.id.isBlank()) return
        val favorite = !viewModel.state.value.isFavorite
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = runCatching {
                libraryRepository.setFavorite(
                    track.toLibraryTrack(artworkFallbackRes = R.drawable.img_cover_fallback),
                    favorite = favorite,
                )
            }.fold({ EventTracker.Outcome.SUCCESS }, { EventTracker.Outcome.FAILURE })
            EventTracker.trackAction(
                action = if (favorite) EventTracker.TrackAction.FAVORITE_ADD else EventTracker.TrackAction.FAVORITE_REMOVE,
                surface = EventTracker.Surface.PLAYER,
                platform = track.artistRef?.platform,
                outcome = outcome,
            )
        }
    }

    private fun addToPlaylist() {
        val track = viewModel.state.value.track
        if (track.id.isBlank()) return
        EventTracker.trackAction(EventTracker.TrackAction.ADD_TO_PLAYLIST, EventTracker.Surface.PLAYER, track.artistRef?.platform)
        playlistDialogs.showPlaylistPicker(track.toLibraryTrack(artworkFallbackRes = R.drawable.img_cover_fallback))
    }

    private fun startProgressUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    controller?.let(::renderProgress)
                    renderSleepTimer()
                    delay(PROGRESS_INTERVAL_MS)
                }
            }
        }
    }

    private fun renderPlayback(player: Player) {
        // 缓冲期间 playWhenReady 仍为 true，按钮不会闪回播放态。
        binding.play.setImageResource(if (player.playWhenReady) R.drawable.ic_transport_pause else R.drawable.ic_transport_play)
        binding.play.contentDescription = getString(if (player.playWhenReady) R.string.home_pause else R.string.home_play)
        binding.previous.isEnabled = player.mediaItemCount > 1
        binding.next.isEnabled = player.mediaItemCount > 1
        player.currentMediaItem?.mediaId?.let { queuePresenter?.updatePlayback(it, player.isPlaying) }
        renderProgress(player)
    }

    private fun renderProgress(player: Player) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: viewModel.state.value.track.durationMs.coerceAtLeast(0L)
        val position = player.currentPosition.coerceIn(0L, duration.coerceAtLeast(0L))
        binding.currentTime.text = formatTime(position)
        binding.duration.text = formatTime(duration)
        if (!userSeeking) binding.seekBar.setProgress(if (duration > 0) position.toFloat() / duration else 0f)
        binding.artwork.updatePlayback(position, duration, player.isPlaying)
    }

    private fun renderTrack(track: PlayerTrack) {
        viewModel.setCurrentTrack(track)
        observeFavorite(track.id)
        binding.title.text = track.title
        binding.artist.text = track.artist
        val artistRef = track.artistRef
        binding.artist.isClickable = artistRef != null
        binding.artist.isFocusable = artistRef != null
        binding.artist.setOnClickListener(
            artistRef?.let { artist ->
                View.OnClickListener {
                    EventTracker.trackAction(EventTracker.TrackAction.OPEN_ARTIST, EventTracker.Surface.PLAYER, artist.platform)
                    navigator.openArtist(artist, InterstitialPlacement.PLAYER_ARTIST_NAME_ENTRY)
                }
            },
        )
        renderTrackText(track)
        // 每次视图创建和媒体切换都重新绑定 target；磁盘/内存缓存避免重复下载。
        val imageLoader = Glide.with(this)
        imageLoader.clear(binding.artwork.artwork)
        imageLoader
            .load(track.artworkUrl?.takeIf(String::isNotBlank) ?: R.drawable.img_cover_fallback)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(R.drawable.bg_stage_artwork)
            .error(R.drawable.img_cover_fallback)
            .override(MAX_ARTWORK_PIXELS, MAX_ARTWORK_PIXELS)
            .centerCrop()
            .dontAnimate()
            .into(binding.artwork.artwork)
    }

    private fun observeFavorite(trackId: String) {
        favoriteTrackJob?.cancel()
        viewModel.setFavorite(false)
        if (trackId.isBlank()) return
        favoriteTrackJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                libraryRepository.observeFavorite(trackId).collect(viewModel::setFavorite)
            }
        }
    }

    /**
     * 歌词和平台介绍共用一个可滚动区域，但通过标题明确区分语义。
     * 有歌词时优先展示歌词；Audius 常见的 HTML 介绍先转成纯文本，避免标签直接出现在 UI。
     */
    private fun renderTrackText(track: PlayerTrack) {
        val trackText = track.resolveDisplayText()
        val content = when (trackText?.type) {
            TrackTextType.LYRICS -> trackText.rawText
            TrackTextType.DESCRIPTION -> HtmlCompat.fromHtml(trackText.rawText, HtmlCompat.FROM_HTML_MODE_COMPACT)
                .toString().trim().takeIf(String::isNotEmpty)
            null -> null
        }
        val labelRes = when (trackText?.type) {
            TrackTextType.LYRICS -> R.string.player_lyrics
            TrackTextType.DESCRIPTION -> R.string.player_about_track
            null -> null
        }
        binding.artwork.setTrackText(labelRes = labelRes, content = content, showText = viewModel.state.value.isTrackTextVisible)
    }

    /** 设计稿：唱片下方“显示歌词 ›”文字入口，与点击唱片翻面等价；无歌词/介绍时隐藏。 */
    private fun renderFlipHint() {
        if (!isViewAvailable) return
        val stage = binding.artwork
        binding.flipHint.visibility = if (stage.hasTrackText) View.VISIBLE else View.INVISIBLE
        binding.flipHint.text = getString(
            when {
                stage.isShowingTrackText -> R.string.player_show_artwork
                stage.isLyricsText -> R.string.player_show_lyrics
                else -> R.string.player_show_description
            },
        )
        binding.flipHint.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_lp_chevron_small, 0)
    }

    /** 定时生效时图标切换为强调色，并在标题下方显示剩余时间（随进度循环每 500ms 刷新）。 */
    private fun renderSleepTimer() {
        val state = SleepTimer.state.value
        val active = state != SleepTimer.State.Off
        binding.sleepTimer.imageTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (active) R.color.lp_accent else R.color.selector_icon_button,
        )
        binding.sleepTimerStatus.isVisible = active
        binding.sleepTimerStatus.text = when (state) {
            SleepTimer.State.Off -> null
            SleepTimer.State.EndOfTrack -> getString(R.string.sleep_timer_status_end_of_track)
            is SleepTimer.State.Countdown ->
                getString(R.string.sleep_timer_status_remaining, SleepTimerSheet.formatRemaining(state.remainingMs()))
        }
    }

    private fun renderPlaybackMode(mode: PlaybackMode) {
        binding.shuffle.setImageResource(
            when (mode) {
                PlaybackMode.SEQUENTIAL -> R.drawable.ic_transport_sequential
                PlaybackMode.REPEAT_ONE -> R.drawable.ic_transport_repeat_one
                PlaybackMode.SHUFFLE -> R.drawable.ic_transport_shuffle
            },
        )
        binding.shuffle.contentDescription = getString(
            when (mode) {
                PlaybackMode.SEQUENTIAL -> R.string.player_sequential
                PlaybackMode.REPEAT_ONE -> R.string.player_repeat_one
                PlaybackMode.SHUFFLE -> R.string.player_shuffle
            },
        )
    }

    private fun applyPlaybackMode(player: Player, mode: PlaybackMode) {
        player.shuffleModeEnabled = mode == PlaybackMode.SHUFFLE
        player.repeatMode = if (mode == PlaybackMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
    }

    private fun PlaybackMode.toAnalyticsAction(): EventTracker.PlaybackAction = when (this) {
        PlaybackMode.SEQUENTIAL -> EventTracker.PlaybackAction.MODE_SEQUENTIAL
        PlaybackMode.REPEAT_ONE -> EventTracker.PlaybackAction.MODE_REPEAT_ONE
        PlaybackMode.SHUFFLE -> EventTracker.PlaybackAction.MODE_SHUFFLE
    }

    private fun currentPlatform() = viewModel.state.value.track.artistRef?.platform

    private fun shareApp() {
        val context = requireContext()
        val storeUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.player_share_app, getString(R.string.app_name), storeUrl))
        startActivity(Intent.createChooser(send, getString(R.string.player_share)))
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0L))
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
        const val MAX_ARTWORK_PIXELS = 720
    }
}
