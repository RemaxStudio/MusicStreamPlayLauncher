package com.amberwave.player.data.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 本地曲库（歌单、收藏、最近播放）。Schema 由 Room Gradle Plugin 导出；
 * 以后升级时提升 [SCHEMA_VERSION] 并声明 AutoMigration，编译阶段会校验新旧 schema，禁止静默丢失用户歌单。
 */
@Database(
    entities = [
        LibraryTrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteTrackEntity::class,
        RecentTrackEntity::class,
    ],
    version = LibraryDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(LibraryTypeConverters::class)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val SCHEMA_VERSION = 1
        const val DATABASE_NAME = "amberwave_library.db"
    }
}
