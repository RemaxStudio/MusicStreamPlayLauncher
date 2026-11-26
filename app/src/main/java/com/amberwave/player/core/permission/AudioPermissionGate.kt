package com.amberwave.player.core.permission

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.feature.device.AudioPermission

/**
 * 本地音频权限的统一流程：首次申请、拒绝后引导到系统设置、结果埋点与跨重建保存“已申请”标记。
 * 必须在 Fragment 初始化阶段创建（registerForActivityResult 的要求）。
 */
class AudioPermissionGate(
    private val fragment: Fragment,
    private val stateKey: String,
    private val onResult: (granted: Boolean) -> Unit,
) {
    var requestAttempted = false
        private set

    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requestAttempted = true
        EventTracker.localMediaPermission(
            if (granted) EventTracker.Outcome.GRANTED else EventTracker.Outcome.DENIED,
        )
        onResult(granted)
    }

    fun isGranted(): Boolean = AudioPermission.isGranted(fragment.requireContext())

    /** 用户已拒绝且不再弹出系统对话框时，只能引导到应用详情页手动开启。 */
    fun mustOpenSettings(): Boolean =
        AudioPermission.shouldOpenSettings(fragment.requireActivity(), requestAttempted)

    fun request() {
        requestAttempted = true
        launcher.launch(AudioPermission.requiredPermission())
    }

    fun requestOrOpenSettings() {
        if (!mustOpenSettings()) {
            request()
            return
        }
        EventTracker.localMediaPermission(EventTracker.Outcome.OPEN_SETTINGS)
        val context = fragment.requireContext()
        fragment.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }

    fun restore(savedInstanceState: Bundle?) {
        requestAttempted = savedInstanceState?.getBoolean(stateKey) == true
    }

    fun save(outState: Bundle) {
        outState.putBoolean(stateKey, requestAttempted)
    }
}
