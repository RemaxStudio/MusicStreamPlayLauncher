package com.streamplay.music.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlaylistInfo(
    val id: Long,
    val name: String,
    val songCount: Int,
    val coverCandidates: List<String> = emptyList(),
)

/** Which saved collection a screen shows. Favorites is a built-in collection without a row id. */
sealed interface SavedCollection {
    data object Favorites : SavedCollection
    data class Playlist(val playlistId: Long) : SavedCollection
}

enum class PlaylistAddOutcome { ADDED, ALREADY_THERE }

/** Repeat / shuffle behaviour exposed to the user as a single cycling control. */
enum class PlaybackOrder {
    IN_ORDER,
    REPEAT_ONE,
    SHUFFLE;

    fun next(): PlaybackOrder = entries[(ordinal + 1) % entries.size]
}
