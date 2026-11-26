package com.amberwave.player.feature.hub

import android.util.Log
import com.google.gson.JsonParser
import java.util.Locale

/** 首页产品形态。代码使用语义名称，避免把远端 A/B 实验代号扩散到业务层。 */
enum class HomeVariant {
    LOCAL,
    ONLINE,
}

/** 与 CoreKit 渠道枚举解耦的首页受众模型，同时作为远端 JSON 字段名。 */
internal enum class HomeAudience(val configKey: String) {
    NATURAL("natural"),
    PAID("paid"),
}

internal data class HomeVariantResolution(
    val audience: HomeAudience,
    val selectedValue: String?,
    val mode: HomeVariant,
)

/** 首页模式状态。启动初始化与前台刷新分别使用独立入口，避免普通回调意外热切换页面。 */
internal object HomeVariantStore {
    @Volatile
    private var initialized = false

    @Volatile
    private var sessionMode = HomeVariant.LOCAL

    @Volatile
    private var sessionAudience = HomeAudience.NATURAL

    val current: HomeVariant
        get() = sessionMode

    val audience: HomeAudience
        get() = sessionAudience

    @Synchronized
    fun initialize(rawValue: String?, audience: HomeAudience) {
        if (initialized) return
        val resolution = resolveHomeExperienceMode(rawValue, audience)
        sessionMode = resolution.mode
        sessionAudience = audience
        initialized = true
    }

    /** 仅供首页 onResume 的显式检测调用；返回值用于决定是否重建页面依赖。 */
    @Synchronized
    fun updateFromForeground(rawValue: String?, audience: HomeAudience): Boolean {
        val resolution = resolveHomeExperienceMode(rawValue, audience)
        val changed = initialized && resolution.mode != sessionMode
        sessionMode = resolution.mode
        sessionAudience = audience
        initialized = true
        return changed
    }
}

/**
 * A/B 面诊断日志的统一出口。只记录非敏感的模式参数，并限制长度、移除换行，避免污染日志。
 */
internal object HomeVariantDiagnostics {
    const val TAG = "MusicHomeMode"

    fun logRemoteValue(source: String, rawValue: String?, audience: HomeAudience) {
        val resolution = resolveHomeExperienceMode(rawValue, audience)
        val session = HomeVariantStore.current
        val effectiveTiming = if (resolution.mode == session) "current_session" else "next_refresh"
        Log.i(
            TAG,
            "source=$source, channel=${audience.name}, remoteValue=${rawValue.forLog()}, " +
                "selectedValue=${resolution.selectedValue.forLog()}, parsed=${resolution.mode}, " +
                "session=$session, effective=$effectiveTiming",
        )
    }

    fun logActivitySelection() {
        Log.i(
            TAG,
            "source=HostActivity, channel=${HomeVariantStore.audience.name}, " +
                "selectedMode=${HomeVariantStore.current}",
        )
    }

    /** Remote Config 生命周期日志不携带配置值，可安全用于确认 fetch/activate/listener 是否执行。 */
    fun logEvent(event: String, details: String = "") {
        Log.i(TAG, buildString {
            append("event=")
            append(event)
            if (details.isNotBlank()) {
                append(", ")
                append(details)
            }
        })
    }

    fun logEventError(event: String, errorType: String, details: String = "") {
        Log.w(TAG, buildString {
            append("event=")
            append(event)
            append(", error=")
            append(errorType)
            if (details.isNotBlank()) {
                append(", ")
                append(details)
            }
        })
    }

    private fun String?.forLog(): String = this
        ?.trim()
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.take(MAX_LOG_VALUE_LENGTH)
        ?.takeIf(String::isNotEmpty)
        ?: "<missing>"

    private const val MAX_LOG_VALUE_LENGTH = 128
}

/** 根据渠道选择 JSON 分支；配置缺失、字段错误或非法模式一律安全回退默认 A 面。 */
internal fun resolveHomeExperienceMode(
    rawValue: String?,
    audience: HomeAudience,
): HomeVariantResolution {
    val selectedValue = rawValue
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { value -> runCatching { JsonParser.parseString(value) }.getOrNull() }
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?.entrySet()
        ?.firstOrNull { (key, _) -> key.equals(audience.configKey, ignoreCase = true) }
        ?.value
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    return HomeVariantResolution(
        audience = audience,
        selectedValue = selectedValue,
        mode = parseModeValue(selectedValue),
    )
}

private fun parseModeValue(value: String?): HomeVariant = when (
    value?.lowercase(Locale.ROOT)
) {
    "online", "b" -> HomeVariant.ONLINE
    "local", "a" -> HomeVariant.LOCAL
    else -> HomeVariant.LOCAL
}
