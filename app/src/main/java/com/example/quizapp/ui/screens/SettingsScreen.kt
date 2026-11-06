package com.example.quizapp.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.BuildConfig
import com.example.quizapp.QuizApplication
import com.example.quizapp.R
import com.example.quizapp.data.dao.DomainWithTranslation
import com.example.quizapp.data.net.PingServer
import com.example.quizapp.ui.common.Localizations
import com.example.quizapp.ui.common.languageDisplayName
import com.example.quizapp.ui.theme.QuizAppTheme

/**
 * Composable screen for app settings including language, domain preferences, look & feel,
 * player switching, and developer options.
 *
 * @param playerId The active player ID.
 * @param onOpenUiLanguage Callback to open UI language selection.
 * @param onOpenLanguage Callback to open content language selection.
 * @param onOpenDomains Callback to open domain selection.
 * @param onOpenDeveloper Callback to open developer settings.
 * @param onOpenLookAndFeel Callback to open look & feel settings.
 * @param onSwitchPlayer Callback to switch active player.
 * @param viewModel ViewModel providing settings state.
 */
@Composable
fun SettingsScreen(
    playerId: Long,
    onOpenUiLanguage: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenDomains: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenLookAndFeel: () -> Unit,
    onSwitchPlayer: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as QuizApplication
    val uiLanguage by app.preferences.uiLanguage.collectAsStateWithLifecycle()

    LaunchedEffect(playerId) {
        viewModel.load(playerId)
    }

    DisposableEffect(state.developerMode) {
        if (!state.developerMode) {
            return@DisposableEffect onDispose { }
        }
        val server = PingServer(port = 42069, onEvent = viewModel::recordIncomingEvent)
        server.start()
        onDispose { server.stop() }
    }

    SettingsContent(
        state = state,
        uiLanguage = uiLanguage,
        onOpenUiLanguage = onOpenUiLanguage,
        onOpenLanguage = onOpenLanguage,
        onOpenDomains = onOpenDomains,
        onOpenDeveloper = onOpenDeveloper,
        onOpenLookAndFeel = onOpenLookAndFeel,
        onSwitchPlayer = onSwitchPlayer,
        onSecretTap = viewModel::handleVersionTap,
    )
}

/**
 * Stateless content layout for [SettingsScreen].
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    uiLanguage: String?,
    onOpenUiLanguage: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenDomains: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenLookAndFeel: () -> Unit,
    onSwitchPlayer: () -> Unit,
    onSecretTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onSwitchPlayer,
                        ) {
                            Text(stringResource(R.string.settings_switch_player))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val uiLangSubtitle = if (uiLanguage == null) {
                        stringResource(R.string.system_language_option, Localizations.systemDisplayName())
                    } else {
                        Localizations.displayName(uiLanguage)
                    }
                    SettingsMenuRow(
                        title = stringResource(R.string.settings_ui_language_title),
                        subtitle = uiLangSubtitle,
                        onClick = onOpenUiLanguage,
                    )
                    SettingsMenuRow(
                        title = stringResource(R.string.settings_language_title),
                        subtitle = languageDisplayName(state.currentLanguage),
                        onClick = onOpenLanguage,
                    )
                    SettingsMenuRow(
                        title = stringResource(R.string.settings_domains_title),
                        subtitle = if (state.preferredDomainSlugs.isEmpty()) {
                            stringResource(R.string.settings_domains_none)
                        } else {
                            stringResource(
                                R.string.settings_domains_count,
                                state.preferredDomainSlugs.size,
                            )
                        },
                        onClick = onOpenDomains,
                    )
                    SettingsMenuRow(
                        title = "Look & feel",
                        subtitle = "Haptics and appearance",
                        onClick = onOpenLookAndFeel,
                    )

                    if (state.developerMode) {
                        Spacer(Modifier.height(8.dp))
                        SettingsMenuRow(
                            title = "Developer",
                            subtitle = "Dev server, pack management, diagnostics",
                            onClick = onOpenDeveloper,
                        )
                    }
                }

                VersionFooter(
                    version = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    onSecretTap = onSecretTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun VersionFooter(
    version: String,
    versionCode: Int,
    onSecretTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Quiz $version ($versionCode)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onSecretTap),
        )
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

// ===== PREVIEWS & FIXTURES =====

private val sampleSettingsState = SettingsUiState(
    isLoading = false,
    availableLanguages = listOf("en", "ru", "de", "fr"),
    currentLanguage = "en",
    hapticFeedback = true,
    availableDomains = listOf(
        DomainWithTranslation(1L, "math", "Mathematics", ""),
        DomainWithTranslation(2L, "physics", "Physics", ""),
        DomainWithTranslation(3L, "biology", "Biology", ""),
    ),
    preferredDomainSlugs = setOf("math", "physics"),
    developerMode = true,
    devServer = "192.168.1.20",
    localAddresses = listOf("192.168.1.42", "10.255.255.253"),
    incomingEvents = listOf("12:34:56 ping from 10.255.255.42"),
)

private val sampleSettingsLoadingState = SettingsUiState(
    isLoading = true,
)

@Preview(name = "Default", showBackground = true)
@Composable
private fun SettingsScreenPreview_Default() {
    QuizAppTheme {
        SettingsContent(
            state = sampleSettingsState,
            uiLanguage = null,
            onOpenUiLanguage = {},
            onOpenLanguage = {},
            onOpenDomains = {},
            onOpenDeveloper = {},
            onOpenLookAndFeel = {},
            onSwitchPlayer = {},
            onSecretTap = {},
        )
    }
}

@Preview(
    name = "Landscape Mode",
    device = "spec:parent=pixel_5,orientation=landscape",
    showBackground = true,
)
@Composable
private fun SettingsScreenPreview_Landscape() {
    QuizAppTheme {
        SettingsContent(
            state = sampleSettingsState,
            uiLanguage = null,
            onOpenUiLanguage = {},
            onOpenLanguage = {},
            onOpenDomains = {},
            onOpenDeveloper = {},
            onOpenLookAndFeel = {},
            onSwitchPlayer = {},
            onSecretTap = {},
        )
    }
}

@Preview(
    name = "Dark Theme",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsScreenPreview_DarkTheme() {
    QuizAppTheme(darkTheme = true) {
        SettingsContent(
            state = sampleSettingsState,
            uiLanguage = null,
            onOpenUiLanguage = {},
            onOpenLanguage = {},
            onOpenDomains = {},
            onOpenDeveloper = {},
            onOpenLookAndFeel = {},
            onSwitchPlayer = {},
            onSecretTap = {},
        )
    }
}

@Preview(name = "Loading State", showBackground = true)
@Composable
private fun SettingsScreenPreview_Loading() {
    QuizAppTheme {
        SettingsContent(
            state = sampleSettingsLoadingState,
            uiLanguage = null,
            onOpenUiLanguage = {},
            onOpenLanguage = {},
            onOpenDomains = {},
            onOpenDeveloper = {},
            onOpenLookAndFeel = {},
            onSwitchPlayer = {},
            onSecretTap = {},
        )
    }
}
