package com.amberwave.player.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.core.navigation.navigator
import com.amberwave.player.core.ui.ViewBindingFragment
import com.amberwave.player.databinding.FragmentSettingsBinding
import com.amberwave.player.core.ads.AdSwitchKey
import com.amberwave.player.core.ads.loadNative
import net.corekit.core.controller.AdSlotSwitchController

class SettingsFragment : ViewBindingFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate) {
    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(LocaleSettingsRepository())
    }
    private val settingsAdapter = SettingsAdapter(::onSettingClick)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) EventTracker.screenView(EventTracker.Screen.SETTINGS)
        childFragmentManager.setFragmentResultListener(LanguageSheet.REQUEST_KEY, this) { _, result ->
            result.getString(LanguageSheet.RESULT_LANGUAGE_TAG)?.let(::applyLanguage)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.title = getString(R.string.settings_title)
        binding.toolbar.setNavigationContentDescription(R.string.action_back)
        binding.toolbar.setNavigationOnClickListener { navigator.navigateUp() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            binding.toolbar.updatePadding(top = bars.top)
            insets
        }
        with(binding.settingsList) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = settingsAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
        viewModel.state.collectWhileStarted(::render)
        // 设置操作不使用插屏；底部原生广告失败时容器自动隐藏。
        if (AdSlotSwitchController.isEnabled(AdSwitchKey.SETTINGS_BOTTOM_NATIVE)) {
            requireActivity().loadNative(binding.adContainer, position = TAG)
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统“应用语言”页面返回时同步 Android 13+ 可能发生的外部修改。
        viewModel.refreshLocale()
    }

    private fun applyLanguage(tag: String) {
        val failed = viewModel.applyLanguage(tag) == LanguageApplyResult.FAILED
        EventTracker.settings(
            EventTracker.SettingsAction.APPLY_LANGUAGE,
            if (failed) EventTracker.Outcome.FAILURE else EventTracker.Outcome.SUCCESS,
            value = tag,
        )
        if (failed) toast(R.string.settings_language_change_failed)
    }

    private fun render(state: SettingsUiState) {
        settingsAdapter.submitList(
            listOf(
                SettingsRow(
                    SettingsAction.LANGUAGE,
                    R.string.settings_language,
                    R.drawable.ic_pref_language,
                    getString(state.currentLanguage.displayNameRes),
                ),
                SettingsRow(SettingsAction.PRIVACY_POLICY, R.string.settings_privacy_policy, R.drawable.ic_pref_privacy),
                SettingsRow(SettingsAction.TERMS_OF_SERVICE, R.string.settings_terms_of_service, R.drawable.ic_pref_terms),
            ),
        )
    }

    private fun onSettingClick(action: SettingsAction) {
        val state = viewModel.state.value
        when (action) {
            SettingsAction.LANGUAGE -> {
                EventTracker.settings(
                    action = EventTracker.SettingsAction.OPEN_LANGUAGE,
                    value = state.currentLanguage.analyticsValue,
                )
                if (childFragmentManager.findFragmentByTag(LanguageSheet.TAG) == null) {
                    LanguageSheet
                        .newInstance(state.currentLanguage.languageTag)
                        .show(childFragmentManager, LanguageSheet.TAG)
                }
            }
            SettingsAction.PRIVACY_POLICY ->
                openSecurePage(state.privacyPolicyUrl, EventTracker.SettingsAction.OPEN_PRIVACY_POLICY)
            SettingsAction.TERMS_OF_SERVICE ->
                openSecurePage(state.termsOfServiceUrl, EventTracker.SettingsAction.OPEN_TERMS_OF_SERVICE)
        }
    }

    private fun openSecurePage(url: String?, analyticsAction: EventTracker.SettingsAction) {
        if (url == null) {
            EventTracker.settings(analyticsAction, EventTracker.Outcome.FAILURE)
            toast(R.string.settings_link_unavailable)
            return
        }
        val opened = try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
        EventTracker.settings(analyticsAction, if (opened) EventTracker.Outcome.SUCCESS else EventTracker.Outcome.FAILURE)
        if (!opened) toast(R.string.settings_link_open_failed)
    }

    private companion object {
        const val TAG = "SettingsFragment"
    }
}
