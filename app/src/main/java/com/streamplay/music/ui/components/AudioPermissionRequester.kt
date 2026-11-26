package com.streamplay.music.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.streamplay.music.core.telemetry.AudioPermissionResult
import com.streamplay.music.core.telemetry.Outcome
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.data.device.AudioAccess

/**
 * Read-audio permission flow shared by the device home and Local music: first request through the
 * system dialog, then — once the user denied with "don't ask again" — a jump to the app's system
 * settings page. Whether a request already happened survives configuration changes.
 */
class AudioPermissionRequester internal constructor(
    private val context: Context,
    private val activity: Activity?,
    private val launcher: ManagedActivityResultLauncher<String, Boolean>,
    private val attempted: MutableState<Boolean>,
) {
    val hasRequested: Boolean get() = attempted.value

    fun isGranted(): Boolean = AudioAccess.isGranted(context)

    fun request() {
        attempted.value = true
        launcher.launch(AudioAccess.permission)
    }

    fun requestOrOpenSettings() {
        val blocked = attempted.value && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, AudioAccess.permission)
        if (!blocked) {
            request()
            return
        }
        Telemetry.log(AudioPermissionResult(Outcome.OPEN_SETTINGS))
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun rememberAudioPermissionRequester(onResult: (Boolean) -> Unit): AudioPermissionRequester {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val attempted = rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        attempted.value = true
        Telemetry.log(AudioPermissionResult(if (granted) Outcome.GRANTED else Outcome.DENIED))
        onResult(granted)
    }
    return remember(context, activity, launcher) { AudioPermissionRequester(context, activity, launcher, attempted) }
}
