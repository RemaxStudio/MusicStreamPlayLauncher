package com.streamplay.music.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamplay.music.core.telemetry.Outcome
import com.streamplay.music.core.telemetry.SettingsAction
import com.streamplay.music.core.telemetry.SettingsUsed
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.data.settings.AppLanguage
import com.streamplay.music.data.settings.LanguageChange
import com.streamplay.music.data.settings.LanguagePreferences
import com.streamplay.music.ui.app.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
data class SettingsUiState(
    val language: AppLanguage,
    val languages: List<AppLanguage> = AppLanguage.entries,
    val privacyPolicyUrl: String?,
    val termsOfServiceUrl: String?,
)

class SettingsViewModel(private val preferences: LanguagePreferences) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SettingsUiState(
            language = preferences.current(),
            privacyPolicyUrl = preferences.privacyPolicyUrl,
            termsOfServiceUrl = preferences.termsOfServiceUrl,
        ),
    )
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    /** The language may have been changed from system settings while the screen was hidden. */
    fun refresh() = mutableState.update { it.copy(language = preferences.current()) }

    fun choose(language: AppLanguage): LanguageChange {
        val result = preferences.apply(language)
        Telemetry.log(
            SettingsUsed(
                SettingsAction.APPLY_LANGUAGE,
                if (result == LanguageChange.FAILED) Outcome.FAILURE else Outcome.SUCCESS,
                language.reportValue,
            ),
        )
        if (result != LanguageChange.FAILED) mutableState.update { it.copy(language = language) }
        return result
    }

    companion object {
        val Factory = viewModelFactory { initializer { SettingsViewModel(container.languages) } }
    }
}
