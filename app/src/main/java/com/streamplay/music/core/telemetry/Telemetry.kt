package com.streamplay.music.core.telemetry

import com.streamplay.source.model.RemotePlatform
import java.util.Locale

/**
 * Single exit for product analytics. Callers pass enums and counts only — search terms, song titles
 * and playlist names are never reported. Reporting failures must never interrupt playback or
 * navigation, so [Telemetry.sink] swallows everything.
 */
sealed interface TelemetryEvent {
    val name: String
    val params: Map<String, Any>
}

enum class Screen { HOME, SEARCH, PLAYER, RECOMMENDED, LOCAL_MUSIC, ARTIST, FAVORITES, PLAYLIST, SETTINGS }

enum class Surface {
    HOME, HOME_MINI_PLAYER, SEARCH, PLAYER, RECOMMENDED, RECOMMENDED_MINI_PLAYER, LOCAL_MUSIC,
    LOCAL_MINI_PLAYER, ARTIST, ARTIST_MINI_PLAYER, FAVORITES, PLAYLIST, TRACK_ACTION_SHEET,
}

enum class PlayerControl {
    OPEN_PLAYER, PLAY, PAUSE, PLAY_ALL, PREVIOUS, NEXT, SEEK, QUEUE_OPEN, QUEUE_SELECT, SHARE,
    MODE_SEQUENTIAL, MODE_REPEAT_ONE, MODE_SHUFFLE, SLEEP_TIMER_SET, SLEEP_TIMER_END_OF_TRACK, SLEEP_TIMER_CANCEL,
}

enum class SongAction {
    OPEN_ARTIST, ADD_TO_PLAYLIST, DOWNLOAD, FAVORITE_ADD, FAVORITE_REMOVE, DELETE_LOCAL,
    SHOW_DETAILS, HIDE_DETAILS, SHARE_ARTIST,
}

enum class PlaylistAction { OPEN, CREATE, ADD_TRACK, REMOVE_TRACKS, DELETE }

enum class SearchStage { STARTED, SUCCESS, EMPTY, FAILURE, RETRY }

enum class SettingsAction { OPEN_LANGUAGE, APPLY_LANGUAGE, OPEN_PRIVACY_POLICY, OPEN_TERMS_OF_SERVICE }

enum class Outcome { SUCCESS, FAILURE, EMPTY, ALREADY_EXISTS, GRANTED, DENIED, OPEN_SETTINGS }

private fun Enum<*>.wire(): String = name.lowercase(Locale.ROOT)
private fun RemotePlatform?.platformWire(): String = this?.name?.lowercase(Locale.ROOT) ?: "local_or_unknown"

data class ScreenViewed(val screen: Screen) : TelemetryEvent {
    override val name = "music_screen_view"
    override val params: Map<String, Any> get() = mapOf("screen" to screen.wire())
}

data class SongPicked(val source: Surface, val platform: RemotePlatform?, val queueSize: Int) : TelemetryEvent {
    override val name = "music_track_select"
    override val params: Map<String, Any>
        get() = mapOf("source" to source.wire(), "platform" to platform.platformWire(), "queue_size" to queueSize.coerceAtLeast(0))
}

data class PlayerControlUsed(
    val control: PlayerControl,
    val surface: Surface,
    val platform: RemotePlatform? = null,
    val queueSize: Int? = null,
) : TelemetryEvent {
    override val name = "music_playback_control"
    override val params: Map<String, Any>
        get() = buildMap {
            put("action", control.wire())
            put("surface", surface.wire())
            platform?.let { put("platform", it.platformWire()) }
            queueSize?.let { put("queue_size", it.coerceAtLeast(0)) }
        }
}

data class PlaybackFailed(val platform: RemotePlatform?, val errorCode: Any?) : TelemetryEvent {
    override val name = "music_playback_error"
    override val params: Map<String, Any>
        get() = mapOf(
            "platform" to platform.platformWire(),
            "error_code" to (errorCode?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: "500"),
        )
}

data class SearchProgress(val stage: SearchStage, val queryLength: Int, val resultCount: Int? = null) : TelemetryEvent {
    override val name = "music_search"
    override val params: Map<String, Any>
        get() = buildMap {
            put("action", stage.wire())
            put("query_length", queryLength.coerceAtLeast(0))
            resultCount?.let { put("result_count", it.coerceAtLeast(0)) }
        }
}

data class SongActionTaken(
    val action: SongAction,
    val surface: Surface,
    val platform: RemotePlatform?,
    val outcome: Outcome? = null,
) : TelemetryEvent {
    override val name = "music_track_action"
    override val params: Map<String, Any>
        get() = buildMap {
            put("action", action.wire())
            put("surface", surface.wire())
            put("platform", platform.platformWire())
            outcome?.let { put("outcome", it.wire()) }
        }
}

data class PlaylistChanged(val action: PlaylistAction, val outcome: Outcome, val songCount: Int? = null) : TelemetryEvent {
    override val name = "music_playlist_action"
    override val params: Map<String, Any>
        get() = buildMap {
            put("action", action.wire())
            put("outcome", outcome.wire())
            songCount?.let { put("track_count", it.coerceAtLeast(0)) }
        }
}

data class SettingsUsed(val action: SettingsAction, val outcome: Outcome? = null, val value: String? = null) : TelemetryEvent {
    override val name = "music_settings_action"
    override val params: Map<String, Any>
        get() = buildMap {
            put("action", action.wire())
            outcome?.let { put("outcome", it.wire()) }
            val cleaned = value?.trim()?.takeIf(String::isNotEmpty)?.lowercase(Locale.ROOT)
            // apply_language always carries a value; "follow system" is reported as "system".
            if (action == SettingsAction.APPLY_LANGUAGE) put("value", cleaned ?: "system") else cleaned?.let { put("value", it) }
        }
}

data class AudioPermissionResult(val outcome: Outcome) : TelemetryEvent {
    override val name = "music_local_permission"
    override val params: Map<String, Any> get() = mapOf("outcome" to outcome.wire())
}

object Telemetry {
    /** Replace to forward events to an analytics backend; the default drops them. */
    @Volatile
    var sink: (TelemetryEvent) -> Unit = {}

    fun log(event: TelemetryEvent) {
        runCatching { sink(event) }
    }
}
