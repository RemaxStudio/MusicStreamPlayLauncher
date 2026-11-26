package com.amberwave.player.feature.creator

import com.amberwave.player.R
import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.catalog.CatalogSdk
import com.amberwave.catalog.model.CatalogArtistDetails
import com.amberwave.catalog.model.CatalogCollection
import com.amberwave.catalog.model.CatalogCollectionType
import com.amberwave.catalog.model.CatalogPage
import com.amberwave.catalog.model.CatalogSort
import com.amberwave.catalog.model.CatalogTrack
import com.amberwave.catalog.model.PageRequest

interface CreatorRepository {
    suspend fun loadDetails(request: CreatorRequest): CatalogArtistDetails
    suspend fun loadTracks(request: CreatorRequest, offset: Int, limit: Int): CreatorTrackPage
    suspend fun loadAlbums(request: CreatorRequest, limit: Int): List<CreatorCollection>
    suspend fun loadPlaylists(request: CreatorRequest, limit: Int): List<CreatorCollection>
    suspend fun loadCollectionTracks(collection: CreatorCollection, limit: Int): List<PlayerTrack>
}

data class CreatorTrackPage(
    val tracks: List<CreatorTrack>,
    val nextOffset: Int?,
)

/**
 * SDK DTO 到歌手页展示模型的唯一映射层。Activity/ViewModel 不感知平台字段差异，
 * 平台 Key 轮换、可播放过滤和请求线程均继续由聚合 SDK 负责。
 */
class CatalogCreatorRepository(
    private val musicSdk: CatalogSdk,
) : CreatorRepository {
    override suspend fun loadDetails(request: CreatorRequest): CatalogArtistDetails =
        musicSdk.getArtistDetails(request.platform, request.artistId)

    override suspend fun loadTracks(request: CreatorRequest, offset: Int, limit: Int): CreatorTrackPage {
        val page = musicSdk.artistTracks(
            platform = request.platform,
            artistId = request.artistId,
            page = PageRequest(offset, limit),
            sort = CatalogSort.POPULAR,
        )
        return page.toArtistTrackPage(offset, limit)
    }

    override suspend fun loadAlbums(request: CreatorRequest, limit: Int): List<CreatorCollection> =
        musicSdk.artistAlbums(
            request.platform,
            request.artistId,
            PageRequest(limit = limit),
        ).items.map(::toCollectionUi)

    override suspend fun loadPlaylists(request: CreatorRequest, limit: Int): List<CreatorCollection> =
        musicSdk.artistPlaylists(
            request.platform,
            request.artistId,
            PageRequest(limit = limit),
        ).items.map(::toCollectionUi)

    override suspend fun loadCollectionTracks(
        collection: CreatorCollection,
        limit: Int,
    ): List<PlayerTrack> {
        val page = when (collection.type) {
            CatalogCollectionType.ALBUM -> musicSdk.albumTracks(
                collection.platform,
                collection.id,
                PageRequest(limit = limit),
            )
            CatalogCollectionType.PLAYLIST -> musicSdk.playlistTracks(
                collection.platform,
                collection.id,
                PageRequest(limit = limit),
            )
        }
        return page.items
            .asSequence()
            .filter { it.isStreamable && it.streamUrl.isNotBlank() }
            .map(::toPlayerTrack)
            .distinctBy(PlayerTrack::id)
            .toList()
    }

    private fun CatalogPage<CatalogTrack>.toArtistTrackPage(offset: Int, limit: Int): CreatorTrackPage {
        val playable = items.asSequence()
            .filter { it.isStreamable && it.streamUrl.isNotBlank() }
            .map { track ->
                CreatorTrack(
                    track = toPlayerTrack(track),
                    artworkUrls = track.artwork?.thumbnailCandidates().orEmpty()
                        .ifEmpty { listOfNotNull(track.artworkUrl) },
                    artworkFallbackRes = R.drawable.img_cover_fallback,
                )
            }
            .toList()
        val next = if (hasMore) nextOffset?.takeIf { it > offset } ?: offset + limit else null
        return CreatorTrackPage(playable, next)
    }

    private fun toPlayerTrack(track: CatalogTrack) = PlayerTrack(
        id = "${track.platform}:${track.id}",
        title = track.title,
        artist = track.artistName,
        artworkUrl = track.artwork?.preferredUrl ?: track.artworkUrl,
        streamUrl = track.streamUrl,
        durationMs = track.durationMs,
        lyrics = track.lyrics,
        description = track.description,
        artistRef = track.artist,
        artworkThumbnailUrls = track.artwork?.thumbnailCandidates().orEmpty()
            .ifEmpty { listOfNotNull(track.artworkUrl) },
    )

    private fun toCollectionUi(collection: CatalogCollection) = CreatorCollection(
        id = collection.id,
        platform = collection.platform,
        type = collection.type,
        title = collection.title,
        subtitle = collection.releaseDate
            ?: collection.owner?.name
            .orEmpty(),
        artworkUrls = collection.artwork?.thumbnailCandidates().orEmpty()
            .ifEmpty { listOfNotNull(collection.artwork?.preferredUrl) },
        artworkFallbackRes = R.drawable.img_cover_fallback,
        trackCount = collection.trackCount,
    )
}
