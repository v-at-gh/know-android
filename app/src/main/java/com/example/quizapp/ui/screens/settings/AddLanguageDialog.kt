package com.example.quizapp.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.quizapp.R
import com.example.quizapp.ui.common.Localizations
import com.example.quizapp.ui.common.drawVerticalScrollbar
import com.example.quizapp.ui.common.languageDisplayName

/**
 * Composable dialog allowing users to search and select candidate languages to add to content preferences.
 *
 * @param candidates List of available language codes to select from.
 * @param importedLanguages Set of imported content language codes.
 * @param onSelect Callback invoked when a language is chosen.
 * @param onDismiss Callback invoked to dismiss the dialog.
 */
@Composable
fun AddLanguageDialog(
    candidates: List<String>,
    importedLanguages: Set<String> = emptySet(),
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, candidates, importedLanguages) {
        val sorted = candidates.sortedWith(
            compareByDescending<String> { it == "en" || it in importedLanguages }
                .thenBy { it != "en" && it != Localizations.systemLanguageCode() }
                .thenBy { languageDisplayName(it) }
        )
        if (query.isBlank()) sorted
        else sorted.filter {
            languageDisplayName(it).contains(query, ignoreCase = true) ||
                it.contains(query, ignoreCase = true)
        }
    }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_add_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.settings_language_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .drawVerticalScrollbar(scrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_language_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        filtered.forEach { lang ->
                            val hasContent = lang == "en" || lang in importedLanguages
                            Card(
                                onClick = { onSelect(lang) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
