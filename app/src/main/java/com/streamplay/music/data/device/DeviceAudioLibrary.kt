package com.streamplay.music.data.device

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.streamplay.music.R
import com.streamplay.music.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File

/** An audio file indexed by MediaStore. Audio is always streamed through its content URI. */
data class DeviceTrack(
    val song: Song,
    val folder: String?,
)

/** Runtime permission rules for reading device audio, shared by the home screen and Local music. */
object AudioAccess {
    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Observes the public audio collection. A [ContentObserver] re-queries whenever the system media
 * scanner, a download or a deletion changes the index, so screens never poll.
 */
class DeviceAudioLibrary(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    fun observe(): Flow<List<DeviceTrack>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        val registration = runCatching { resolver.registerContentObserver(collectionUri, true, observer) }
        if (registration.isFailure) {
            close(registration.exceptionOrNull())
            return@callbackFlow
        }
        trySend(Unit)
        awaitClose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
        .conflate()
        .map { query() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    private val collectionUri: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    @Suppress("DEPRECATION")
    private val locationColumn: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }

    private fun query(): List<DeviceTrack> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            locationColumn,
        )
        val cursor = resolver.query(
            collectionUri,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        ) ?: return emptyList()
        // Resolved per query so a per-app language change is reflected on the next refresh.
        val unknownTitle = appContext.getString(R.string.local_music_unknown_title)
        val unknownArtist = appContext.getString(R.string.local_music_unknown_artist)

        return cursor.use { rows ->
            val idCol = rows.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = rows.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = rows.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = rows.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = rows.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = rows.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val locationCol = rows.getColumnIndexOrThrow(locationColumn)
            val result = ArrayList<DeviceTrack>(rows.count.coerceAtLeast(0))
            while (rows.moveToNext()) {
                val mediaId = rows.getLong(idCol)
                val fileName = rows.getString(nameCol).orEmpty()
                val albumId = rows.getLong(albumCol)
                val artwork = albumId.takeIf { it > 0L }?.let { ContentUris.withAppendedId(ALBUM_ART, it).toString() }
                val song = Song(
                    id = Song.deviceId(mediaId),
                    title = rows.getString(titleCol).meaningful()
                        ?: fileName.substringBeforeLast('.').takeIf(String::isNotBlank)
                        ?: unknownTitle,
                    artist = rows.getString(artistCol).meaningful() ?: unknownArtist,
                    streamUrl = ContentUris.withAppendedId(collectionUri, mediaId).toString(),
                    artworkUrl = artwork,
                    durationMs = rows.getLong(durationCol).coerceAtLeast(0L),
                )
                result += DeviceTrack(song, folderOf(rows.getString(locationCol)))
            }
            result
        }
    }

    private fun folderOf(location: String?): String? {
        val path = location?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            path.trim('/').substringAfterLast('/').takeIf(String::isNotBlank)
        } else {
            File(path).parentFile?.name?.takeIf(String::isNotBlank)
        }
    }

    private fun String?.meaningful(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }

    private companion object {
        val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
