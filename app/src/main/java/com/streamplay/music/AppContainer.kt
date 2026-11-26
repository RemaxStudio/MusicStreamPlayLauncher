package com.streamplay.music

import android.content.Context
import com.streamplay.music.core.remote.HomeExperienceStore
import com.streamplay.music.core.remote.RemoteSettingsSync
import com.streamplay.music.data.device.DeviceAudioLibrary
import com.streamplay.music.data.device.DeviceFileRemover
import com.streamplay.music.data.download.SongDownloader
import com.streamplay.music.data.library.LibraryRepository
import com.streamplay.music.data.library.db.StreamPlayDatabase
import com.streamplay.music.data.settings.LanguagePreferences
import com.streamplay.music.data.source.OnlineCatalog
import com.streamplay.music.data.source.SourceGateway
import com.streamplay.music.playback.PlaybackConnector
import com.streamplay.music.playback.SleepTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container (see "Manual dependency injection" in the Android architecture
 * guide). One instance lives in [StreamPlayApplication]; ViewModel factories and the playback
 * service read their collaborators from here, so every screen shares the same database, catalog
 * client and media controller.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sources = SourceGateway()
    val catalog = OnlineCatalog(sources)
    val homeExperience = HomeExperienceStore()
    val remoteSettings = RemoteSettingsSync(homeExperience, sources, appScope)

    private val database by lazy { StreamPlayDatabase.create(appContext) }
    val library by lazy { LibraryRepository(database.libraryDao()) }
    val deviceAudio by lazy { DeviceAudioLibrary(appContext) }
    val fileRemover by lazy { DeviceFileRemover(appContext.contentResolver) }
    val downloader by lazy { SongDownloader(appContext) }
    val languages by lazy { LanguagePreferences() }

    val playback = PlaybackConnector(appContext)
    val sleepTimer = SleepTimerController()
}
