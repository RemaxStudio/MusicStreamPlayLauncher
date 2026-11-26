package com.streamplay.music.core.model

import androidx.compose.runtime.Immutable
import com.streamplay.source.model.RemoteArtistRef
import com.streamplay.source.model.RemotePlatform

/**
 * The one song model shared by every screen, the playback layer and the local library.
 *
 * Online songs use the id format `PLATFORM:upstreamId`; songs read from MediaStore use
 * [DEVICE_ID_PREFIX]. Keeping a single model avoids the field-by-field copies between list,
 * player and database representations.
 */
@Immutable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val streamUrl: String,
    val artworkUrl: String? = null,
    /** Small-to-large artwork candidates; the image loader walks this list until one succeeds. */
    val artworkCandidates: List<String> = listOfNotNull(artworkUrl),
    val durationMs: Long = 0L,
    val lyrics: String? = null,
    val description: String? = null,
    val artistLink: ArtistLink? = null,
) {
    val origin: SongOrigin
        get() = if (id.startsWith(DEVICE_ID_PREFIX)) SongOrigin.DEVICE else SongOrigin.ONLINE

    val isPlayable: Boolean
        get() = id.isNotBlank() && streamUrl.isNotBlank()

    /** Device audio may only be deleted when it is a real MediaStore row, never an online stream. */
    val isDeletableDeviceFile: Boolean
        get() = origin == SongOrigin.DEVICE && streamUrl.startsWith("content://media/")

    /** Artwork chain used by compact UI: thumbnails first, the full image as the last resort. */
    fun artworkChain(limit: Int = MAX_ARTWORK_CANDIDATES): List<String> {
        val cleaned = artworkCandidates.asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it.length <= MAX_URL_LENGTH }
            .distinct()
            .toList()
        val full = artworkUrl?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_URL_LENGTH }
        return if (full == null || full in cleaned) cleaned.take(limit) else cleaned.take(limit - 1) + full
    }

    /** Lyrics win over the platform description because they describe the audio more precisely. */
    fun companionText(): CompanionText? =
        lyrics.clean()?.let { CompanionText(CompanionText.Kind.LYRICS, it) }
            ?: description.clean()?.let { CompanionText(CompanionText.Kind.DESCRIPTION, it) }

    companion object {
        const val DEVICE_ID_PREFIX = "LOCAL:"
        private const val MAX_ARTWORK_CANDIDATES = 4
        private const val MAX_URL_LENGTH = 2_048

        fun onlineId(platform: RemotePlatform, upstreamId: String) = "${platform.name}:$upstreamId"

        fun deviceId(mediaStoreId: Long) = "$DEVICE_ID_PREFIX$mediaStoreId"
    }
}

enum class SongOrigin { ONLINE, DEVICE }

/** Stable reference to an artist page; never resolved by name to avoid opening the wrong artist. */
@Immutable
data class ArtistLink(
    val platform: RemotePlatform,
    val artistId: String,
    val name: String,
) {
    companion object {
        fun from(ref: RemoteArtistRef?): ArtistLink? = ref?.takeIf { it.id.isNotBlank() }
            ?.let { ArtistLink(it.platform, it.id, it.name) }
    }
}

@Immutable
data class CompanionText(val kind: Kind, val text: String) {
    enum class Kind { LYRICS, DESCRIPTION }
}

/** Parses an online song id back into its platform and upstream id. */
fun Song.upstreamKey(): Pair<RemotePlatform, String>? {
    if (origin == SongOrigin.DEVICE) return null
    val separator = id.indexOf(':')
    if (separator <= 0 || separator == id.lastIndex) return null
    val platform = RemotePlatform.entries.firstOrNull { it.name.equals(id.substring(0, separator), ignoreCase = true) }
        ?: return null
    return platform to id.substring(separator + 1)
}

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
