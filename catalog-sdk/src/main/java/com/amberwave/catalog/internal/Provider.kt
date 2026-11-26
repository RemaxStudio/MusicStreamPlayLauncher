package com.amberwave.catalog.internal

import com.amberwave.catalog.CatalogSdkCredentials
import com.amberwave.catalog.model.CatalogPlatform
import com.amberwave.catalog.model.CatalogArtist
import com.amberwave.catalog.model.CatalogArtistDetails
import com.amberwave.catalog.model.CatalogCollection
import com.amberwave.catalog.model.PageRequest
import com.amberwave.catalog.model.CatalogSort
import com.amberwave.catalog.model.CatalogTrack
import com.amberwave.catalog.model.TrackQuery
import com.amberwave.catalog.model.ProviderHealth

internal interface CatalogProvider {
    val platform: CatalogPlatform
    suspend fun search(query: String, offset: Int, limit: Int): List<CatalogTrack>
    suspend fun trending(offset: Int, limit: Int): List<CatalogTrack>
    suspend fun getTrack(id: String): CatalogTrack
    suspend fun tracks(query: TrackQuery, page: PageRequest): ProviderPage<CatalogTrack>
    suspend fun similarTracks(id: String, page: PageRequest): ProviderPage<CatalogTrack>
    suspend fun searchArtists(query: String, page: PageRequest): ProviderPage<CatalogArtist>
    suspend fun getArtistDetails(id: String): CatalogArtistDetails
    suspend fun getArtist(id: String): CatalogArtist = getArtistDetails(id).artist
    suspend fun artistTracks(id: String, page: PageRequest, sort: CatalogSort): ProviderPage<CatalogTrack>
    suspend fun artistAlbums(id: String, page: PageRequest): ProviderPage<CatalogCollection>
    suspend fun artistPlaylists(id: String, page: PageRequest): ProviderPage<CatalogCollection>
    suspend fun searchAlbums(query: String, page: PageRequest): ProviderPage<CatalogCollection>
    suspend fun getAlbum(id: String): CatalogCollection
    suspend fun albumTracks(id: String, page: PageRequest): ProviderPage<CatalogTrack>
    suspend fun searchPlaylists(query: String, page: PageRequest): ProviderPage<CatalogCollection>
    suspend fun getPlaylist(id: String): CatalogCollection
    suspend fun playlistTracks(id: String, page: PageRequest): ProviderPage<CatalogTrack>
    fun health(): ProviderHealth
    /** 替换本 Provider 关心的那部分凭据，不重建网络客户端。 */
    fun updateCredentials(credentials: CatalogSdkCredentials)
}

internal data class ProviderPage<T>(
    val items: List<T>,
    val totalCount: Int? = null,
    /** 适配器已知上游仍可能有数据时使用，适合过滤后不足一页的响应。 */
    val hasMoreHint: Boolean? = null,
    /** 过滤响应内容时仍按上游实际消费数量推进，防止重复页或空页死循环。 */
    val nextOffsetHint: Int? = null,
) {
    fun toPublic(page: PageRequest): com.amberwave.catalog.model.CatalogPage<T> {
        val hasMore = hasMoreHint ?: totalCount?.let { page.offset + items.size < it } ?: (items.size >= page.limit)
        val nextOffset = if (!hasMore) {
            null
        } else {
            nextOffsetHint?.takeIf { it > page.offset }
                ?: page.offset + (items.size.takeIf { it > 0 } ?: page.limit)
        }
        return com.amberwave.catalog.model.CatalogPage(
            items = items,
            offset = page.offset,
            limit = page.limit,
            hasMore = hasMore,
            totalCount = totalCount,
            nextOffset = nextOffset,
        )
    }
}

class CatalogSdkException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal class ProviderRequestException(
    val statusCode: Int?,
    val retryAfterMs: Long? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    val invalidCredential: Boolean get() = statusCode == 401 || statusCode == 403
    val rateLimited: Boolean get() = statusCode == 429
}
