package com.streamplay.music.data.source

import android.util.Log
import com.streamplay.music.BuildConfig
import com.streamplay.music.core.remote.SourceKeyPatch
import com.streamplay.source.AudiusCredential
import com.streamplay.source.MusicSource
import com.streamplay.source.MusicSourceConfig
import com.streamplay.source.MusicSourceCredentials
import com.streamplay.source.MusicSourceFactory

/**
 * Owns the single [MusicSource] instance so the OkHttp pool, provider health and key rotation are
 * shared by every screen. Built-in credentials from BuildConfig are used until Remote Config sends
 * a patch, which is merged without rebuilding the client.
 */
class SourceGateway {
    private val lock = Any()

    @Volatile
    private var current: MusicSourceCredentials = bundledCredentials()

    val source: MusicSource by lazy {
        MusicSourceFactory.create(
            MusicSourceConfig(
                jamendoClientIds = current.jamendoClientIds,
                audiusCredentials = current.audiusCredentials,
            ),
        )
    }

    fun applyPatch(patch: SourceKeyPatch) = synchronized(lock) {
        val merged = MusicSourceCredentials(
            jamendoClientIds = patch.jamendoClientIds ?: current.jamendoClientIds,
            audiusCredentials = patch.audiusCredentials ?: current.audiusCredentials,
        )
        // Re-applying identical keys would reset the SDK's cooldown and health bookkeeping.
        if (merged == current) return@synchronized
        source.updateCredentials(merged)
        current = merged
        Log.i(TAG, "credentials updated: jamendo=${merged.jamendoClientIds.size} audius=${merged.audiusCredentials.size}")
    }

    private fun bundledCredentials(): MusicSourceCredentials {
        val keys = BuildConfig.SOURCE_AUDIUS_KEYS.splitList()
        val tokens = BuildConfig.SOURCE_AUDIUS_TOKENS.splitList()
        return MusicSourceCredentials(
            jamendoClientIds = BuildConfig.SOURCE_JAMENDO_IDS.splitList(),
            audiusCredentials = keys.mapIndexed { index, key -> AudiusCredential(key, tokens.getOrNull(index)) },
        )
    }

    private fun String.splitList(): List<String> =
        split(',').map(String::trim).filter(String::isNotEmpty).distinct()

    private companion object {
        const val TAG = "SourceGateway"
    }
}
