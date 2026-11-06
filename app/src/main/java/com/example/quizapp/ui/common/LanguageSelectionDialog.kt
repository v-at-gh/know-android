package com.example.quizapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.quizapp.R

/**
 * Dialog enabling language selection from a list of available options with status indicators.
 */
@Composable
fun LanguageSelectionDialog(
    availableLanguages: List<String>,
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
    unavailableLanguages: Set<String> = emptySet(),
    onUnavailableTapped: (String) -> Unit = {},
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.settings_language_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawVerticalScrollbar(scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sortLanguages(availableLanguages).forEach { lang ->
                    val isSelected = lang == currentLanguage
                    val isUnavailable = lang in unavailableLanguages

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    if (isUnavailable) {
                                        onDismissRequest()
                                        onUnavailableTapped(lang)
                                    } else {
                                        if (lang != currentLanguage) {
                                            onLanguageSelected(lang)
                                        }
                                        onDismissRequest()
                                    }
                                },
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = languageDisplayName(lang),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isUnavailable) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                enabled = !isUnavailable,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
