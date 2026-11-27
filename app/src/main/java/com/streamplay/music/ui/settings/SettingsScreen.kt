package com.streamplay.music.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamplay.music.R
import com.streamplay.music.core.telemetry.Screen
import com.streamplay.music.core.telemetry.ScreenViewed
import com.streamplay.music.core.telemetry.SettingsAction
import com.streamplay.music.core.telemetry.SettingsUsed
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.data.settings.AppLanguage
import com.streamplay.music.data.settings.LanguageChange
import com.streamplay.music.ui.actions.StreamSheet
import com.streamplay.music.ui.app.AppNavigator
import com.streamplay.music.ui.app.LocalSession
import com.streamplay.music.ui.components.CollapsingTitleBar
import com.streamplay.music.ui.components.RowDivider
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay
import com.streamplay.music.ui.theme.StreamType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navigator: AppNavigator, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = LocalSession.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var pickingLanguage by rememberSaveable { mutableStateOf(false) }
    val linkMissing = stringResource(R.string.settings_link_unavailable)
    val noBrowser = stringResource(R.string.settings_link_open_failed)
    val feedbackEmail = stringResource(R.string.feedback_email)
    val noEmailApp = stringResource(R.string.settings_feedback_open_failed)
    val changeFailed = stringResource(R.string.settings_language_change_failed)

    LaunchedEffect(Unit) { Telemetry.log(ScreenViewed(Screen.SETTINGS)) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    fun openLink(url: String?, action: SettingsAction) {
        Telemetry.log(SettingsUsed(action))
        if (url == null) {
            scope.launch { session.snackbar.showSnackbar(linkMissing) }
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            scope.launch { session.snackbar.showSnackbar(noBrowser) }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = { CollapsingTitleBar(stringResource(R.string.settings_title), navigator::back, scrollBehavior) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsCard(StreamPlay.colors.cardGrouped) {
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    label = stringResource(R.string.settings_language),
                    value = stringResource(state.language.labelRes),
                    onClick = {
                        Telemetry.log(SettingsUsed(SettingsAction.OPEN_LANGUAGE))
                        pickingLanguage = true
                    },
                )
            }
            SettingsCard(StreamPlay.colors.recessed) {
                SettingsRow(
                    icon = Icons.Outlined.PrivacyTip,
                    label = stringResource(R.string.settings_privacy_policy),
                    onClick = { openLink(state.privacyPolicyUrl, SettingsAction.OPEN_PRIVACY_POLICY) },
                )
                RowDivider(startInset = 58.dp, endInset = 16.dp)
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    label = stringResource(R.string.settings_terms_of_service),
                    onClick = { openLink(state.termsOfServiceUrl, SettingsAction.OPEN_TERMS_OF_SERVICE) },
                )
                RowDivider(startInset = 58.dp, endInset = 16.dp)
                SettingsRow(
                    icon = Icons.Outlined.Email,
                    label = stringResource(R.string.settings_feedback),
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$feedbackEmail".toUri()))
                        } catch (_: ActivityNotFoundException) {
                            scope.launch { session.snackbar.showSnackbar(noEmailApp) }
                        }
                    },
                )
            }
        }
    }

    if (pickingLanguage) {
        LanguageSheet(
            current = state.language,
            options = state.languages,
            onDismiss = { pickingLanguage = false },
            onChoose = { language ->
                pickingLanguage = false
                // AppCompat may recreate the activity right away on success.
                if (viewModel.choose(language) == LanguageChange.FAILED) scope.launch { session.snackbar.showSnackbar(changeFailed) }
            },
        )
    }
}

@Composable
private fun SettingsCard(color: Color, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = color,
        border = BorderStroke(1.dp, StreamPlay.colors.cardGroupedEdge),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(StreamPlay.colors.lilacTile, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = StreamType.songArtist.fontWeight), modifier = Modifier.weight(1f))
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.padding(start = 8.dp, end = 6.dp),
            )
        }
    }
}

@Composable
private fun LanguageSheet(
    current: AppLanguage,
    options: List<AppLanguage>,
    onDismiss: () -> Unit,
    onChoose: (AppLanguage) -> Unit,
) {
    StreamSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.language_picker_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 18.dp))
        Text(
            stringResource(R.string.language_picker_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 16.dp),
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = StreamPlay.colors.recessed,
            border = BorderStroke(1.dp, StreamPlay.colors.cardGroupedEdge),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEachIndexed { index, language ->
                    val selected = language == current
                    if (index > 0 && !selected && options[index - 1] != current) RowDivider(startInset = 14.dp, endInset = 14.dp)
                    ChoiceRow(
                        label = stringResource(language.labelRes),
                        selected = selected,
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onChoose(language) },
                    )
                }
            }
        }
    }
}

/** Single-choice row: highlighted with a lilac outline when selected, radio indicator at the end. */
@Composable
fun ChoiceRow(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (selected) {
                    Modifier
                        .padding(vertical = 3.dp)
                        .background(StreamPlay.colors.lilacTint, RoundedCornerShape(12.dp))
                        .border(1.dp, Palette.Lilac, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) StreamType.songTitle.fontWeight else StreamType.songArtist.fontWeight),
            color = if (selected) Palette.Lilac else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Palette.Lilac, unselectedColor = StreamPlay.colors.radioIdle),
        )
    }
}
