package com.streamplay.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.streamplay.music.core.model.PlaybackOrder
import com.streamplay.music.core.model.Song
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable projection of the MediaSession, the single source of truth for "what is playing". */
@Immutable
data class PlaybackState(
    val isConnected: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    /** User intent: stays true while buffering, so play/pause icons never flicker. */
    val playWhenReady: Boolean = false,
    /** Audio is actually being produced; drives equalizer animations in lists. */
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val order: PlaybackOrder = PlaybackOrder.IN_ORDER,
) {
    val current: Song? get() = queue.getOrNull(currentIndex)

    /** Id of the song that should be highlighted as "playing" in lists. */
    val activeSongId: String? get() = current?.id?.takeIf { isPlaying }

    val canSkip: Boolean get() = queue.size > 1
}

/**
 * The app's one [MediaController]. It connects while the activity is started and releases when it
 * stops, exactly like a bound service. Requests issued before the controller is ready (e.g. tapping
 * a song during a cold start) are kept and applied on connection.
 */
class PlaybackConnector(context: Context) : DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private var pending: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var queuedRequest: QueueRequest? = null

    private val mutableState = MutableStateFlow(PlaybackState())
    private val mutableFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    /** Emits when the playback service cannot be reached. */
    val failures: SharedFlow<Unit> = mutableFailures.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    override fun onStart(owner: LifecycleOwner) = connect()

    override fun onStop(owner: LifecycleOwner) = release()

    /** Replaces (or re-positions) the queue and starts playback at [startId]; this is an explicit play intent. */
    fun play(songs: List<Song>, startId: String) {
        val queue = songs.asSequence().filter(Song::isPlayable).distinctBy(Song::id).take(MAX_QUEUE).toList()
        if (queue.isEmpty()) return
        val request = QueueRequest(queue, queue.indexOfFirst { it.id == startId }.coerceAtLeast(0))
        val player = controller
        if (player == null) {
            queuedRequest = request
            // Optimistic projection so the player screen can render the chosen song immediately.
            mutableState.value = mutableState.value.copy(queue = queue, currentIndex = request.index, playWhenReady = true)
            return
        }
        apply(player, request)
    }

    /** True while a request is waiting for the controller, so screens don't treat "empty" as an error. */
    val hasPendingRequest: Boolean get() = queuedRequest != null

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.playWhenReady) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    /** Explicit skips are new play requests: they resume playback even when paused. */
    fun skipToNext() = controller?.run { seekToNext(); play() }

    fun skipToPrevious() = controller?.run { seekToPrevious(); play() }

    fun skipToQueueIndex(index: Int) = controller?.run {
        if (index in 0 until mediaItemCount) {
            seekToDefaultPosition(index)
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.takeIf { it.duration > 0 }?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun setOrder(order: PlaybackOrder) {
        val player = controller ?: return
        player.shuffleModeEnabled = order == PlaybackOrder.SHUFFLE
        player.repeatMode = if (order == PlaybackOrder.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
        publish(player)
    }

    fun positionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    private fun connect() {
        if (pending != null) return
        val token = SessionToken(appContext, ComponentName(appContext, StreamPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        pending = future
        future.addListener(
            {
                if (pending !== future) return@addListener
                runCatching { future.get() }
                    .onSuccess(::onConnected)
                    .onFailure { mutableFailures.tryEmit(Unit) }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun onConnected(player: MediaController) {
        controller = player
        player.addListener(listener)
        queuedRequest?.let { apply(player, it) }
        queuedRequest = null
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
        publish(player)
    }

    private fun release() {
        controller?.removeListener(listener)
        controller = null
        pending?.let(MediaController::releaseFuture)
        pending = null
        mutableState.value = mutableState.value.copy(isConnected = false, isPlaying = false)
    }

    private fun apply(player: MediaController, request: QueueRequest) {
        val currentIds = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
        when {
            currentIds != request.songs.map(Song::id) -> {
                player.setMediaItems(request.songs.map(MediaItemCodec::encode), request.index, C.TIME_UNSET)
                player.prepare()
            }
            player.currentMediaItemIndex != request.index -> player.seekToDefaultPosition(request.index)
            player.playbackState == Player.STATE_ENDED -> player.seekToDefaultPosition(request.index)
        }
        player.play()
        publish(player)
    }

    private fun publish(player: Player) {
        val previous = mutableState.value
        val count = player.mediaItemCount
        val currentIndex = player.currentMediaItemIndex
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        // Reuse decoded songs when the queue did not change; only the current item carries a duration.
        val sameQueue = previous.queue.size == count &&
            previous.queue.indices.all { previous.queue[it].id == player.getMediaItemAt(it).mediaId }
        val queue = if (sameQueue) previous.queue else List(count) { MediaItemCodec.decode(player.getMediaItemAt(it)) }
        mutableState.value = PlaybackState(
            isConnected = true,
            queue = queue,
            currentIndex = if (count == 0) -1 else currentIndex,
            playWhenReady = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            isPlaying = player.isPlaying,
            durationMs = duration.takeIf { it > 0L } ?: queue.getOrNull(currentIndex)?.durationMs ?: 0L,
            order = when {
                player.repeatMode == Player.REPEAT_MODE_ONE -> PlaybackOrder.REPEAT_ONE
                player.shuffleModeEnabled -> PlaybackOrder.SHUFFLE
                else -> PlaybackOrder.IN_ORDER
            },
        )
    }

    private data class QueueRequest(val songs: List<Song>, val index: Int)

    private companion object {
        const val MAX_QUEUE = 100
    }
}
