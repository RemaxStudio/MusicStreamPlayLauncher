package com.amberwave.player.feature.actions

import com.amberwave.catalog.CatalogSdk
import com.amberwave.catalog.model.CatalogArtistRef
import com.amberwave.catalog.model.CatalogPlatform
import com.amberwave.catalog.model.CatalogTrack
import java.util.Locale

/** Song Info 的歌手解析与弹框、Activity 解耦，便于旧歌单数据按需补齐歌手引用。 */
fun interface TrackArtistResolver {
    suspend fun resolve(track: TrackActionTarget): CatalogArtistRef?
}

class CatalogArtistResolver internal constructor(
    private val loadTrack: suspend (CatalogPlatform, String) -> CatalogTrack,
) : TrackArtistResolver {
    constructor(musicSdk: CatalogSdk) : this(musicSdk::getTrack)

    override suspend fun resolve(track: TrackActionTarget): CatalogArtistRef? {
        track.artistRef?.let { return it }
        val source = track.id.toSourceTrackId() ?: return null
        return loadTrack(source.first, source.second).artist
    }
}

/** App 内在线歌曲 id 统一为 PLATFORM:upstreamId；这里只接受明确格式，禁止用歌手名模糊查询。 */
private fun String.toSourceTrackId(): Pair<CatalogPlatform, String>? {
    val separator = indexOf(':')
    if (separator <= 0 || separator == lastIndex) return null
    val platform = substring(0, separator)
        .uppercase(Locale.US)
        .let { runCatching { CatalogPlatform.valueOf(it) }.getOrNull() }
        ?: return null
    val upstreamId = substring(separator + 1).takeIf(String::isNotBlank) ?: return null
    return platform to upstreamId
}
