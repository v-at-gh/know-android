package com.example.quizapp.ui.screens.create

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.ui.screens.create.CreatePlayerUiState
import com.example.quizapp.ui.screens.create.CreatePlayerViewModel
import com.example.quizapp.R
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.common.LanguageOption
import com.example.quizapp.ui.common.Localizations
import com.example.quizapp.ui.common.drawVerticalScrollbar
import com.example.quizapp.ui.theme.QuizAppTheme

/**
 * Composable screen step in player creation for selecting the UI application language.
 *
 * @param onNext Callback to proceed to the next step.
 * @param onBack Callback to return to the previous step.
 * @param viewModel ViewModel holding player creation state.
 */
@Composable
fun CreatePlayerUiLanguageScreen(
    onNext: () -> Unit,
    onDone: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CreatePlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CreatePlayerUiLanguageContent(
        state = state,
        onSelectUiLanguage = viewModel::setUiLanguage,
        onNext = {
            if (state.isBundledMode) {
                onNext()
            } else {
                viewModel.createPlayerQuick { id ->
                    viewModel.reset()
                    onDone(id)
                }
            }
        },
        onBack = onBack,
    )
}

@Composable
fun CreatePlayerUiLanguageContent(
    state: CreatePlayerUiState,
    onSelectUiLanguage: (String?) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AppScreen(modifier = modifier) {
        if (isLandscape) {
            CreatePlayerUiLanguageLandscape(
                state = state,
                onSelectUiLanguage = onSelectUiLanguage,
                onNext = onNext,
                onBack = onBack,
            )
        } else {
            CreatePlayerUiLanguagePortrait(
                state = state,
                onSelectUiLanguage = onSelectUiLanguage,
                onNext = onNext,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CreatePlayerUiLanguagePortrait(
    state: CreatePlayerUiState,
    onSelectUiLanguage: (String?) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .drawVerticalScrollbar(scrollState)
                .verticalScroll(scrollState)
                .padding(top = 64.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.create_player_ui_language_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.create_player_ui_language_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            // Option 1: System
            val systemLabel = stringResource(
                R.string.system_language_option,
                Localizations.systemDisplayName(),
            )
            UiLanguageItemCard(
                label = systemLabel,
                isSelected = state.uiLanguage == null,
                onSelect = { onSelectUiLanguage(null) },
            )

            // Options: Bundled UI languages
            Localizations.sortedUiLanguages().forEach { langCode ->
                val isSelected = state.uiLanguage == langCode
                UiLanguageItemCard(
                    label = Localizations.displayName(langCode),
                    isSelected = isSelected,
                    onSelect = { onSelectUiLanguage(langCode) },
                )
            }
        }

        // Floating Back
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }

        // Floating Next
        Button(
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.action_next))
        }
    }
}

@Composable
private fun CreatePlayerUiLanguageLandscape(
    state: CreatePlayerUiState,
    onSelectUiLanguage: (String?) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.create_player_ui_language_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.create_player_ui_language_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxSize()
                    .drawVerticalScrollbar(scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val systemLabel = stringResource(
                    R.string.system_language_option,
                    Localizations.systemDisplayName(),
                )
                UiLanguageItemCard(
                    label = systemLabel,
                    isSelected = state.uiLanguage == null,
                    onSelect = { onSelectUiLanguage(null) },
                )

                Localizations.sortedUiLanguages().forEach { langCode ->
                    val isSelected = state.uiLanguage == langCode
                    UiLanguageItemCard(
                        label = Localizations.displayName(langCode),
                        isSelected = isSelected,
                        onSelect = { onSelectUiLanguage(langCode) },
                    )
                }
            }
        }

        // Floating Back
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }

        // Floating Next
        Button(
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.action_next))
        }
    }
}

@Composable
private fun UiLanguageItemCard(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            },
        ),
    ) {
        LanguageOption(
            label = label,
            selected = isSelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// ===== PREVIEWS =====

@Preview(name = "Portrait Default", showBackground = true)
@Composable
private fun CreatePlayerUiLanguagePreview_Portrait() {
    QuizAppTheme {
        CreatePlayerUiLanguageContent(
            state = CreatePlayerUiState(),
            onSelectUiLanguage = {},
            onNext = {},
            onBack = {},
        )
    }
}
