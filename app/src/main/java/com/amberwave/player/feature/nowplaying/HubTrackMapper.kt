package com.amberwave.player.feature.nowplaying

import com.amberwave.player.feature.hub.HubTrack

/** 首页展示模型进入播放层时完整保留封面候选链，避免不同页面重复且不一致的字段映射。 */
internal fun HubTrack.toPlayerTrack(): PlayerTrack = PlayerTrack(
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
