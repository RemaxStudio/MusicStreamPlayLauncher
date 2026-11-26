package com.streamplay.music.core.remote

import com.streamplay.source.AudiusCredential
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Partial credential update delivered through Remote Config (`music_sdk_config`).
 * `null` keeps the current platform credentials; an explicit empty list disables the platform.
 *
 * ```json
 * { "jamendoClientIds": ["id"], "audiusCredentials": [{ "apiKey": "key", "bearerToken": "token" }] }
 * ```
 */
data class SourceKeyPatch(
    val jamendoClientIds: List<String>? = null,
    val audiusCredentials: List<AudiusCredential>? = null,
) {
    val isEmpty: Boolean get() = jamendoClientIds == null && audiusCredentials == null

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun decode(raw: String?): SourceKeyPatch {
            val root = raw?.trim()?.takeIf(String::isNotEmpty)
                ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                ?: return SourceKeyPatch()
            return SourceKeyPatch(
                jamendoClientIds = root.stringList("jamendoClientIds"),
                audiusCredentials = root.audiusList("audiusCredentials"),
            )
        }

        /** Missing or mistyped field → keep current; a non-empty array whose entries are all invalid also keeps current. */
        private fun JsonObject.stringList(field: String): List<String>? {
            val array = get(field) as? JsonArray ?: return null
            if (array.isEmpty()) return emptyList()
            return array.mapNotNull { it.textOrNull() }.distinct().takeIf(List<String>::isNotEmpty)
        }

        private fun JsonObject.audiusList(field: String): List<AudiusCredential>? {
            val array = get(field) as? JsonArray ?: return null
            if (array.isEmpty()) return emptyList()
            return array.mapNotNull { element ->
                val entry = element as? JsonObject ?: return@mapNotNull null
                val apiKey = entry["apiKey"].textOrNull() ?: return@mapNotNull null
                val bearer = entry["bearerToken"].textOrNull()
                    ?.let { if (it.startsWith("Bearer ", ignoreCase = true)) it.substringAfter(' ').trim() else it }
                    ?.takeIf(String::isNotEmpty)
                AudiusCredential(apiKey = apiKey, bearerToken = bearer)
            }.distinctBy { it.apiKey to it.bearerToken }.takeIf(List<AudiusCredential>::isNotEmpty)
        }

        private fun JsonElement?.textOrNull(): String? =
            (this as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.trim()?.takeIf(String::isNotEmpty)
    }
}
