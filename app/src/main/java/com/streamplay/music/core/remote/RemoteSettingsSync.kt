package com.streamplay.music.core.remote

import android.os.SystemClock
import android.util.Log
import com.streamplay.music.data.source.SourceGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.utils.ConfigRemoteManager

/**
 * Bridges the strings managed by CoreKit's [ConfigRemoteManager] into the music features.
 *
 * Firebase fetching, caching and activation stay inside CoreKit; this class only reads the
 * already-activated values for the home experience (`music_home_mode`) and the streaming
 * catalog credentials (`music_sdk_config`).
 */
class RemoteSettingsSync(
    private val homeStore: HomeExperienceStore,
    private val sources: SourceGateway,
    private val scope: CoroutineScope,
) {
    private val firstActivation = CompletableDeferred<Unit>()
    private val commitLock = Mutex()
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        runCatching {
            ConfigRemoteManager.setOnConfigUpdatedCallback {
                firstActivation.complete(Unit)
                scope.launch { pull(Trigger.UPDATE_CALLBACK) }
            }
        }.onFailure { Log.w(TAG, "callback registration failed: ${it.javaClass.simpleName}") }

        // A fetch that finished before the Application started never re-sends the callback.
        if (runCatching { ConfigRemoteManager.isInitialized() }.getOrDefault(false)) {
            firstActivation.complete(Unit)
            scope.launch { pull(Trigger.ALREADY_ACTIVE) }
        }
    }

    /** Waits (at most [TIMEOUT_MS]) for the first activation so the home screen opens in the right shape. */
    suspend fun awaitStartupDecision() = withContext(Dispatchers.IO) {
        start()
        val began = SystemClock.elapsedRealtime()
        val ready = withTimeoutOrNull(TIMEOUT_MS) {
            firstActivation.await()
            pull(Trigger.STARTUP)
        } != null
        if (!ready) homeStore.settle(rawJson = null, audience = audience())
        Log.i(TAG, "startup decision ready=$ready after ${SystemClock.elapsedRealtime() - began}ms")
    }

    /** Called whenever the home screen resumes. Returns true when the home experience changed. */
    suspend fun refreshOnResume(): Boolean = withContext(Dispatchers.IO) {
        start()
        withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { ConfigRemoteManager.refresh() }
                .onFailure { Log.w(TAG, "foreground refresh failed: ${it.javaClass.simpleName}") }
            pull(Trigger.FOREGROUND)
        } == true
    }

    private suspend fun pull(trigger: Trigger): Boolean {
        // Remote reads may suspend; the lock only guards the final commit.
        val homeRaw = read(KEY_HOME_MODE)
        val sourceRaw = read(KEY_SOURCE_CONFIG)
        val audience = audience()
        return commitLock.withLock {
            val changed = when (trigger) {
                Trigger.FOREGROUND, Trigger.STARTUP -> homeStore.reconcile(homeRaw, audience)
                else -> {
                    homeStore.settle(homeRaw, audience)
                    false
                }
            }
            val patch = SourceKeyPatch.decode(sourceRaw)
            if (!patch.isEmpty) {
                sources.applyPatch(patch)
            } else if (!sourceRaw.isNullOrBlank()) {
                Log.w(TAG, "music_sdk_config is malformed; keeping current credentials")
            }
            changed
        }
    }

    private suspend fun read(key: String): String? = runCatching {
        ConfigRemoteManager.getString(key, "")?.trim()?.takeIf(String::isNotEmpty)
    }.getOrElse {
        Log.w(TAG, "read $key failed: ${it.javaClass.simpleName}")
        null
    }

    /** Channel lookup failures count as organic installs so the default stays on the device home. */
    private fun audience(): Audience = runCatching {
        when (ChannelUserController.getCurrentChannel()) {
            ChannelUserController.UserChannelType.PAID -> Audience.PAID
            else -> Audience.ORGANIC
        }
    }.getOrDefault(Audience.ORGANIC)

    private enum class Trigger { UPDATE_CALLBACK, ALREADY_ACTIVE, STARTUP, FOREGROUND }

    private companion object {
        const val TAG = "RemoteSettingsSync"
        const val KEY_HOME_MODE = "music_home_mode"
        const val KEY_SOURCE_CONFIG = "music_sdk_config"
        const val TIMEOUT_MS = 5_000L
    }
}
