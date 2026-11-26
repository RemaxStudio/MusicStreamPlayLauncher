package com.streamplay.music.data.source

import androidx.compose.runtime.Immutable
import androidx.core.text.HtmlCompat
import com.streamplay.music.core.model.ArtistLink
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.model.upstreamKey
import com.streamplay.source.model.PageRequest
import com.streamplay.source.model.RemoteArtistDetails
import com.streamplay.source.model.RemoteCollection
import com.streamplay.source.model.RemoteCollectionType
import com.streamplay.source.model.RemotePage
import com.streamplay.source.model.RemotePlatform
import com.streamplay.source.model.RemoteSort
import com.streamplay.source.model.RemoteTrack
import com.streamplay.source.model.TrackQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope

/** One page of songs plus the upstream offset to request next (null when exhausted). */
data class SongPage(val songs: List<Song>, val nextOffset: Int?)

data class HomeFeed(val recommended: List<Song>, val mostPlayed: List<Song>)

@Immutable
data class ArtistRelease(
    val id: String,
    val platform: RemotePlatform,
    val type: RemoteCollectionType,
    val title: String,
    /** Release date or owner name; empty means the UI derives "Release • N songs". */
    val caption: String,
    val coverCandidates: List<String>,
    val songCount: Int?,
)

/**
 * Read-only gateway to the aggregated streaming catalog. Platform fan-out, de-duplication and key
 * rotation are handled by the SDK; this class only decides *what* to ask for and maps the DTOs.
 */
class OnlineCatalog(private val gateway: SourceGateway) {
    private val source get() = gateway.source

    /**
     * Popular weekly picks for the shelf plus trending songs for "Most played", fetched in parallel.
     * One shelf failing still shows the other; only a total failure is reported as an error.
     */
    suspend fun homeFeed(): HomeFeed = supervisorScope {
        val recommended = async {
            attempt { source.tracks(TrackQuery(sort = RemoteSort.POPULAR), PageRequest(limit = HOME_RECOMMENDED)).items }
        }
        val trending = async { attempt { source.trendingTracks(limit = HOME_MOST_PLAYED).items } }
        val picks = recommended.await()
        val hot = trending.await()
        if (picks.isFailure && hot.isFailure) throw checkNotNull(picks.exceptionOrNull())
        HomeFeed(
            recommended = picks.getOrDefault(emptyList()).map(RemoteTrack::toSong),
            mostPlayed = hot.getOrDefault(emptyList()).map(RemoteTrack::toSong),
        )
    }

    suspend fun recommendedPage(offset: Int, limit: Int): SongPage =
        source.tracks(TrackQuery(sort = RemoteSort.POPULAR), PageRequest(offset, limit)).toSongPage(offset, limit)

    suspend fun searchPage(query: String, offset: Int, limit: Int): SongPage =
        source.searchTracks(query = query, offset = offset, limit = limit).toSongPage(offset, limit)

    suspend fun artistDetails(link: ArtistLink): RemoteArtistDetails =
        source.getArtistDetails(link.platform, link.artistId)

    suspend fun artistSongs(link: ArtistLink, offset: Int, limit: Int): SongPage =
        source.artistTracks(link.platform, link.artistId, PageRequest(offset, limit), RemoteSort.POPULAR)
            .toSongPage(offset, limit)

    suspend fun artistReleases(link: ArtistLink, limit: Int): List<ArtistRelease> =
        source.artistAlbums(link.platform, link.artistId, PageRequest(limit = limit)).items.map(RemoteCollection::toRelease)

    suspend fun artistPlaylists(link: ArtistLink, limit: Int): List<ArtistRelease> =
        source.artistPlaylists(link.platform, link.artistId, PageRequest(limit = limit)).items.map(RemoteCollection::toRelease)

    suspend fun releaseSongs(release: ArtistRelease, limit: Int): List<Song> {
        val page = when (release.type) {
            RemoteCollectionType.ALBUM -> source.albumTracks(release.platform, release.id, PageRequest(limit = limit))
            RemoteCollectionType.PLAYLIST -> source.playlistTracks(release.platform, release.id, PageRequest(limit = limit))
        }
        return page.items.asSequence().filter(RemoteTrack::isPlayable).map(RemoteTrack::toSong).distinctBy(Song::id).toList()
    }

    /** Songs saved before artist links existed are resolved by their exact upstream id, never by name. */
    suspend fun resolveArtist(song: Song): ArtistLink? {
        song.artistLink?.let { return it }
        val (platform, upstreamId) = song.upstreamKey() ?: return null
        return ArtistLink.from(source.getTrack(platform, upstreamId).artist)
    }

    private fun RemotePage<RemoteTrack>.toSongPage(offset: Int, limit: Int): SongPage {
        val songs = items.asSequence().filter(RemoteTrack::isPlayable).map(RemoteTrack::toSong).toList()
        val next = if (hasMore) nextOffset?.takeIf { it > offset } ?: (offset + limit) else null
        return SongPage(songs, next)
    }

    /** Like runCatching, but never swallows coroutine cancellation. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object {
        const val HOME_RECOMMENDED = 8
        const val HOME_MOST_PLAYED = 10
    }
}

private val RemoteTrack.isPlayable: Boolean get() = isStreamable && streamUrl.isNotBlank()

internal fun RemoteTrack.toSong(): Song {
    val thumbnails = artwork?.thumbnailCandidates().orEmpty().ifEmpty { listOfNotNull(artworkUrl) }
    return Song(
        id = Song.onlineId(platform, id),
        title = title.decodeEntities(),
        artist = artistName.decodeEntities(),
        streamUrl = streamUrl,
        artworkUrl = artwork?.preferredUrl ?: artworkUrl,
        artworkCandidates = thumbnails,
        durationMs = durationMs,
        lyrics = lyrics,
        description = description,
        artistLink = ArtistLink.from(artist)?.let { it.copy(name = it.name.decodeEntities()) },
    )
}

/** Some catalogs return names with HTML entities (e.g. `&amp;`); show them as plain text. */
internal fun String.decodeEntities(): String =
    if ('&' in this && ';' in this) HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() else this

private fun RemoteCollection.toRelease() = ArtistRelease(
    id = id,
    platform = platform,
    type = type,
    title = title.decodeEntities(),
    caption = releaseDate ?: owner?.name.orEmpty().decodeEntities(),
    coverCandidates = artwork?.thumbnailCandidates().orEmpty().ifEmpty { listOfNotNull(artwork?.preferredUrl) },
    songCount = trackCount,
)
