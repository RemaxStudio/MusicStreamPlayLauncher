package com.streamplay.music.data.library.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Song metadata is stored exactly once; playlists, favorites and history reference it by id.
 * The table keeps enough fields to resume playback without network access to the catalog.
 */
@Entity(tableName = "songs")
data class SongRow(
    @PrimaryKey @ColumnInfo(name = "song_id") val songId: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    @ColumnInfo(name = "artwork_url") val artworkUrl: String?,
    @ColumnInfo(name = "artwork_candidates") val artworkCandidates: List<String>,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val lyrics: String?,
    val description: String?,
    @ColumnInfo(name = "artist_platform") val artistPlatform: String?,
    @ColumnInfo(name = "artist_id") val artistId: String?,
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["title"], unique = true)],
)
data class PlaylistRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "playlist_id") val playlistId: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "touched_at") val touchedAt: Long,
)

@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlist_id", "song_id"],
    foreignKeys = [
        ForeignKey(PlaylistRow::class, ["playlist_id"], ["playlist_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SongRow::class, ["song_id"], ["song_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("song_id")],
)
data class PlaylistEntryRow(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(
    tableName = "favorites",
    foreignKeys = [ForeignKey(SongRow::class, ["song_id"], ["song_id"], onDelete = ForeignKey.CASCADE)],
)
data class FavoriteRow(
    @PrimaryKey @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "liked_at") val likedAt: Long,
)

@Entity(
    tableName = "listening_history",
    foreignKeys = [ForeignKey(SongRow::class, ["song_id"], ["song_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("played_at")],
)
data class HistoryRow(
    @PrimaryKey @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "played_at") val playedAt: Long,
)

/** Lightweight projection for playlist pickers and home shortcuts. */
data class PlaylistOverview(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    val title: String,
    @ColumnInfo(name = "song_count") val songCount: Int,
    @ColumnInfo(name = "cover_candidates") val coverCandidates: List<String>?,
)
