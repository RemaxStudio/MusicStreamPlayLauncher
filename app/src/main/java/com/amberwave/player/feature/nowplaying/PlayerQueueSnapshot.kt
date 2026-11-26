package com.amberwave.player.feature.nowplaying

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.amberwave.catalog.model.CatalogArtistRef
import com.amberwave.catalog.model.CatalogPlatform

/** 从 MediaSession 读取不可变队列快照，供不同页面的 Mini Player/队列弹层共同使用。 */
internal fun Player.toPlayerTrackQueue(): List<PlayerTrack> = List(mediaItemCount) { index ->
    getMediaItemAt(index).toPlayerTrack(
        durationMs = if (index == currentMediaItemIndex) {
            duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        } else {
            0L
        },
    )
}

internal fun MediaItem.toPlayerTrack(durationMs: Long): PlayerTrack {
    val artworkUrl = mediaMetadata.artworkUri?.toString()
    val extras = mediaMetadata.extras
    val artworkThumbnailUrls = extras
        ?.getStringArrayList(MEDIA_METADATA_ARTWORK_THUMBNAILS_KEY)
        .orEmpty()
        .ifEmpty { listOfNotNull(artworkUrl) }
    return PlayerTrack(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        artworkUrl = artworkUrl,
        streamUrl = localConfiguration?.uri?.toString().orEmpty(),
        durationMs = durationMs,
        lyrics = extras?.getString(MEDIA_METADATA_LYRICS_KEY),
        description = extras?.getString(MEDIA_METADATA_DESCRIPTION_KEY),
        artistRef = extras?.let {
            val artistId = it.getString(MEDIA_METADATA_ARTIST_ID_KEY).orEmpty()
            val platform = it.getString(MEDIA_METADATA_ARTIST_PLATFORM_KEY)
                ?.let { raw -> runCatching { CatalogPlatform.valueOf(raw) }.getOrNull() }
            if (artistId.isNotBlank() && platform != null) {
                CatalogArtistRef(artistId, platform, mediaMetadata.artist?.toString().orEmpty())
            } else {
                null
            }
        },
        artworkThumbnailUrls = artworkThumbnailUrls,
    )
}

/** [toPlayerTrack] 的逆映射：把业务曲目写入 MediaSession 元数据，供各页面恢复完整信息。 */
internal fun PlayerTrack.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        lyrics?.takeIf(String::isNotBlank)?.let { putString(MEDIA_METADATA_LYRICS_KEY, it) }
        description?.takeIf(String::isNotBlank)?.let { putString(MEDIA_METADATA_DESCRIPTION_KEY, it) }
        artistRef?.let { artist ->
            putString(MEDIA_METADATA_ARTIST_ID_KEY, artist.id)
            putString(MEDIA_METADATA_ARTIST_PLATFORM_KEY, artist.platform.name)
        }
        artworkCandidates().takeIf { it.isNotEmpty() }?.let { candidates ->
            putStringArrayList(MEDIA_METADATA_ARTWORK_THUMBNAILS_KEY, ArrayList(candidates))
        }
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(artworkUrl?.takeIf(String::isNotBlank)?.let(Uri::parse))
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl)
        .setMediaMetadata(metadata)
        .build()
}
