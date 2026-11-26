package com.streamplay.music.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import com.streamplay.music.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

enum class DownloadOutcome { QUEUED, ALREADY_QUEUED, NOT_DOWNLOADABLE, FAILED }

/** Pure description of a download; kept free of Android types so it can be unit tested. */
data class DownloadPlan(val url: String, val fileName: String)

object DownloadPlanner {
    private val illegalChars = Regex("[\\u0000-\\u001F\\u007F/\\\\:*?\"<>|]")
    private const val MAX_BASE_NAME = 96

    fun plan(song: Song): DownloadPlan? {
        val url = song.streamUrl.trim()
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val base = listOf(song.artist, song.title)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" - ")
            .replace(illegalChars, "_")
            .replace(Regex("_+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.')
            .take(MAX_BASE_NAME)
            .trim(' ', '.')
            .ifBlank { "Music" }
        // Same-named songs can come from different platforms; a stable suffix prevents collisions.
        val suffix = song.id.hashCode().toUInt().toString(16).padStart(8, '0')
        return DownloadPlan(url, "$base-$suffix.mp3")
    }
}

/**
 * Hands downloads to the system [DownloadManager] using the application context, so the task
 * survives the screen. Android 10+ saves into the public Music folder (visible in Local music);
 * Android 8–9 use the app-specific Music directory to avoid the legacy storage permission.
 */
class SongDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val ledger = appContext.getSharedPreferences("song_downloads", Context.MODE_PRIVATE)

    suspend fun enqueue(song: Song): DownloadOutcome = withContext(Dispatchers.IO) {
        val plan = DownloadPlanner.plan(song) ?: return@withContext DownloadOutcome.NOT_DOWNLOADABLE
        val dm = manager ?: return@withContext DownloadOutcome.FAILED
        synchronized(ledger) {
            val key = "id:${song.id}"
            val previous = ledger.getLong(key, NO_ID)
            if (previous != NO_ID && dm.isActiveOrDone(previous)) return@synchronized DownloadOutcome.ALREADY_QUEUED
            runCatching {
                val request = DownloadManager.Request(Uri.parse(plan.url))
                    .setTitle(song.title.ifBlank { plan.fileName })
                    .setDescription(song.artist)
                    .setMimeType("audio/mpeg")
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .withDestination(plan.fileName)
                ledger.edit { putLong(key, dm.enqueue(request)) }
                DownloadOutcome.QUEUED
            }.getOrElse {
                ledger.edit { remove(key) }
                DownloadOutcome.FAILED
            }
        }
    }

    private fun DownloadManager.Request.withDestination(fileName: String): DownloadManager.Request = apply {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return@apply
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "$FOLDER/$fileName")
        } else {
            setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_MUSIC, fileName)
        }
    }

    private fun DownloadManager.isActiveOrDone(id: Long): Boolean = runCatching {
        query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) in setOf(
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_SUCCESSFUL,
            )
        } ?: false
    }.getOrDefault(false)

    private companion object {
        const val NO_ID = -1L
        const val FOLDER = "StreamPlay"
    }
}
