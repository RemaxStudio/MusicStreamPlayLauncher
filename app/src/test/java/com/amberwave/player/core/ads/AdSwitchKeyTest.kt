package com.amberwave.player.core.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdSwitchKeyTest {
    @Test
    fun `native ad switch keys match remote configuration contract`() {
        assertEquals(
            setOf(
                "home_bottom_native",
                "search_result_bottom_native",
                "recommended_music_bottom_native",
                "local_music_bottom_native",
                "artist_detail_bottom_native",
                "playlist_detail_bottom_native",
                "favorites_bottom_native",
                "settings_bottom_native",
            ),
            setOf(
                AdSwitchKey.HOME_BOTTOM_NATIVE,
                AdSwitchKey.SEARCH_RESULT_BOTTOM_NATIVE,
                AdSwitchKey.RECOMMENDED_MUSIC_BOTTOM_NATIVE,
                AdSwitchKey.LOCAL_MUSIC_BOTTOM_NATIVE,
                AdSwitchKey.ARTIST_DETAIL_BOTTOM_NATIVE,
                AdSwitchKey.PLAYLIST_DETAIL_BOTTOM_NATIVE,
                AdSwitchKey.FAVORITES_BOTTOM_NATIVE,
                AdSwitchKey.SETTINGS_BOTTOM_NATIVE,
            ),
        )
    }

    @Test
    fun `each interstitial entry maps to its own switch key`() {
        assertEquals(
            mapOf(
                InterstitialPlacement.HOME_RECOMMENDED_MORE_ENTRY to
                    "home_recommended_more_entry_interstitial",
                InterstitialPlacement.HOME_LOCAL_PLAYLISTS_ENTRY to
                    "home_local_playlists_entry_interstitial",
                InterstitialPlacement.HOME_FAVORITES_ENTRY to
                    "home_favorites_entry_interstitial",
                InterstitialPlacement.HOME_PLAYLIST_ENTRY to
                    "home_playlist_entry_interstitial",
                InterstitialPlacement.ARTIST_LIST_NAME_ENTRY to
                    "artist_list_name_entry_interstitial",
                InterstitialPlacement.PLAYER_ARTIST_NAME_ENTRY to
                    "player_artist_name_entry_interstitial",
                InterstitialPlacement.SONG_INFO_ARTIST_ENTRY to
                    "song_info_artist_entry_interstitial",
            ),
            InterstitialPlacement.entries.associateWith(InterstitialPlacement::switchKey),
        )
    }
}
