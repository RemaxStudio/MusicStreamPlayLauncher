package com.amberwave.catalog

import com.amberwave.catalog.internal.AggregatingCatalogSdk
import com.amberwave.catalog.internal.AudiusProvider
import com.amberwave.catalog.internal.JamendoProvider
import com.amberwave.catalog.internal.KeyPool
import com.amberwave.catalog.model.CatalogPage
import com.amberwave.catalog.model.CatalogArtist
import com.amberwave.catalog.model.CatalogArtistDetails
import com.amberwave.catalog.model.CatalogCollection
import com.amberwave.catalog.model.PageRequest
import com.amberwave.catalog.model.CatalogPlatform
import com.amberwave.catalog.model.CatalogSort
import com.amberwave.catalog.model.CatalogTrack
import com.amberwave.catalog.model.TrackQuery
import com.amberwave.catalog.model.ProviderHealth
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface CatalogSdk {
    suspend fun tracks(query: TrackQuery = TrackQuery(), page: PageRequest = PageRequest()): CatalogPage<CatalogTrack>

    /** 同时查询所有健康平台，单个平台故障不会让整个请求失败。 */
    suspend fun searchTracks(query: String, offset: Int = 0, limit: Int = 20): CatalogPage<CatalogTrack>

    /** 获取平台热门歌曲并聚合去重。 */
    suspend fun trendingTracks(offset: Int = 0, limit: Int = 20): CatalogPage<CatalogTrack>

    /** 使用 platform + id 精确获取曲目。 */
    suspend fun getTrack(platform: CatalogPlatform, id: String): CatalogTrack

    suspend fun similarTracks(platform: CatalogPlatform, id: String, page: PageRequest = PageRequest()): CatalogPage<CatalogTrack>

    suspend fun searchArtists(query: String, page: PageRequest = PageRequest()): CatalogPage<CatalogArtist>
    /** 获取适合搜索结果或轻量展示的歌手摘要。 */
    suspend fun getArtist(platform: CatalogPlatform, id: String): CatalogArtist
    /** 获取歌手主页所需的完整详情及平台能力。 */
    suspend fun getArtistDetails(platform: CatalogPlatform, id: String): CatalogArtistDetails
    /** 获取歌手歌曲；POPULAR 与 LATEST 会映射为平台原生排序。 */
    suspend fun artistTracks(
        platform: CatalogPlatform,
        artistId: String,
        page: PageRequest = PageRequest(),
        sort: CatalogSort = CatalogSort.POPULAR,
    ): CatalogPage<CatalogTrack>
    /** 获取歌手发行的专辑或单曲合集。 */
    suspend fun artistAlbums(
        platform: CatalogPlatform,
        artistId: String,
        page: PageRequest = PageRequest(),
    ): CatalogPage<CatalogCollection>
    /** 获取歌手创建的歌单；不支持的平台返回 totalCount=null 的终止空页。 */
    suspend fun artistPlaylists(
        platform: CatalogPlatform,
        artistId: String,
        page: PageRequest = PageRequest(),
    ): CatalogPage<CatalogCollection>

    suspend fun searchAlbums(query: String, page: PageRequest = PageRequest()): CatalogPage<CatalogCollection>
    suspend fun getAlbum(platform: CatalogPlatform, id: String): CatalogCollection
    suspend fun albumTracks(platform: CatalogPlatform, albumId: String, page: PageRequest = PageRequest()): CatalogPage<CatalogTrack>

    suspend fun searchPlaylists(query: String, page: PageRequest = PageRequest()): CatalogPage<CatalogCollection>
    suspend fun getPlaylist(platform: CatalogPlatform, id: String): CatalogCollection
    suspend fun playlistTracks(platform: CatalogPlatform, playlistId: String, page: PageRequest = PageRequest()): CatalogPage<CatalogTrack>

    /** 主动发起轻量请求，更新 Key 池状态后返回各平台健康度。 */
    suspend fun checkHealth(): List<ProviderHealth>

    /** 返回最近请求维护的本地健康快照，不产生网络调用。 */
    fun health(): List<ProviderHealth>

    /**
     * 运行时线程安全地替换两个平台的凭据池，可用于 Remote Config 热更新。
     * 此方法不会重建 OkHttp 连接池，正在执行的请求不会被中断。
     */
    fun updateCredentials(credentials: CatalogSdkCredentials)
}

data class CatalogSdkConfig(
    val jamendoClientIds: List<String> = emptyList(),
    /** 保留旧版只传 API Key 的接入方式。新业务应使用 [audiusCredentials]。 */
    val audiusApiKeys: List<String> = emptyList(),
    val audiusCredentials: List<AudiusCredential> = emptyList(),
    val keyCooldownMs: Long = TimeUnit.MINUTES.toMillis(2),
    val connectTimeoutSeconds: Long = 10,
    val readTimeoutSeconds: Long = 20,
) {
    init {
        require(keyCooldownMs >= 0) { "keyCooldownMs must not be negative" }
        require(connectTimeoutSeconds > 0 && readTimeoutSeconds > 0) { "Timeouts must be positive" }
    }
}

object CatalogSdkFactory {
    fun create(config: CatalogSdkConfig, client: OkHttpClient? = null): CatalogSdk {
        val httpClient = client ?: OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .build()
        val jamendoClientIds = KeyPool.clean(config.jamendoClientIds)
        val audiusCredentials = (config.audiusCredentials + config.audiusApiKeys.map { AudiusCredential(it) })
            .mapNotNull(AudiusCredential::normalizedOrNull)
            .distinctBy(AudiusCredential::identity)
        // 两个 Provider 始终存在，允许应用以空凭据启动，再由 Remote Config 动态启用平台。
        val providers = listOf(
            JamendoProvider(
                httpClient,
                KeyPool(
                    credentials = jamendoClientIds,
                    defaultCooldownMs = config.keyCooldownMs,
                    normalize = { it.trim().takeIf(String::isNotEmpty) },
                    identity = { it },
                ),
            ),
            AudiusProvider(
                httpClient,
                KeyPool(
                    credentials = audiusCredentials,
                    defaultCooldownMs = config.keyCooldownMs,
                    normalize = AudiusCredential::normalizedOrNull,
                    identity = AudiusCredential::identity,
                ),
            ),
        )
        return AggregatingCatalogSdk(providers)
    }
}
