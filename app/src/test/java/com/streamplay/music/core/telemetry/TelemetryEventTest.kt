package com.streamplay.music.core.telemetry

import com.streamplay.source.model.RemotePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TelemetryEventTest {

    @Test
    fun `playback errors always carry a code and platform`() {
        assertEquals(mapOf("platform" to "local_or_unknown", "error_code" to "500"), PlaybackFailed(null, " ").params)
        assertEquals(mapOf("platform" to "audius", "error_code" to "2004"), PlaybackFailed(RemotePlatform.AUDIUS, 2004).params)
    }

    @Test
    fun `language changes report system when following the system`() {
        assertEquals("system", SettingsUsed(SettingsAction.APPLY_LANGUAGE, Outcome.SUCCESS, "").params["value"])
        assertEquals("zh-cn", SettingsUsed(SettingsAction.APPLY_LANGUAGE, Outcome.SUCCESS, "zh-CN").params["value"])
        assertFalse(SettingsUsed(SettingsAction.OPEN_LANGUAGE).params.containsKey("value"))
    }

    @Test
    fun `counts are never negative`() {
        assertEquals(0, SongPicked(Surface.HOME, null, -3).params["queue_size"])
        assertEquals("open_player", PlayerControlUsed(PlayerControl.OPEN_PLAYER, Surface.HOME_MINI_PLAYER).params["action"])
    }

    @Test
    fun `a failing sink never escapes`() {
        Telemetry.sink = { error("backend down") }
        Telemetry.log(ScreenViewed(Screen.HOME))
        Telemetry.sink = {}
    }
}
