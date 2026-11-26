package com.streamplay.music.data.library.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Local library: playlists, favorites and listening history.
 * Raise [VERSION] together with an AutoMigration whenever the schema changes; the exported schema
 * in app/schemas lets Room verify migrations at compile time so user playlists are never dropped.
 */
@Database(
    entities = [SongRow::class, PlaylistRow::class, PlaylistEntryRow::class, FavoriteRow::class, HistoryRow::class],
    version = StreamPlayDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(StringListConverter::class)
abstract class StreamPlayDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryStoreDao

    companion object {
        const val VERSION = 1
        private const val FILE_NAME = "stream_play_library.db"

        fun create(context: Context): StreamPlayDatabase =
            Room.databaseBuilder(context.applicationContext, StreamPlayDatabase::class.java, FILE_NAME).build()
    }
}

class StringListConverter {
    private val codec = ListSerializer(String.serializer())

    @TypeConverter
    fun encode(values: List<String>?): String? = values?.let { Json.encodeToString(codec, it) }

    @TypeConverter
    fun decode(raw: String?): List<String>? = raw?.let { runCatching { Json.decodeFromString(codec, it) }.getOrNull() }
}
