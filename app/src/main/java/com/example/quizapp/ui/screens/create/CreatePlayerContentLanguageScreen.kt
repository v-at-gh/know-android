package com.example.quizapp.ui.screens.create

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.R
import com.example.quizapp.data.prefs.PreferencesRepository
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.common.LanguageLoadingOption
import com.example.quizapp.ui.common.LanguageOption
import com.example.quizapp.ui.common.LanguageUnavailableOption
import com.example.quizapp.ui.common.drawVerticalScrollbar
import com.example.quizapp.ui.common.languageDisplayName
import com.example.quizapp.ui.common.sortLanguages
import com.example.quizapp.ui.theme.QuizAppTheme

/**
 * Composable screen step in player creation for selecting a primary content language.
 *
 * @param onDone Callback to complete player creation.
 * @param onBack Callback to return to the previous step.
 * @param viewModel ViewModel holding player creation state.
 */
@Composable
fun CreatePlayerContentLanguageScreen(
    onDone: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CreatePlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CreatePlayerContentLanguageContent(
        state = state,
        onSelectLanguage = viewModel::setPrimaryLanguage,
        onInstallLanguage = viewModel::installLanguage,
        onNext = {
            viewModel.createPlayer { id ->
                viewModel.reset()
                onDone(id)
            }
        },
        onBack = onBack,
    )
}

@Composable
fun CreatePlayerContentLanguageContent(
    state: CreatePlayerUiState,
    onSelectLanguage: (String) -> Unit,
    onInstallLanguage: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AppScreen(modifier = modifier) {
        if (isLandscape) {
            CreatePlayerContentLanguageLandscape(
                state = state,
                onSelectLanguage = onSelectLanguage,
                onInstallLanguage = onInstallLanguage,
                onNext = onNext,
                onBack = onBack,
            )
        } else {
            CreatePlayerContentLanguagePortrait(
                state = state,
                onSelectLanguage = onSelectLanguage,
                onInstallLanguage = onInstallLanguage,
                onNext = onNext,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CreatePlayerContentLanguagePortrait(
    state: CreatePlayerUiState,
    onSelectLanguage: (String) -> Unit,
    onInstallLanguage: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.create_player_language_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.create_player_language_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawVerticalScrollbar(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val languagesToShow = if (state.isBundledMode) {
                sortLanguages(state.bundledCompleteLanguages)
            } else {
                sortLanguages(state.availableLanguages)
            }

            languagesToShow.forEach { lang ->
                val isSelected = lang == state.selectedPrimaryLanguage
                val isReady = lang == PreferencesRepository.DEFAULT_LANGUAGE ||
                        lang in state.importedLanguages
                val isImporting = lang in state.importingLanguages || lang == state.installingTargetLang

                LanguageItemCard(
                    lang = lang,
                    isSelected = isSelected,
                    isReady = isReady,
                    isImporting = isImporting,
                    onSelect = { onSelectLanguage(lang) },
                    onInstall = { onInstallLanguage(lang) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_back))
            }
            Button(
                onClick = onNext,
                enabled = state.canContinueFromLanguage,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}

@Composable
private fun CreatePlayerContentLanguageLandscape(
    state: CreatePlayerUiState,
    onSelectLanguage: (String) -> Unit,
    onInstallLanguage: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.create_player_language_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.create_player_language_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_back))
                }
                Button(
                    onClick = onNext,
                    enabled = state.canContinueFromLanguage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxSize()
                .drawVerticalScrollbar(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val languagesToShow = if (state.isBundledMode) {
                sortLanguages(state.bundledCompleteLanguages)
            } else {
                sortLanguages(state.availableLanguages)
            }

            languagesToShow.forEach { lang ->
                val isSelected = lang == state.selectedPrimaryLanguage
                val isReady = lang == PreferencesRepository.DEFAULT_LANGUAGE ||
                        lang in state.importedLanguages
                val isImporting = lang in state.importingLanguages || lang == state.installingTargetLang

                LanguageItemCard(
                    lang = lang,
                    isSelected = isSelected,
                    isReady = isReady,
                    isImporting = isImporting,
                    onSelect = { onSelectLanguage(lang) },
                    onInstall = { onInstallLanguage(lang) },
                )
            }
        }
    }
}

@Composable
private fun LanguageItemCard(
    lang: String,
    isSelected: Boolean,
    isReady: Boolean,
    isImporting: Boolean,
    onSelect: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
    ) {
        when {
            isImporting -> {
                LanguageLoadingOption(
                    label = languageDisplayName(lang),
                    subtitle = stringResource(R.string.settings_language_installing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            !isReady -> {
                LanguageUnavailableOption(
                    label = languageDisplayName(lang),
                    actionLabel = stringResource(R.string.settings_language_install),
                    onAction = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            else -> {
                LanguageOption(
                    label = languageDisplayName(lang),
                    selected = isSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            onClick = onSelect,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ===== PREVIEWS =====

private val sampleCreateLanguageState = CreatePlayerUiState(
    availableLanguages = listOf("en", "ru", "de", "fr"),
    selectedPrimaryLanguage = "ru",
    importedLanguages = setOf("en", "ru"),
    importingLanguages = setOf("de"),
)

@Preview(name = "Portrait Default", showBackground = true)
@Composable
private fun CreatePlayerContentLanguagePreview_Portrait() {
    QuizAppTheme {
        CreatePlayerContentLanguageContent(
            state = sampleCreateLanguageState,
            onSelectLanguage = {},
            onInstallLanguage = {},
            onNext = {},
            onBack = {},
        )
    }
}
