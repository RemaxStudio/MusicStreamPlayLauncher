package com.streamplay.source.internal

import com.streamplay.source.MusicSourceCredentials
import com.streamplay.source.model.RemotePlatform
import com.streamplay.source.model.RemoteArtist
import com.streamplay.source.model.RemoteArtistDetails
import com.streamplay.source.model.RemoteCollection
import com.streamplay.source.model.PageRequest
import com.streamplay.source.model.RemoteSort
import com.streamplay.source.model.RemoteTrack
import com.streamplay.source.model.TrackQuery
import com.streamplay.source.model.ProviderHealth

internal interface SourceProvider {
    val platform: RemotePlatform
    suspend fun search(query: String, offset: Int, limit: Int): List<RemoteTrack>
    suspend fun trending(offset: Int, limit: Int): List<RemoteTrack>
    suspend fun getTrack(id: String): RemoteTrack
    suspend fun tracks(query: TrackQuery, page: PageRequest): ProviderPage<RemoteTrack>
    suspend fun similarTracks(id: String, page: PageRequest): ProviderPage<RemoteTrack>
    suspend fun searchArtists(query: String, page: PageRequest): ProviderPage<RemoteArtist>
    suspend fun getArtistDetails(id: String): RemoteArtistDetails
    suspend fun getArtist(id: String): RemoteArtist = getArtistDetails(id).artist
    suspend fun artistTracks(id: String, page: PageRequest, sort: RemoteSort): ProviderPage<RemoteTrack>
    suspend fun artistAlbums(id: String, page: PageRequest): ProviderPage<RemoteCollection>
    suspend fun artistPlaylists(id: String, page: PageRequest): ProviderPage<RemoteCollection>
    suspend fun searchAlbums(query: String, page: PageRequest): ProviderPage<RemoteCollection>
    suspend fun getAlbum(id: String): RemoteCollection
    suspend fun albumTracks(id: String, page: PageRequest): ProviderPage<RemoteTrack>
    suspend fun searchPlaylists(query: String, page: PageRequest): ProviderPage<RemoteCollection>
    suspend fun getPlaylist(id: String): RemoteCollection
    suspend fun playlistTracks(id: String, page: PageRequest): ProviderPage<RemoteTrack>
    fun health(): ProviderHealth
    /** 替换本 Provider 关心的那部分凭据，不重建网络客户端。 */
    fun updateCredentials(credentials: MusicSourceCredentials)
}

internal data class ProviderPage<T>(
    val items: List<T>,
    val totalCount: Int? = null,
    /** 适配器已知上游仍可能有数据时使用，适合过滤后不足一页的响应。 */
    val hasMoreHint: Boolean? = null,
    /** 过滤响应内容时仍按上游实际消费数量推进，防止重复页或空页死循环。 */
    val nextOffsetHint: Int? = null,
) {
    fun toPublic(page: PageRequest): com.streamplay.source.model.RemotePage<T> {
        val hasMore = hasMoreHint ?: totalCount?.let { page.offset + items.size < it } ?: (items.size >= page.limit)
        val nextOffset = if (!hasMore) {
            null
        } else {
            nextOffsetHint?.takeIf { it > page.offset }
                ?: page.offset + (items.size.takeIf { it > 0 } ?: page.limit)
        }
        return com.streamplay.source.model.RemotePage(
            items = items,
            offset = page.offset,
            limit = page.limit,
            hasMore = hasMore,
            totalCount = totalCount,
            nextOffset = nextOffset,
        )
    }
}

class MusicSourceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal class ProviderRequestException(
    val statusCode: Int?,
    val retryAfterMs: Long? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    val invalidCredential: Boolean get() = statusCode == 401 || statusCode == 403
    val rateLimited: Boolean get() = statusCode == 429
}
