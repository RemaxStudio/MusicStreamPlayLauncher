package com.streamplay.source

import com.streamplay.source.internal.AggregatedMusicSource
import com.streamplay.source.internal.AudiusProvider
import com.streamplay.source.internal.JamendoProvider
import com.streamplay.source.internal.KeyPool
import com.streamplay.source.model.RemotePage
import com.streamplay.source.model.RemoteArtist
import com.streamplay.source.model.RemoteArtistDetails
import com.streamplay.source.model.RemoteCollection
import com.streamplay.source.model.PageRequest
import com.streamplay.source.model.RemotePlatform
import com.streamplay.source.model.RemoteSort
import com.streamplay.source.model.RemoteTrack
import com.streamplay.source.model.TrackQuery
import com.streamplay.source.model.ProviderHealth
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface MusicSource {
    suspend fun tracks(query: TrackQuery = TrackQuery(), page: PageRequest = PageRequest()): RemotePage<RemoteTrack>

    /** 同时查询所有健康平台，单个平台故障不会让整个请求失败。 */
    suspend fun searchTracks(query: String, offset: Int = 0, limit: Int = 20): RemotePage<RemoteTrack>

    /** 获取平台热门歌曲并聚合去重。 */
    suspend fun trendingTracks(offset: Int = 0, limit: Int = 20): RemotePage<RemoteTrack>

    /** 使用 platform + id 精确获取曲目。 */
    suspend fun getTrack(platform: RemotePlatform, id: String): RemoteTrack

    suspend fun similarTracks(platform: RemotePlatform, id: String, page: PageRequest = PageRequest()): RemotePage<RemoteTrack>

    suspend fun searchArtists(query: String, page: PageRequest = PageRequest()): RemotePage<RemoteArtist>
    /** 获取适合搜索结果或轻量展示的歌手摘要。 */
    suspend fun getArtist(platform: RemotePlatform, id: String): RemoteArtist
    /** 获取歌手主页所需的完整详情及平台能力。 */
    suspend fun getArtistDetails(platform: RemotePlatform, id: String): RemoteArtistDetails
    /** 获取歌手歌曲；POPULAR 与 LATEST 会映射为平台原生排序。 */
    suspend fun artistTracks(
        platform: RemotePlatform,
        artistId: String,
        page: PageRequest = PageRequest(),
        sort: RemoteSort = RemoteSort.POPULAR,
    ): RemotePage<RemoteTrack>
    /** 获取歌手发行的专辑或单曲合集。 */
    suspend fun artistAlbums(
        platform: RemotePlatform,
        artistId: String,
        page: PageRequest = PageRequest(),
    ): RemotePage<RemoteCollection>
    /** 获取歌手创建的歌单；不支持的平台返回 totalCount=null 的终止空页。 */
    suspend fun artistPlaylists(
        platform: RemotePlatform,
        artistId: String,
        page: PageRequest = PageRequest(),
    ): RemotePage<RemoteCollection>

    suspend fun searchAlbums(query: String, page: PageRequest = PageRequest()): RemotePage<RemoteCollection>
    suspend fun getAlbum(platform: RemotePlatform, id: String): RemoteCollection
    suspend fun albumTracks(platform: RemotePlatform, albumId: String, page: PageRequest = PageRequest()): RemotePage<RemoteTrack>

    suspend fun searchPlaylists(query: String, page: PageRequest = PageRequest()): RemotePage<RemoteCollection>
    suspend fun getPlaylist(platform: RemotePlatform, id: String): RemoteCollection
    suspend fun playlistTracks(platform: RemotePlatform, playlistId: String, page: PageRequest = PageRequest()): RemotePage<RemoteTrack>

    /** 主动发起轻量请求，更新 Key 池状态后返回各平台健康度。 */
    suspend fun checkHealth(): List<ProviderHealth>

    /** 返回最近请求维护的本地健康快照，不产生网络调用。 */
    fun health(): List<ProviderHealth>

    /**
     * 运行时线程安全地替换两个平台的凭据池，可用于 Remote Config 热更新。
     * 此方法不会重建 OkHttp 连接池，正在执行的请求不会被中断。
     */
    fun updateCredentials(credentials: MusicSourceCredentials)
}

data class MusicSourceConfig(
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

object MusicSourceFactory {
    fun create(config: MusicSourceConfig, client: OkHttpClient? = null): MusicSource {
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
        return AggregatedMusicSource(providers)
    }
}
