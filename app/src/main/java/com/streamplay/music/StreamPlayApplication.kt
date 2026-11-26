package com.streamplay.music

import android.app.Application

class StreamPlayApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Bundled catalog keys work immediately; Remote Config hot-swaps them once activated.
        container.remoteSettings.start()
    }
}
