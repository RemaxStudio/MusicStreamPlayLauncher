package com.streamplay.music.data.device

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RemovalStep {
    data object Removed : RemovalStep
    /** The system must confirm (Android 10 RecoverableSecurityException); retry after consent. */
    data class NeedsConsent(val sender: IntentSender) : RemovalStep
    data object Failed : RemovalStep
}

/**
 * MediaStore deletion primitives. Android 11+ uses the system batch delete dialog; Android 10 may
 * throw a recoverable exception carrying its own consent dialog; Android 9 and lower delete
 * directly once WRITE_EXTERNAL_STORAGE has been granted.
 */
class DeviceFileRemover(private val resolver: ContentResolver) {

    @RequiresApi(Build.VERSION_CODES.R)
    fun systemDeleteRequest(uri: Uri): PendingIntent? =
        runCatching { MediaStore.createDeleteRequest(resolver, listOf(uri)) }.getOrNull()

    suspend fun deleteDirectly(uri: Uri): RemovalStep = withContext(Dispatchers.IO) {
        try {
            if (resolver.delete(uri, null, null) > 0) RemovalStep.Removed else RemovalStep.Failed
        } catch (security: SecurityException) {
            val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (security as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
            } else {
                null
            }
            sender?.let(RemovalStep::NeedsConsent) ?: RemovalStep.Failed
        } catch (_: Exception) {
            RemovalStep.Failed
        }
    }

    companion object {
        /** Only plain MediaStore content URIs are ever accepted for deletion. */
        fun parseSafeUri(raw: String): Uri? = runCatching { Uri.parse(raw) }.getOrNull()
            ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT && it.authority == MediaStore.AUTHORITY }
    }
}
