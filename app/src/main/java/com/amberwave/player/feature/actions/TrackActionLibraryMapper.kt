package com.amberwave.player.feature.actions

import com.amberwave.player.feature.collection.LibraryTrack

/** 将弹框展示模型转换为 Room 领域模型，数据层不依赖任何具体页面。 */
internal fun TrackActionTarget.toLibraryTrack() = LibraryTrack(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    artworkThumbnailUrls = artworkUrls,
    artworkFallbackRes = artworkFallbackRes,
    streamUrl = streamUrl,
    durationMs = durationMs,
    lyrics = lyrics,
    description = description,
    artistRef = artistRef,
)
