package com.example.quizapp.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.R
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.common.languageDisplayName
import com.example.quizapp.ui.common.sortLanguages
import com.example.quizapp.ui.screens.SettingsUiState
import com.example.quizapp.ui.screens.SettingsViewModel
import com.example.quizapp.ui.theme.QuizAppTheme

/**
 * Composable screen for configuring primary and secondary content languages for quiz questions and articles.
 *
 * @param playerId The active player ID.
 * @param onBack Callback to navigate back.
 * @param modifier Optional layout modifier.
 * @param viewModel ViewModel backing settings state.
 */
@Composable
fun SettingsLanguageScreen(
    playerId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(playerId) {
        viewModel.load(playerId)
    }

    val scrollState = rememberScrollState()

    AppScreen(
        modifier = modifier,
        onBack = onBack,
        scrollState = scrollState,
    ) {
        SettingsLanguageContent(
            state = state,
            onSetPrimary = viewModel::setPrimaryLanguage,
            onAddFavorite = viewModel::toggleContentLanguage,
            onRemoveFavorite = viewModel::toggleContentLanguage,
            scrollState = scrollState,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Stateless UI content for [SettingsLanguageScreen].
 */
@Composable
fun SettingsLanguageContent(
    state: SettingsUiState,
    onSetPrimary: (String) -> Unit,
    onAddFavorite: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val favorites = sortLanguages(state.contentLanguages)
    val candidates = state.availableLanguages.filter { it !in state.contentLanguages }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 64.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_language_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 48.dp),
        )
        Text(
            text = stringResource(R.string.settings_language_primary_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_language_primary_title),
            style = MaterialTheme.typography.titleMedium,
        )

        favorites.forEach { lang ->
            val isPrimary = lang == state.primaryLanguage
            val hasContent = lang == "en" || lang in state.importedLanguages
            FavoriteLanguageItemCard(
                lang = lang,
                isPrimary = isPrimary,
                canRemove = !isPrimary,
                hasContent = hasContent,
                onActivate = { if (!isPrimary) onSetPrimary(lang) },
                onRemove = { onRemoveFavorite(lang) },
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showAddDialog = true },
            enabled = candidates.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_language_add))
        }
    }

    if (showAddDialog) {
        AddLanguageDialog(
            candidates = candidates,
            importedLanguages = state.importedLanguages,
            onSelect = { lang ->
                onAddFavorite(lang)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun FavoriteLanguageItemCard(
    lang: String,
    isPrimary: Boolean,
    canRemove: Boolean,
    hasContent: Boolean,
    onActivate: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(
                    selected = isPrimary,
                    onClick = onActivate,
                    role = Role.RadioButton,
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isPrimary, onClick = null)
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = languageDisplayName(lang),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasContent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                if (!hasContent) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "(No content)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.settings_language_remove),
                    )
                }
            }
        }
    }
}

// ===== PREVIEW =====

private val sampleSettingsLanguageState = SettingsUiState(
    isLoading = false,
    availableLanguages = listOf("en", "ru", "de", "fr", "es", "it"),
    currentLanguage = "ru",
    primaryLanguage = "ru",
    contentLanguages = setOf("ru", "en", "de"),
)

@Preview(name = "Default", showBackground = true)
@Composable
private fun SettingsLanguageContentPreview_Default() {
    QuizAppTheme {
        AppScreen(onBack = {}) {
            SettingsLanguageContent(
                state = sampleSettingsLanguageState,
                onSetPrimary = {},
                onAddFavorite = {},
                onRemoveFavorite = {},
                scrollState = rememberScrollState(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
