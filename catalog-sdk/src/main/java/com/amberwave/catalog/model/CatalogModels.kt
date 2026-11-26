package com.amberwave.catalog.model

import java.net.URI

/** 媒体来源；业务层可用于展示版权归属或做来源筛选。 */
enum class CatalogPlatform { JAMENDO, AUDIUS }

data class CatalogImage(
    val smallUrl: String? = null,
    val mediumUrl: String? = null,
    val largeUrl: String? = null,
    /** Audius 返回的备用内容节点；图片加载失败时业务层可依次替换 URL host。 */
    val mirrors: List<String> = emptyList(),
) {
    val preferredUrl: String? get() = largeUrl ?: mediumUrl ?: smallUrl

    /** 列表优先使用平台的最小图片，并将 Audius 镜像节点展开成可直接加载的备用 URL。 */
    fun thumbnailCandidates(): List<String> = candidatesFor(smallUrl ?: mediumUrl ?: largeUrl)

    private fun candidatesFor(primaryUrl: String?): List<String> {
        if (primaryUrl.isNullOrBlank()) return emptyList()
        val primaryUri = runCatching { URI(primaryUrl) }.getOrNull()
        val path = primaryUri?.rawPath?.takeIf(String::isNotBlank) ?: return listOf(primaryUrl)
        val suffix = buildString {
            append(path)
            primaryUri.rawQuery?.let { append('?').append(it) }
        }
        return buildList {
            add(primaryUrl)
            mirrors.forEach { mirror ->
                mirror.trim().takeIf(String::isNotBlank)?.let { add(it.trimEnd('/') + suffix) }
            }
        }.distinct()
    }
}

data class CatalogArtist(
    val id: String,
    val platform: CatalogPlatform,
    val name: String,
    val handle: String? = null,
    val image: CatalogImage? = null,
    val bio: String? = null,
    val website: String? = null,
    val permalink: String? = null,
    val followerCount: Int? = null,
    val trackCount: Int? = null,
    val isVerified: Boolean = false,
)

/**
 * 曲目中携带的稳定歌手引用。业务层应使用 platform + id 打开歌手页，不能用可能重名的 name 反查。
 */
data class CatalogArtistRef(
    val id: String,
    val platform: CatalogPlatform,
    val name: String,
)

enum class CatalogSocialPlatform { WEBSITE, TWITTER, INSTAGRAM, TIKTOK }

data class CatalogSocialLink(
    val platform: CatalogSocialPlatform,
    val url: String,
    val handle: String? = null,
)

data class CatalogArtistLocation(
    val displayName: String,
    val city: String? = null,
    /** ISO 3166-1 alpha-3 或平台提供的国家标识；未知时为空。 */
    val countryCode: String? = null,
)

/** 平台明确支持的歌手页能力，避免业务层把“不支持”错误展示为 0 或空列表。 */
enum class CatalogArtistFeature {
    TRACKS,
    ALBUMS,
    PLAYLISTS,
    COVER_IMAGE,
    BIOGRAPHY,
    LOCATION,
    TAGS,
    JOIN_DATE,
    VERIFICATION,
    FOLLOWER_COUNT,
    FOLLOWING_COUNT,
    SOCIAL_LINKS,
}

data class CatalogArtistCapabilities(
    val supportedFeatures: Set<CatalogArtistFeature>,
) {
    fun supports(feature: CatalogArtistFeature): Boolean = feature in supportedFeatures
}

/**
 * 歌手页详情。基础信息继续复用 CatalogArtist，平台特有字段统一为可空值，并由 capabilities 描述能力边界。
 */
data class CatalogArtistDetails(
    val artist: CatalogArtist,
    val coverImage: CatalogImage? = null,
    val location: CatalogArtistLocation? = null,
    val followingCount: Int? = null,
    val albumCount: Int? = null,
    val playlistCount: Int? = null,
    val tags: List<String> = emptyList(),
    val joinedDate: String? = null,
    val socialLinks: List<CatalogSocialLink> = emptyList(),
    val capabilities: CatalogArtistCapabilities,
)

enum class CatalogCollectionType { ALBUM, PLAYLIST }

data class CatalogCollection(
    val id: String,
    val platform: CatalogPlatform,
    val type: CatalogCollectionType,
    val title: String,
    val owner: CatalogArtist? = null,
    val artwork: CatalogImage? = null,
    val description: String? = null,
    val trackCount: Int? = null,
    val releaseDate: String? = null,
    val permalink: String? = null,
)

/** 聚合 SDK 对外唯一的曲目模型，隔离各平台字段差异。 */
data class CatalogTrack(
    val id: String,
    val platform: CatalogPlatform,
    val title: String,
    val artistName: String,
    val artworkUrl: String?,
    val artwork: CatalogImage? = artworkUrl?.let { CatalogImage(largeUrl = it) },
    val durationMs: Long,
    val streamUrl: String,
    val albumName: String? = null,
    val genre: String? = null,
    val permalink: String? = null,
    /** 平台提供的非时间轴歌词；没有歌词能力或曲目未提供时为 null。 */
    val lyrics: String? = null,
    /** 平台提供的歌曲介绍；它与歌词语义不同，由业务层明确标注后展示。 */
    val description: String? = null,
    /** 当前调用方是否可以直接播放；聚合列表默认只返回 true 的曲目。 */
    val isStreamable: Boolean = true,
    /** 是否配置了关注、购买或其他访问门控。 */
    val isStreamGated: Boolean = false,
    /** 用于从任意歌曲入口稳定跳转到歌手页；旧数据缺少 ID 时允许为空。 */
    val artist: CatalogArtistRef? = null,
) {
    val artistId: String? get() = artist?.id
}

data class CatalogPage<T>(
    val items: List<T>,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    /** 平台未提供总数时为 null。聚合结果通常无法得到精确总数。 */
    val totalCount: Int? = null,
    /**
     * 下一次应提交给同一接口的上游 offset。经过不可播放内容过滤后，它可能大于 offset + items.size。
     */
    val nextOffset: Int? = if (hasMore) offset + items.size else null,
)

data class PageRequest(val offset: Int = 0, val limit: Int = 20) {
    init {
        require(offset >= 0) { "offset cannot be negative" }
        require(limit in 1..100) { "limit must be between 1 and 100" }
    }
}

enum class CatalogSort { RELEVANCE, POPULAR, LATEST }

data class TrackQuery(
    val text: String? = null,
    val genre: String? = null,
    val mood: String? = null,
    val artistId: String? = null,
    val collectionId: String? = null,
    val minDurationSeconds: Int? = null,
    val maxDurationSeconds: Int? = null,
    val sort: CatalogSort = CatalogSort.RELEVANCE,
    val platform: CatalogPlatform? = null,
)

data class ProviderHealth(
    val platform: CatalogPlatform,
    val availableKeys: Int,
    val coolingDownKeys: Int,
    val disabledKeys: Int,
    val isAvailable: Boolean,
)
