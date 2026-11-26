package com.streamplay.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.streamplay.music.MainActivity
import com.streamplay.music.StreamPlayApplication
import com.streamplay.music.core.telemetry.PlaybackFailed
import com.streamplay.music.core.telemetry.Telemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground media service that exclusively owns the [ExoPlayer]. Media3 keeps the media
 * notification, lock screen and Bluetooth controls in sync with the session metadata.
 *
 * The service is also the single place that (1) skips sources the CDN refuses to serve and
 * (2) writes listening history, because playback continues while no screen is visible.
 */
class StreamPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val brokenIds = linkedSetOf<String>()
    private var lastLoggedId: String? = null

    private val container get() = (application as StreamPlayApplication).container

    private val resilience = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val player = session?.player ?: return
            val failing = player.currentMediaItem?.let { MediaItemCodec.decode(it) }
            Telemetry.log(PlaybackFailed(failing?.artistLink?.platform, error.errorCode))
            skipBrokenItem(player)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) brokenIds.clear()
        }
    }

    private val history = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) session?.player?.let(::logCurrentSong)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            session?.player?.takeIf(Player::isPlaying)?.let(::logCurrentSong)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // "In order" wraps around at the end of the queue, matching the player's order control.
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(resilience)
                addListener(history)
            }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
        container.sleepTimer.bind(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Stop the service when the user swipes the app away while nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        container.sleepTimer.unbind()
        session?.run {
            player.removeListener(resilience)
            player.removeListener(history)
            player.release()
            release()
        }
        session = null
        historyScope.cancel()
        super.onDestroy()
    }

    /** Records only when audio actually starts; pausing and resuming the same song writes nothing. */
    private fun logCurrentSong(player: Player) {
        val item = player.currentMediaItem ?: return
        if (item.mediaId.isBlank() || item.mediaId == lastLoggedId) return
        lastLoggedId = item.mediaId
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val song = MediaItemCodec.decode(item, duration)
        historyScope.launch {
            try {
                container.library.logPlay(song)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // History is a side feature; a database failure must not interrupt audio.
                Log.w(TAG, "Unable to log playback of ${song.id}", error)
            }
        }
    }

    /**
     * Jumps to the next item that has not failed yet. Broken ids are remembered until the queue
     * changes so several dead sources cannot bounce between each other in repeat mode.
     */
    private fun skipBrokenItem(player: Player) {
        player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)?.let(brokenIds::add)
        val count = player.mediaItemCount
        val next = (1..count)
            .map { step -> (player.currentMediaItemIndex + step) % count }
            .firstOrNull { player.getMediaItemAt(it).mediaId !in brokenIds }
        if (next == null) {
            player.pause()
            return
        }
        player.seekToDefaultPosition(next)
        player.prepare()
        player.play()
    }

    private companion object {
        const val TAG = "StreamPlaybackService"
    }
}
