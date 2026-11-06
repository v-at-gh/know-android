package com.example.quizapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.QuizApplication
import com.example.quizapp.data.qap.QapInstallState
import com.example.quizapp.ui.common.PackDetailsBottomSheet
import com.example.quizapp.ui.common.PackRowState

/**
 * Composable screen presenting available QAP content packs for download, installation, and update.
 *
 * @param viewModel ViewModel backing the quiz catalog screen.
 */
@Composable
fun QuizCatalogScreen(
    viewModel: QuizCatalogViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingUninstallRow by remember { mutableStateOf<PackRowState?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading && state.packs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage != null && state.packs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = state.errorMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }
            }
            state.packs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No packs available. Go to Settings -> Developer to configure a server.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.packs, key = { it.entry.id }) { row ->
                        CatalogPackCard(
                            row = row,
                            installState = state.installState,
                            onClick = { viewModel.showPackDetails(row) },
                            onInstallOrUpdate = { viewModel.installPack(row.entry) },
                        )
                    }
                }
            }
        }

        pendingUninstallRow?.let { row ->
            val entry = row.entry
            val context = LocalContext.current
            val app = context.applicationContext as QuizApplication
            val domainsToDelete = remember(entry.id) { app.qapInstaller.getDomainsToDelete(entry) }

            AlertDialog(
                onDismissRequest = { pendingUninstallRow = null },
                title = { Text("Uninstall ${entry.id}?") },
                text = {
                    Text("This will remove ${domainsToDelete.size} domains and their questions. Your historical stats will not be deleted.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingUninstallRow = null
                            viewModel.uninstallPack(entry)
                        }
                    ) {
                        Text("Uninstall", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingUninstallRow = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        val selectedPack = state.selectedPackForDetails
        if (selectedPack != null) {
            PackDetailsBottomSheet(
                row = selectedPack,
                installState = state.installState,
                enabledDomains = if (selectedPack.installed) state.currentPlayerDomains else null,
                enabledLanguages = if (selectedPack.installed) state.currentPlayerLanguages else null,
                onInstallOrUpdate = {
                    viewModel.dismissPackDetails()
                    viewModel.installPack(selectedPack.entry)
                },
                onUninstall = {
                    viewModel.dismissPackDetails()
                    pendingUninstallRow = selectedPack
                },
                onToggleAllDomains = if (selectedPack.installed) {
                    { enable -> viewModel.togglePackDomains(selectedPack.entry, enable) }
                } else null,
                onToggleAllLanguages = if (selectedPack.installed) {
                    { enable -> viewModel.togglePackLanguages(selectedPack.entry, enable) }
                } else null,
                onDismiss = viewModel::dismissPackDetails,
            )
        }
    }
}

@Composable
private fun CatalogPackCard(
    row: PackRowState,
    installState: QapInstallState,
    onClick: () -> Unit,
    onInstallOrUpdate: () -> Unit,
) {
    val entry = row.entry
    val isInstalling = installState is QapInstallState.Downloading && installState.id == entry.id ||
                      installState is QapInstallState.Verifying && installState.id == entry.id ||
                      installState is QapInstallState.Importing && installState.id == entry.id
    val isDeleting = installState is QapInstallState.Deleting && installState.id == entry.id
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.id, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${entry.kind} · v${entry.version} · ${"%.1f".format(entry.sizeMb)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val questionsCount = entry.totals["questions"] ?: 0
                val articlesCount = entry.totals["articles"] ?: 0
                Text(
                    text = "${entry.domains.size} domains · ${entry.languages.size} languages · $questionsCount questions · $articlesCount articles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            when {
                isDeleting -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Uninstalling...")
                    }
                }
                isInstalling -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Installing...")
                    }
                }
                !row.installed -> {
                    Button(onClick = onInstallOrUpdate) {
                        Text("Install")
                    }
                }
                row.updateAvailable -> {
                    Button(onClick = onInstallOrUpdate) {
                        Text("Update")
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                    ) {
                        Text("Installed", color = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}
