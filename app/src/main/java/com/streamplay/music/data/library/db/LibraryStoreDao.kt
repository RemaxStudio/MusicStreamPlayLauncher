package com.streamplay.music.data.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryStoreDao {

    @Query(
        """
        SELECT p.playlist_id, p.title,
               (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlist_id = p.playlist_id) AS song_count,
               (SELECT s.artwork_candidates FROM playlist_entries e
                    JOIN songs s ON s.song_id = e.song_id
                    WHERE e.playlist_id = p.playlist_id
                    ORDER BY e.added_at DESC LIMIT 1) AS cover_candidates
        FROM playlists p
        ORDER BY p.touched_at DESC, p.created_at DESC
        """,
    )
    fun playlists(): Flow<List<PlaylistOverview>>

    @Query("SELECT title FROM playlists WHERE playlist_id = :playlistId")
    fun playlistTitle(playlistId: Long): Flow<String?>

    @Query(
        """
        SELECT s.* FROM playlist_entries e JOIN songs s ON s.song_id = e.song_id
        WHERE e.playlist_id = :playlistId ORDER BY e.added_at DESC
        """,
    )
    fun playlistSongs(playlistId: Long): Flow<List<SongRow>>

    @Query("SELECT s.* FROM favorites f JOIN songs s ON s.song_id = f.song_id ORDER BY f.liked_at DESC")
    fun favoriteSongs(): Flow<List<SongRow>>

    @Query(
        """
        SELECT s.* FROM listening_history h JOIN songs s ON s.song_id = h.song_id
        ORDER BY h.played_at DESC, h.song_id DESC LIMIT :limit
        """,
    )
    fun history(limit: Int): Flow<List<SongRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE song_id = :songId)")
    fun isFavorite(songId: String): Flow<Boolean>

    @Insert
    suspend fun insertPlaylist(row: PlaylistRow): Long

    @Upsert
    suspend fun upsertSong(row: SongRow)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(row: PlaylistEntryRow): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(row: FavoriteRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(row: HistoryRow)

    @Query("UPDATE playlists SET touched_at = :at WHERE playlist_id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, at: Long)

    @Query("DELETE FROM favorites WHERE song_id IN (:songIds)")
    suspend fun deleteFavorites(songIds: Collection<String>): Int

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId AND song_id IN (:songIds)")
    suspend fun deleteEntries(playlistId: Long, songIds: Collection<String>): Int

    @Query("DELETE FROM playlists WHERE playlist_id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long): Int

    @Query(
        """
        DELETE FROM listening_history WHERE song_id IN (
            SELECT song_id FROM listening_history ORDER BY played_at DESC, song_id DESC LIMIT -1 OFFSET :keep
        )
        """,
    )
    suspend fun trimHistory(keep: Int)

    @Transaction
    suspend fun addToPlaylist(playlistId: Long, song: SongRow, at: Long): Boolean {
        upsertSong(song)
        val added = insertEntry(PlaylistEntryRow(playlistId, song.songId, at)) != -1L
        if (added) touchPlaylist(playlistId, at)
        return added
    }

    @Transaction
    suspend fun setFavorite(song: SongRow, favorite: Boolean, at: Long) {
        if (favorite) {
            upsertSong(song)
            insertFavorite(FavoriteRow(song.songId, at))
        } else {
            deleteFavorites(listOf(song.songId))
        }
    }

    /** Re-playing a song moves it to the top; trimming happens in the same transaction. */
    @Transaction
    suspend fun logPlay(song: SongRow, at: Long, keep: Int) {
        upsertSong(song)
        insertHistory(HistoryRow(song.songId, at))
        trimHistory(keep)
    }
}
