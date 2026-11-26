package com.streamplay.music.ui.navigation

import com.streamplay.music.core.model.ArtistLink
import com.streamplay.music.core.model.SavedCollection
import com.streamplay.source.model.RemotePlatform
import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. */
@Serializable
data object HomeDestination

@Serializable
data object RecommendedDestination

@Serializable
data object SearchDestination

@Serializable
data object PlayerDestination

@Serializable
data class ArtistDestination(val platform: String, val artistId: String, val name: String) {
    fun toLink(): ArtistLink? = RemotePlatform.entries.firstOrNull { it.name == platform }
        ?.takeIf { artistId.isNotBlank() }
        ?.let { ArtistLink(it, artistId, name) }

    companion object {
        fun of(link: ArtistLink) = ArtistDestination(link.platform.name, link.artistId, link.name)
    }
}

/** [playlistId] = [FAVORITES] opens the built-in favorites collection. */
@Serializable
data class CollectionDestination(val playlistId: Long = FAVORITES) {
    fun toCollection(): SavedCollection =
        if (playlistId == FAVORITES) SavedCollection.Favorites else SavedCollection.Playlist(playlistId)

    companion object {
        const val FAVORITES = -1L
    }
}

@Serializable
data object LocalMusicDestination

@Serializable
data object SettingsDestination
