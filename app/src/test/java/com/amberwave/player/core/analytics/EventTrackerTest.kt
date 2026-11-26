package com.amberwave.player.core.analytics

import com.amberwave.catalog.model.CatalogPlatform
import net.corekit.core.report.ReportDataManager
import net.corekit.core.report.ReporterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTrackerTest {
    @Test
    fun `playback error code is reported as string`() {
        val parameters = EventTracker.playbackErrorParameters(
            platform = CatalogPlatform.AUDIUS,
            errorCode = 2004,
        )

        assertEquals("audius", parameters["platform"])
        assertEquals("2004", parameters["error_code"])
        assertTrue(parameters["error_code"] is String)
    }

    @Test
    fun `missing or blank playback error code falls back to 500`() {
        assertEquals(
            "500",
            EventTracker.playbackErrorParameters(CatalogPlatform.JAMENDO, null)["error_code"],
        )
        assertEquals(
            "500",
            EventTracker.playbackErrorParameters(CatalogPlatform.JAMENDO, "")["error_code"],
        )
        assertEquals(
            "500",
            EventTracker.playbackErrorParameters(CatalogPlatform.JAMENDO, "   ")["error_code"],
        )
    }

    @Test
    fun `apply language always contains a normalized value`() {
        assertEquals(
            "zh-cn",
            EventTracker.settingsParameters(
                action = EventTracker.SettingsAction.APPLY_LANGUAGE,
                outcome = EventTracker.Outcome.SUCCESS,
                value = "zh-CN",
            )["value"],
        )
        assertEquals(
            "system",
            EventTracker.settingsParameters(
                action = EventTracker.SettingsAction.APPLY_LANGUAGE,
                outcome = EventTracker.Outcome.SUCCESS,
                value = null,
            )["value"],
        )
        assertEquals(
            "system",
            EventTracker.settingsParameters(
                action = EventTracker.SettingsAction.APPLY_LANGUAGE,
                outcome = EventTracker.Outcome.SUCCESS,
                value = "   ",
            )["value"],
        )
    }

    @Test
    fun `analytics entry does not dispatch events`() {
        val reporter = CapturingReporter()
        ReportDataManager.setReporters(listOf(reporter))
        try {
            EventTracker.settings(
                action = EventTracker.SettingsAction.APPLY_LANGUAGE,
                outcome = EventTracker.Outcome.SUCCESS,
                value = "de",
            )

            assertNull(reporter.eventName)
        } finally {
            ReportDataManager.setReporters(emptyList())
        }
    }

    private class CapturingReporter : ReporterData {
        var eventName: String? = null

        override fun getName() = "test"

        override fun reportData(eventName: String, data: Map<String, Any>) {
            this.eventName = eventName
        }

        override fun setCommonParams(params: Map<String, Any>) = Unit

        override fun setUserParams(params: Map<String, Any>) = Unit
    }

}
