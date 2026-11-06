package com.example.quizapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.quizapp.R
import com.example.quizapp.data.qap.QapInstallState

/**
 * Modal bottom sheet displaying detailed QAP pack info, domain/language lists, and action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PackDetailsBottomSheet(
    row: PackRowState,
    installState: QapInstallState = QapInstallState.Idle,
    enabledDomains: Set<String>? = null,
    enabledLanguages: Set<String>? = null,
    onInstallOrUpdate: () -> Unit,
    onUninstall: (() -> Unit)? = null,
    onToggleAllDomains: ((Boolean) -> Unit)? = null,
    onToggleAllLanguages: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        val entry = row.entry
        val isInstalling = installState is QapInstallState.Downloading && installState.id == entry.id ||
                          installState is QapInstallState.Verifying && installState.id == entry.id ||
                          installState is QapInstallState.Importing && installState.id == entry.id
        val isDeleting = installState is QapInstallState.Deleting && installState.id == entry.id

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(entry.id, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${entry.version} · ${entry.kind} · ${"%.1f".format(entry.sizeMb)} MB",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Totals
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${entry.totals["questions"] ?: 0} questions", style = MaterialTheme.typography.bodySmall)
                Text("${entry.totals["articles"] ?: 0} articles", style = MaterialTheme.typography.bodySmall)
            }

            // Languages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Languages", style = MaterialTheme.typography.titleSmall)
                if (onToggleAllLanguages != null && enabledLanguages != null) {
                    Row {
                        TextButton(onClick = { onToggleAllLanguages(true) }) {
                            Text(stringResource(R.string.action_all), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onToggleAllLanguages(false) }) {
                            Text(stringResource(R.string.action_none), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (enabledLanguages != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    entry.languages.forEach { lang ->
                        val isOn = lang in enabledLanguages
                        val name = languageDisplayName(lang)
                        Surface(
                            color = if (isOn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = (if (isOn) "✓ " else "") + name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (isOn) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Text(
                    entry.languages.joinToString(", ") { languageDisplayName(it) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Domains
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Domains", style = MaterialTheme.typography.titleSmall)
                if (onToggleAllDomains != null && enabledDomains != null) {
                    Row {
                        TextButton(onClick = { onToggleAllDomains(true) }) {
                            Text(stringResource(R.string.action_all), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onToggleAllDomains(false) }) {
                            Text(stringResource(R.string.action_none), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (enabledDomains != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    entry.domains.forEach { domain ->
                        val isOn = domain in enabledDomains
                        Surface(
                            color = if (isOn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = (if (isOn) "✓ " else "") + domain,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (isOn) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Text(
                    entry.domains.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action button
            when {
                isDeleting -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Uninstalling...")
                }
                isInstalling -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Installing...")
                }
                !row.installed -> Button(onClick = onInstallOrUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_install))
                }
                row.updateAvailable -> Button(onClick = onInstallOrUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_update))
                }
                onUninstall != null -> OutlinedButton(
                    onClick = onUninstall,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pack_uninstall))
                }
                else -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pack_installed))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
