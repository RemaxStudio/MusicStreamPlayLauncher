package com.amberwave.player.feature.discover

import com.amberwave.player.R
import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.catalog.CatalogSdk
import com.amberwave.catalog.model.CatalogSort
import com.amberwave.catalog.model.CatalogTrack
import com.amberwave.catalog.model.PageRequest
import com.amberwave.catalog.model.TrackQuery

interface DiscoverRepository {
    suspend fun load(offset: Int, limit: Int): DiscoverPage
}

data class DiscoverPage(
    val tracks: List<DiscoverTrack>,
    val nextOffset: Int?,
)

/** 推荐策略与首页一致，使用多平台周热门并由聚合 SDK 负责穿插、去重和 Key 容灾。 */
class CatalogDiscoverRepository(
    private val musicSdk: CatalogSdk,
) : DiscoverRepository {
    override suspend fun load(offset: Int, limit: Int): DiscoverPage {
        val page = musicSdk.tracks(
            query = TrackQuery(sort = CatalogSort.POPULAR),
            page = PageRequest(offset = offset, limit = limit),
        )
        val tracks = page.items
            .asSequence()
            .filter { it.isStreamable && it.streamUrl.isNotBlank() }
            .map(::toUi)
            .toList()
        val nextOffset = if (page.hasMore) {
            page.nextOffset?.takeIf { it > offset } ?: offset + limit
        } else {
            null
        }
        return DiscoverPage(tracks, nextOffset)
    }

    private fun toUi(track: CatalogTrack): DiscoverTrack {
        val artworkThumbnailUrls = track.artwork?.thumbnailCandidates().orEmpty()
            .ifEmpty { listOfNotNull(track.artworkUrl) }
        return DiscoverTrack(
            track = PlayerTrack(
                id = "${track.platform}:${track.id}",
                title = track.title,
                artist = track.artistName,
                artworkUrl = track.artwork?.preferredUrl ?: track.artworkUrl,
                streamUrl = track.streamUrl,
                durationMs = track.durationMs,
                lyrics = track.lyrics,
                description = track.description,
                artistRef = track.artist,
                artworkThumbnailUrls = artworkThumbnailUrls,
            ),
            artworkThumbnailUrls = artworkThumbnailUrls,
            artworkFallbackRes = R.drawable.img_cover_fallback,
        )
    }
}
