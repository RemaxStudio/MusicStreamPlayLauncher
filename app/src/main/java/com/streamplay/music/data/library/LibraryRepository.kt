package com.streamplay.music.data.library

import android.database.sqlite.SQLiteConstraintException
import com.streamplay.music.core.model.ArtistLink
import com.streamplay.music.core.model.PlaylistAddOutcome
import com.streamplay.music.core.model.PlaylistInfo
import com.streamplay.music.core.model.SavedCollection
import com.streamplay.music.core.model.Song
import com.streamplay.music.data.library.db.LibraryStoreDao
import com.streamplay.music.data.library.db.PlaylistRow
import com.streamplay.music.data.library.db.SongRow
import com.streamplay.source.model.RemotePlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Failure reasons for playlist creation that the UI can translate. */
sealed class PlaylistNameProblem(message: String) : IllegalArgumentException(message) {
    class Blank : PlaylistNameProblem("Playlist name is required")
    class Taken : PlaylistNameProblem("A playlist with this name already exists")
}

class LibraryRepository(
    private val dao: LibraryStoreDao,
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(historyLimit > 0) { "historyLimit must be positive" }
    }

    fun playlists(): Flow<List<PlaylistInfo>> = dao.playlists().map { rows ->
        rows.map { PlaylistInfo(it.playlistId, it.title, it.songCount, it.coverCandidates.orEmpty()) }
    }

    fun recentlyPlayed(): Flow<List<Song>> = dao.history(historyLimit).map { it.map(SongRow::toSong) }

    fun songs(collection: SavedCollection): Flow<List<Song>> = when (collection) {
        SavedCollection.Favorites -> dao.favoriteSongs()
        is SavedCollection.Playlist -> dao.playlistSongs(collection.playlistId)
    }.map { it.map(SongRow::toSong) }

    /** Favorites always exist; a playlist title becomes null once the playlist is deleted. */
    fun title(collection: SavedCollection, favoritesTitle: String): Flow<String?> = when (collection) {
        SavedCollection.Favorites -> flowOf(favoritesTitle)
        is SavedCollection.Playlist -> dao.playlistTitle(collection.playlistId)
    }

    fun isFavorite(songId: String): Flow<Boolean> = dao.isFavorite(songId)

    suspend fun createPlaylist(rawName: String): Result<Long> {
        val name = rawName.trim()
        if (name.isEmpty()) return Result.failure(PlaylistNameProblem.Blank())
        return try {
            val now = clock()
            Result.success(dao.insertPlaylist(PlaylistRow(title = name, createdAt = now, touchedAt = now)))
        } catch (duplicate: SQLiteConstraintException) {
            Result.failure(PlaylistNameProblem.Taken())
        }
    }

    suspend fun addToPlaylist(playlistId: Long, song: Song): PlaylistAddOutcome =
        if (dao.addToPlaylist(playlistId, song.toRow(), clock())) PlaylistAddOutcome.ADDED else PlaylistAddOutcome.ALREADY_THERE

    suspend fun setFavorite(song: Song, favorite: Boolean) = dao.setFavorite(song.toRow(), favorite, clock())

    suspend fun logPlay(song: Song) = dao.logPlay(song.toRow(), clock(), historyLimit)

    suspend fun remove(collection: SavedCollection, songIds: Set<String>): Int {
        if (songIds.isEmpty()) return 0
        return when (collection) {
            SavedCollection.Favorites -> dao.deleteFavorites(songIds)
            is SavedCollection.Playlist -> dao.deleteEntries(collection.playlistId, songIds)
        }
    }

    suspend fun deletePlaylist(playlistId: Long): Boolean = dao.deletePlaylist(playlistId) > 0

    private companion object {
        const val DEFAULT_HISTORY_LIMIT = 50
    }
}

private fun SongRow.toSong() = Song(
    id = songId,
    title = title,
    artist = artist,
    streamUrl = streamUrl,
    artworkUrl = artworkUrl,
    artworkCandidates = artworkCandidates,
    durationMs = durationMs,
    lyrics = lyrics,
    description = description,
    artistLink = artistId?.takeIf(String::isNotBlank)?.let { id ->
        RemotePlatform.entries.firstOrNull { it.name == artistPlatform }?.let { ArtistLink(it, id, artist) }
    },
)

private fun Song.toRow() = SongRow(
    songId = id,
    title = title,
    artist = artist,
    streamUrl = streamUrl,
    artworkUrl = artworkUrl,
    artworkCandidates = artworkChain(),
    durationMs = durationMs,
    lyrics = lyrics,
    description = description,
    artistPlatform = artistLink?.platform?.name,
    artistId = artistLink?.artistId,
)
