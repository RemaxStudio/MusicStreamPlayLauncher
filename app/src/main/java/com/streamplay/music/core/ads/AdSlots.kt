package com.streamplay.music.core.ads

import android.os.SystemClock
import net.corekit.core.controller.AdSlotSwitchController

/**
 * Business-side ad placements. The ad SDK itself is not bundled in this build, so every slot
 * resolves to "not shown"; the remote switch keys and the shared interstitial frequency gate are
 * kept as the stable integration boundary for when an SDK is wired back in.
 */
enum class NativeSlot(val switchKey: String) {
    HOME_BOTTOM("home_bottom_native"),
    SEARCH_RESULTS_BOTTOM("search_result_bottom_native"),
    RECOMMENDED_BOTTOM("recommended_music_bottom_native"),
    LOCAL_MUSIC_BOTTOM("local_music_bottom_native"),
    ARTIST_BOTTOM("artist_detail_bottom_native"),
    PLAYLIST_BOTTOM("playlist_detail_bottom_native"),
    FAVORITES_BOTTOM("favorites_bottom_native"),
    SETTINGS_BOTTOM("settings_bottom_native"),
}

enum class EntryInterstitial(val switchKey: String) {
    RECOMMENDED_MORE("home_recommended_more_entry_interstitial"),
    LOCAL_PLAYLISTS("home_local_playlists_entry_interstitial"),
    FAVORITES("home_favorites_entry_interstitial"),
    PLAYLIST("home_playlist_entry_interstitial"),
    ARTIST_FROM_LIST("artist_list_name_entry_interstitial"),
    ARTIST_FROM_PLAYER("player_artist_name_entry_interstitial"),
    ARTIST_FROM_SONG_INFO("song_info_artist_entry_interstitial"),
}

object AdSlots {
    private const val INITIAL_QUIET_MS = 30_000L
    private const val MIN_SPACING_MS = 90_000L
    private val sessionStart = SystemClock.elapsedRealtime()
    private var lastAttempt: Long? = null

    /** Remote switch for a slot; failures read as disabled. */
    fun isSwitchedOn(key: String): Boolean = runCatching { AdSlotSwitchController.isEnabled(key) }.getOrDefault(false)

    /** Native slots render nothing until an ad SDK is integrated. */
    fun showsNative(slot: NativeSlot): Boolean = false

    /**
     * Shared interstitial pacing: nothing in the first 30s, then at most once every 90s across all
     * entries. Returns whether an interstitial may be requested; without an SDK nothing is shown.
     */
    @Synchronized
    fun mayRequestInterstitial(entry: EntryInterstitial): Boolean {
        if (!isSwitchedOn(entry.switchKey)) return false
        val now = SystemClock.elapsedRealtime()
        val previous = lastAttempt
        if (previous == null && now - sessionStart < INITIAL_QUIET_MS) return false
        if (previous != null && now - previous < MIN_SPACING_MS) return false
        lastAttempt = now
        return false
    }
}
