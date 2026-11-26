package com.amberwave.player

import android.os.SystemClock
import com.blankj.utilcode.util.LogUtils
import com.amberwave.player.feature.hub.HomeVariantDiagnostics
import com.amberwave.player.feature.hub.HomeVariantStore
import com.amberwave.player.feature.hub.HomeAudience
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.utils.ConfigRemoteManager

/**
 * 将 CoreKit 已管理好的远端字符串同步到音乐业务。
 *
 * Firebase 的拉取、缓存和激活时机全部由 [ConfigRemoteManager] 负责；业务层不再持有
 * Firebase 原生实例，也不重复注册原生监听器。平台凭据集中在一个 JSON 中，避免多个参数
 * 分批更新时产生 API Key 与 Bearer Token 错配。
 */
internal object RemoteConfigBootstrap {
    private const val MUSIC_SDK_CONFIG = "music_sdk_config"
    private const val HOME_MODE = "music_home_mode"
    private const val REMOTE_OPERATION_TIMEOUT_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** CoreKit 内部 Firebase 首次 fetchAndActivate 成功后的唯一首屏放行信号。 */
    private val initialRemoteReady = CompletableDeferred<Unit>()
    private val syncMutex = Mutex()

    @Volatile
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        // 使用 CoreKit 暴露的更新回调，不再由业务层接触 Firebase 原生监听器。
        runCatching {
            ConfigRemoteManager.setOnConfigUpdatedCallback {
                initialRemoteReady.complete(Unit)
                HomeVariantDiagnostics.logEvent(
                    "config_manager_update_callback",
                    "initialFetchSucceeded=true",
                )
                scope.launch { syncRemoteValues(trigger = "config_updated_callback") }
            }
        }.onFailure { error ->
            HomeVariantDiagnostics.logEventError(
                event = "config_manager_callback_registration_failed",
                errorType = error.javaClass.simpleName,
            )
        }

        // 若 CoreKit 在业务 Application 初始化前已成功完成 Firebase 拉取，回调不会再次补发；
        // isInitialized=true 与成功回调语义相同，可以直接放行并读取当前激活值。
        val alreadyInitialized = runCatching { ConfigRemoteManager.isInitialized() }
            .getOrDefault(false)
        if (alreadyInitialized && initialRemoteReady.complete(Unit)) {
            HomeVariantDiagnostics.logEvent(
                "config_manager_already_initialized",
                "initialFetchSucceeded=true",
            )
            scope.launch { syncRemoteValues(trigger = "application_start_initialized") }
        } else if (!alreadyInitialized) {
            HomeVariantDiagnostics.logEvent("config_manager_waiting_initial_success_callback")
        }
    }

    /** 首次构建首页前等待 CoreKit 的 Firebase 成功回调，再读取最新渠道配置。 */
    suspend fun awaitHomeBootstrap() = withContext(Dispatchers.IO) {
        start()
        val startedAt = SystemClock.elapsedRealtime()
        val completed = withTimeoutOrNull(REMOTE_OPERATION_TIMEOUT_MS) {
            initialRemoteReady.await()
            val modeChanged = syncRemoteValues(
                trigger = "home_bootstrap",
                // Application 预读可能先写入默认值，首屏构建前允许用本次最新结果纠正。
                updateForegroundMode = true,
            )
            HomeVariantDiagnostics.logEvent(
                event = "config_manager_bootstrap_complete",
                details = "initialFetchSucceeded=true, modeChanged=$modeChanged, " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            true
        } == true
        if (!completed) {
            HomeVariantStore.initialize(
                rawValue = null,
                audience = currentAudience(),
            )
            HomeVariantDiagnostics.logEvent(
                event = "config_manager_bootstrap_timeout",
                details = "timeoutMs=$REMOTE_OPERATION_TIMEOUT_MS, " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}, " +
                    "retainedMode=${HomeVariantStore.current}",
            )
        }
    }

    /**
     * 首页每次重新可见时通过 CoreKit 强制刷新一次，并在五秒内返回模式是否变化。
     * 超时、协程取消或刷新失败都保持当前页面，不在主线程等待。
     */
    suspend fun refreshHomeModeOnResume(): Boolean = withContext(Dispatchers.IO) {
        start()
        val changed = withTimeoutOrNull(REMOTE_OPERATION_TIMEOUT_MS) {
            val refreshed = runCatching { ConfigRemoteManager.refresh() }
                .onFailure { error ->
                    HomeVariantDiagnostics.logEventError(
                        event = "config_manager_foreground_refresh_failed",
                        errorType = error.javaClass.simpleName,
                    )
                }
                .getOrDefault(false)
            val result = syncRemoteValues(
                trigger = "home_on_resume",
                updateForegroundMode = true,
            )
            HomeVariantDiagnostics.logEvent(
                event = "config_manager_foreground_refresh_complete",
                details = "refreshed=$refreshed, modeChanged=$result",
            )
            result
        }
        if (changed == null) {
            HomeVariantDiagnostics.logEvent(
                event = "config_manager_foreground_refresh_timeout",
                details = "timeoutMs=$REMOTE_OPERATION_TIMEOUT_MS",
            )
        }
        changed == true
    }

    private suspend fun syncRemoteValues(
        trigger: String,
        updateForegroundMode: Boolean = false,
    ): Boolean {
        // 远端读取可以挂起，不能占用应用层互斥锁；锁只保护最终状态提交。
        val homeModeRaw = readString(HOME_MODE)
        val sdkConfigRaw = readString(MUSIC_SDK_CONFIG)
        val audience = currentAudience()
        return syncMutex.withLock {
            val modeChanged = if (updateForegroundMode) {
                HomeVariantStore.updateFromForeground(homeModeRaw, audience)
            } else {
                HomeVariantStore.initialize(homeModeRaw, audience)
                false
            }
            HomeVariantDiagnostics.logRemoteValue(
                source = "ConfigRemoteManager.getString($HOME_MODE):$trigger",
                rawValue = homeModeRaw,
                audience = audience,
            )

            val patch = CatalogCredentialsParser.parse(sdkConfigRaw)
            HomeVariantDiagnostics.logEvent(
                event = "config_manager_read_complete",
                details = "trigger=$trigger, channel=${audience.name}, " +
                    "hasHomeMode=${!homeModeRaw.isNullOrBlank()}, " +
                    "hasMusicSdkConfig=${!sdkConfigRaw.isNullOrBlank()}, " +
                    "musicSdkConfigLength=${sdkConfigRaw?.length ?: 0}",
            )
            if (!patch.isEmpty) {
                updateCredentialsIfChanged(patch)
            } else if (!sdkConfigRaw.isNullOrBlank()) {
                LogUtils.w("music_sdk_config is malformed; keeping current music credentials")
            }
            modeChanged
        }
    }

    /** 避免每次 onResume 重置 SDK 内部的 Key 健康、冷却和轮换状态。 */
    private fun updateCredentialsIfChanged(patch: CatalogCredentialPatch) {
        val current = CatalogGraph.credentialsSnapshot()
        val jamendoChanged = patch.jamendoClientIds?.let { it != current.jamendoClientIds } == true
        val audiusChanged = patch.audiusCredentials?.let { it != current.audiusCredentials } == true
        if (!jamendoChanged && !audiusChanged) return

        CatalogGraph.updateCredentials(
            jamendoClientIds = patch.jamendoClientIds,
            audiusCredentials = patch.audiusCredentials,
        )
        val snapshot = CatalogGraph.credentialsSnapshot()
        // 只记录数量，不输出任何 Key 或 Token。
        LogUtils.i(
            "Music credentials updated from ConfigRemoteManager: " +
                "jamendo=${snapshot.jamendoClientIds.size}, " +
                "audius=${snapshot.audiusCredentials.size}",
        )
    }

    private suspend fun readString(key: String): String? = runCatching {
        ConfigRemoteManager.getString(key, "")?.trim()?.takeIf(String::isNotEmpty)
    }.getOrElse { error ->
        HomeVariantDiagnostics.logEventError(
            event = "config_manager_read_failed",
            errorType = error.javaClass.simpleName,
            details = "key=$key",
        )
        null
    }

    /** 渠道读取异常时按自然用户处理，确保默认仍是本地 A 面。 */
    private fun currentAudience(): HomeAudience = runCatching {
        when (ChannelUserController.getCurrentChannel()) {
            ChannelUserController.UserChannelType.PAID -> HomeAudience.PAID
            ChannelUserController.UserChannelType.NATURAL -> HomeAudience.NATURAL
        }
    }.getOrElse { error ->
        HomeVariantDiagnostics.logEventError(
            event = "channel_read_failed",
            errorType = error.javaClass.simpleName,
            details = "fallback=NATURAL",
        )
        HomeAudience.NATURAL
    }
}
