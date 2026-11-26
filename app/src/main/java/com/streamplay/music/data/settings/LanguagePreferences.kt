package com.streamplay.music.data.settings

import android.os.Looper
import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.streamplay.music.BuildConfig
import com.streamplay.music.R
import java.util.Locale

/**
 * Languages offered in Settings. Tags mirror res/xml/locales_config.xml and labels are written in
 * their own language so a user who picked an unfamiliar language can still find their way back.
 * An empty tag is the AndroidX convention for "follow the system".
 */
enum class AppLanguage(val tag: String, @param:StringRes val labelRes: Int) {
    SYSTEM("", R.string.settings_language_system_default),
    ENGLISH("en", R.string.settings_language_english),
    CHINESE_SIMPLIFIED("zh-CN", R.string.settings_language_chinese_simplified),
    JAPANESE("ja", R.string.settings_language_japanese),
    KOREAN("ko", R.string.settings_language_korean),
    INDONESIAN("id", R.string.settings_language_indonesian),
    MALAY("ms", R.string.settings_language_malay),
    GERMAN("de", R.string.settings_language_german),
    FRENCH("fr", R.string.settings_language_french),
    SPANISH("es", R.string.settings_language_spanish),
    VIETNAMESE("vi", R.string.settings_language_vietnamese);

    /** Stable, non-localized identifier for analytics. */
    val reportValue: String get() = tag.ifBlank { "system" }.lowercase(Locale.ROOT)

    companion object {
        /** Maps a stored locale tag back to a supported language; zh-TW never maps to Simplified Chinese. */
        fun match(tag: String?): AppLanguage? {
            if (tag.isNullOrBlank()) return null
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.let { return it }
            val locale = Locale.forLanguageTag(tag.replace('_', '-'))
            if (locale.language == "zh" && (locale.script.equals("Hans", ignoreCase = true) || '-' !in tag)) {
                return CHINESE_SIMPLIFIED
            }
            return entries.firstOrNull { '-' !in it.tag && it.tag.isNotEmpty() && it.tag == locale.language }
        }
    }
}

enum class LanguageChange { UNCHANGED, APPLIED, FAILED }

/**
 * Per-app language through the official AndroidX API, which covers Android 8 through Android 15+
 * (LocaleManager on 13+, AppCompat storage below). Also exposes the configured legal links.
 */
class LanguagePreferences {
    val privacyPolicyUrl: String? = BuildConfig.LEGAL_PRIVACY_URL.httpsOrNull()
    val termsOfServiceUrl: String? = BuildConfig.LEGAL_TERMS_URL.httpsOrNull()

    fun current(): AppLanguage {
        val tag = runCatching { AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag() }
            .onFailure { Log.w(TAG, "Unable to read the application locale", it) }
            .getOrNull()
        if (tag.isNullOrBlank()) return AppLanguage.SYSTEM
        return AppLanguage.match(tag) ?: AppLanguage.ENGLISH
    }

    /** Must run on the main thread because AppCompat may recreate the activity immediately. */
    fun apply(language: AppLanguage): LanguageChange {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Locale changes must happen on the main thread" }
        if (language == current()) return LanguageChange.UNCHANGED
        return runCatching {
            AppCompatDelegate.setApplicationLocales(
                if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language.tag),
            )
        }.fold(
            onSuccess = { LanguageChange.APPLIED },
            onFailure = {
                Log.e(TAG, "Unable to apply the application locale", it)
                LanguageChange.FAILED
            },
        )
    }

    private fun String.httpsOrNull(): String? =
        trim().takeIf { it.startsWith("https://", ignoreCase = true) && it.length > "https://".length }

    private companion object {
        const val TAG = "LanguagePreferences"
    }
}
