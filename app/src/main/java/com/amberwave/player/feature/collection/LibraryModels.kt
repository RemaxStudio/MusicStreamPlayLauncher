package com.amberwave.player.feature.collection

import androidx.annotation.DrawableRes
import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.player.feature.nowplaying.artworkCandidates
import com.amberwave.catalog.model.CatalogArtistRef

data class LibraryTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val artworkThumbnailUrls: List<String>,
    @param:DrawableRes val artworkFallbackRes: Int,
    val streamUrl: String,
    val durationMs: Long,
    val lyrics: String?,
    val description: String?,
    val artistRef: CatalogArtistRef? = null,
) {
    fun toPlayerTrack() = PlayerTrack(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        lyrics = lyrics,
        description = description,
        artistRef = artistRef,
        artworkThumbnailUrls = artworkThumbnailUrls,
    )
}

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val artworkUrl: String?,
    val artworkThumbnailUrls: List<String>,
)

enum class AddTrackResult { ADDED, ALREADY_PRESENT }

sealed interface CollectionSource {
    data object Favorites : CollectionSource
    data class Playlist(val id: Long) : CollectionSource
}

fun PlayerTrack.toLibraryTrack(
    artworkThumbnailUrls: List<String> = artworkCandidates(),
    @DrawableRes artworkFallbackRes: Int,
) = LibraryTrack(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    artworkThumbnailUrls = artworkThumbnailUrls,
    artworkFallbackRes = artworkFallbackRes,
    streamUrl = streamUrl,
    durationMs = durationMs,
    lyrics = lyrics,
    description = description,
    artistRef = artistRef,
)
