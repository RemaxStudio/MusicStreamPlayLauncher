package com.amberwave.player

import android.content.Context
import androidx.room.Room
import com.amberwave.player.feature.collection.LibraryRepository
import com.amberwave.player.feature.collection.RoomLibraryRepository
import com.amberwave.player.data.library.LibraryDatabase

/** 应用级 Room 单例，所有页面共享同一失效追踪器和数据库线程池。 */
internal object LibraryGraph {
    @Volatile
    private var database: LibraryDatabase? = null

    @Volatile
    private var repository: LibraryRepository? = null

    fun repository(context: Context): LibraryRepository = repository ?: synchronized(this) {
        repository ?: RoomLibraryRepository(database(context).libraryDao()).also { repository = it }
    }

    private fun database(context: Context): LibraryDatabase = database ?: synchronized(this) {
        database ?: Room.databaseBuilder(
            context.applicationContext,
            LibraryDatabase::class.java,
            LibraryDatabase.DATABASE_NAME,
        ).build().also { database = it }
    }
}
