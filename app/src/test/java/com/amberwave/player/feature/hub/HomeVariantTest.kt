package com.amberwave.player.feature.hub

import com.amberwave.player.R
import com.amberwave.player.core.ui.PageLoadError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVariantTest {
    @Test
    fun `missing or invalid config safely selects local mode`() {
        listOf(null, "", "unexpected", "[]").forEach { raw ->
            val resolution = resolveHomeExperienceMode(raw, HomeAudience.NATURAL)
            assertEquals(HomeVariant.LOCAL, resolution.mode)
            assertNull(resolution.selectedValue)
        }
    }

    @Test
    fun `natural and paid users select their own json branch`() {
        val raw = """{"natural":"local","paid":"online"}"""

        val natural = resolveHomeExperienceMode(raw, HomeAudience.NATURAL)
        val paid = resolveHomeExperienceMode(raw, HomeAudience.PAID)

        assertEquals("local", natural.selectedValue)
        assertEquals(HomeVariant.LOCAL, natural.mode)
        assertEquals("online", paid.selectedValue)
        assertEquals(HomeVariant.ONLINE, paid.mode)
    }

    @Test
    fun `json keys are case insensitive and experiment aliases remain supported`() {
        val raw = """{"NATURAL":"b","PAID":"a"}"""

        assertEquals(
            HomeVariant.ONLINE,
            resolveHomeExperienceMode(raw, HomeAudience.NATURAL).mode,
        )
        assertEquals(
            HomeVariant.LOCAL,
            resolveHomeExperienceMode(raw, HomeAudience.PAID).mode,
        )
    }

    @Test
    fun `missing audience branch defaults only that audience to local`() {
        val raw = """{"paid":"online"}"""

        assertEquals(
            HomeVariant.LOCAL,
            resolveHomeExperienceMode(raw, HomeAudience.NATURAL).mode,
        )
        assertEquals(
            HomeVariant.ONLINE,
            resolveHomeExperienceMode(raw, HomeAudience.PAID).mode,
        )
    }

    @Test
    fun `local mode only renders device tracks and hides online entry points`() {
        val deviceTrack = track("LOCAL:1")
        val content = HubContent(
            recommended = listOf(track("AUDIUS:online")),
            mostPlayed = listOf(track("JAMENDO:online")),
            localMusic = HubDeviceState.Loaded(listOf(deviceTrack)),
        )

        val items = buildHomeItems(HomeVariant.LOCAL, content, isOnlineLoading = false)

        assertFalse((items.first() as HubItem.Header).showSearch)
        assertTrue(items.filterIsInstance<HubItem.LocalTrack>().single().track == deviceTrack)
        assertFalse(items.any { it is HubItem.Recommended || it is HubItem.MostPlayed })
        assertFalse(
            items.filterIsInstance<HubItem.SectionTitle>().any {
                it.id == HubSectionId.RECOMMENDED || it.id == HubSectionId.MOST_PLAYED
            },
        )
    }

    @Test
    fun `online mode retains search recommendation and most played sections`() {
        val content = HubContent(
            recommended = listOf(track("AUDIUS:1")),
            mostPlayed = listOf(track("JAMENDO:1")),
            localMusic = HubDeviceState.Loaded(listOf(track("LOCAL:1"))),
        )

        val items = buildHomeItems(HomeVariant.ONLINE, content, isOnlineLoading = false)

        assertTrue((items.first() as HubItem.Header).showSearch)
        assertTrue(items.any { it is HubItem.Recommended })
        assertTrue(items.any { it is HubItem.MostPlayed })
        assertFalse(items.any { it is HubItem.LocalTrack })
    }

    @Test
    fun `online initial failure renders retry item instead of loading skeletons`() {
        val items = buildHomeItems(
            mode = HomeVariant.ONLINE,
            content = HubContent(),
            isOnlineLoading = false,
            onlineError = PageLoadError.HOME,
        )

        assertEquals(PageLoadError.HOME, items.filterIsInstance<HubItem.LoadError>().single().error)
        assertFalse(items.any { it is HubItem.RecommendedSkeleton })
        assertFalse(items.any { it is HubItem.MostPlayedSkeleton })
    }

    @Test
    fun `online retry replaces failure item with loading skeletons`() {
        val items = buildHomeItems(
            mode = HomeVariant.ONLINE,
            content = HubContent(),
            isOnlineLoading = true,
            // StateFlow emissions may briefly retain the previous error while loading turns true.
            onlineError = PageLoadError.HOME,
        )

        assertFalse(items.any { it is HubItem.LoadError })
        assertTrue(items.any { it is HubItem.RecommendedSkeleton })
        assertTrue(items.any { it is HubItem.MostPlayedSkeleton })
    }

    @Test
    fun `online refresh failure keeps already loaded music visible`() {
        val items = buildHomeItems(
            mode = HomeVariant.ONLINE,
            content = HubContent(
                recommended = listOf(track("AUDIUS:1")),
                mostPlayed = listOf(track("JAMENDO:1")),
            ),
            isOnlineLoading = false,
            onlineError = PageLoadError.HOME,
        )

        assertFalse(items.any { it is HubItem.LoadError })
        assertTrue(items.any { it is HubItem.Recommended })
        assertTrue(items.any { it is HubItem.MostPlayed })
    }

    private fun track(id: String) = HubTrack(
        id = id,
        title = "Track",
        artist = "Artist",
        artworkRes = R.drawable.placeholder_device_track,
    )
}
