package com.streamplay.music.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.streamplay.music.core.model.ArtistLink
import com.streamplay.music.core.model.Song
import com.streamplay.source.model.RemotePlatform

/**
 * Round-trips a [Song] through MediaSession metadata so every controller (screens, notification,
 * lock screen, Bluetooth) can recover the full song, including lyrics and the artist link.
 */
internal object MediaItemCodec {
    private const val PREFIX = "com.streamplay.music.meta."
    private const val LYRICS = PREFIX + "lyrics"
    private const val DESCRIPTION = PREFIX + "description"
    private const val ARTIST_ID = PREFIX + "artist_id"
    private const val ARTIST_PLATFORM = PREFIX + "artist_platform"
    private const val ARTWORK_CHAIN = PREFIX + "artwork_chain"

    fun encode(song: Song): MediaItem {
        val extras = Bundle().apply {
            song.lyrics?.takeIf(String::isNotBlank)?.let { putString(LYRICS, it) }
            song.description?.takeIf(String::isNotBlank)?.let { putString(DESCRIPTION, it) }
            song.artistLink?.let {
                putString(ARTIST_ID, it.artistId)
                putString(ARTIST_PLATFORM, it.platform.name)
            }
            song.artworkChain().takeIf(List<String>::isNotEmpty)?.let { putStringArrayList(ARTWORK_CHAIN, ArrayList(it)) }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(song.artworkUrl?.takeIf(String::isNotBlank)?.let(Uri::parse))
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    fun decode(item: MediaItem, durationMs: Long = 0L): Song {
        val meta = item.mediaMetadata
        val extras = meta.extras
        val artworkUrl = meta.artworkUri?.toString()
        val artistName = meta.artist?.toString().orEmpty()
        val link = extras?.let { bundle ->
            val id = bundle.getString(ARTIST_ID).orEmpty()
            val platform = RemotePlatform.entries.firstOrNull { it.name == bundle.getString(ARTIST_PLATFORM) }
            if (id.isNotBlank() && platform != null) ArtistLink(platform, id, artistName) else null
        }
        return Song(
            id = item.mediaId,
            title = meta.title?.toString().orEmpty(),
            artist = artistName,
            streamUrl = item.localConfiguration?.uri?.toString().orEmpty(),
            artworkUrl = artworkUrl,
            artworkCandidates = extras?.getStringArrayList(ARTWORK_CHAIN).orEmpty().ifEmpty { listOfNotNull(artworkUrl) },
            durationMs = durationMs,
            lyrics = extras?.getString(LYRICS),
            description = extras?.getString(DESCRIPTION),
            artistLink = link,
        )
    }
}
