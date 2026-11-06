package com.example.quizapp.ui.screens.settings

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.quizapp.QuizApplication
import com.example.quizapp.R
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.common.LanguageOption
import com.example.quizapp.ui.common.Localizations
import com.example.quizapp.ui.common.drawVerticalScrollbar
import com.example.quizapp.ui.theme.QuizAppTheme

@Composable
fun SettingsUiLanguageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as QuizApplication
    val uiLanguage by app.preferences.uiLanguage.collectAsStateWithLifecycle()

    LaunchedEffect(uiLanguage) {
        val locales = if (uiLanguage == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(uiLanguage!!)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    val scrollState = rememberScrollState()

    AppScreen(
        modifier = modifier,
        onBack = onBack,
        scrollState = scrollState,
    ) {
        SettingsUiLanguageContent(
            selectedUiLanguage = uiLanguage,
            onSelectUiLanguage = { lang ->
                app.preferences.setUiLanguage(lang)
            },
            scrollState = scrollState,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun SettingsUiLanguageContent(
    selectedUiLanguage: String?,
    onSelectUiLanguage: (String?) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        SettingsUiLanguageLandscape(
            selectedUiLanguage = selectedUiLanguage,
            onSelectUiLanguage = onSelectUiLanguage,
            scrollState = scrollState,
            modifier = modifier,
        )
    } else {
        SettingsUiLanguagePortrait(
            selectedUiLanguage = selectedUiLanguage,
            onSelectUiLanguage = onSelectUiLanguage,
            scrollState = scrollState,
            modifier = modifier,
        )
    }
}

@Composable
private fun SettingsUiLanguagePortrait(
    selectedUiLanguage: String?,
    onSelectUiLanguage: (String?) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 64.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_ui_language_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 48.dp),
        )
        Spacer(Modifier.height(8.dp))

        val systemLabel = stringResource(
            R.string.system_language_option,
            Localizations.systemDisplayName(),
        )
        UiLanguageItemCard(
            label = systemLabel,
            isSelected = selectedUiLanguage == null,
            onSelect = { onSelectUiLanguage(null) },
        )

        Localizations.sortedUiLanguages().forEach { langCode ->
            val isSelected = selectedUiLanguage == langCode
            UiLanguageItemCard(
                label = Localizations.displayName(langCode),
                isSelected = isSelected,
                onSelect = { onSelectUiLanguage(langCode) },
            )
        }
    }
}

@Composable
private fun SettingsUiLanguageLandscape(
    selectedUiLanguage: String?,
    onSelectUiLanguage: (String?) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 64.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f),
        ) {
            Text(
                text = stringResource(R.string.settings_ui_language_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 48.dp),
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
                isSelected = selectedUiLanguage == null,
                onSelect = { onSelectUiLanguage(null) },
            )

            Localizations.sortedUiLanguages().forEach { langCode ->
                val isSelected = selectedUiLanguage == langCode
                UiLanguageItemCard(
                    label = Localizations.displayName(langCode),
                    isSelected = isSelected,
                    onSelect = { onSelectUiLanguage(langCode) },
                )
            }
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
private fun SettingsUiLanguagePreview_Portrait() {
    QuizAppTheme {
        AppScreen(onBack = {}) {
            SettingsUiLanguageContent(
                selectedUiLanguage = null,
                onSelectUiLanguage = {},
                scrollState = rememberScrollState(),
            )
        }
    }
}
