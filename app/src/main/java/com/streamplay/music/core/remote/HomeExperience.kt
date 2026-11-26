package com.streamplay.music.core.remote

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Product shape of the home screen. DEVICE (remote value `a`/`local`) is a pure on-device player,
 * STREAMING (`b`/`online`) shows the online catalog. Anything unexpected falls back to DEVICE.
 */
enum class HomeExperience {
    DEVICE,
    STREAMING;

    companion object {
        fun parse(value: String?): HomeExperience = when (value?.trim()?.lowercase(Locale.ROOT)) {
            "online", "b" -> STREAMING
            else -> DEVICE
        }
    }
}

/** Audience of the install, used as the key inside the remote `music_home_mode` JSON object. */
enum class Audience(val configKey: String) {
    ORGANIC("natural"),
    PAID("paid"),
}

/**
 * Picks the experience for [audience] out of a JSON object such as `{"natural":"a","paid":"b"}`.
 * Malformed JSON, missing keys or non-string values resolve to [HomeExperience.DEVICE].
 */
fun resolveHomeExperience(rawJson: String?, audience: Audience): HomeExperience {
    val root = rawJson?.trim()?.takeIf(String::isNotEmpty)
        ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
        ?: return HomeExperience.DEVICE
    val selected = root.entries
        .firstOrNull { (key, _) -> key.equals(audience.configKey, ignoreCase = true) }
        ?.value as? JsonPrimitive
    return HomeExperience.parse(selected?.takeIf(JsonPrimitive::isString)?.content)
}

/**
 * Session-wide home experience. [settle] is the one-time startup decision; [reconcile] is only
 * used by the explicit foreground refresh and reports whether the shape actually changed.
 */
class HomeExperienceStore {
    private val mutableMode = MutableStateFlow(HomeExperience.DEVICE)
    private var settled = false

    val mode: StateFlow<HomeExperience> = mutableMode.asStateFlow()

    @Synchronized
    fun settle(rawJson: String?, audience: Audience) {
        if (settled) return
        settled = true
        mutableMode.value = resolveHomeExperience(rawJson, audience)
        Log.i(TAG, "settled mode=${mutableMode.value} audience=$audience")
    }

    @Synchronized
    fun reconcile(rawJson: String?, audience: Audience): Boolean {
        val next = resolveHomeExperience(rawJson, audience)
        val changed = settled && next != mutableMode.value
        settled = true
        mutableMode.value = next
        if (changed) Log.i(TAG, "foreground refresh switched mode to $next")
        return changed
    }

    private companion object {
        const val TAG = "HomeExperience"
    }
}
