package com.example.quizapp.ui.screens.settings

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.QuizApplication
import com.example.quizapp.R
import com.example.quizapp.data.qap.QapInstallState
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.common.PackDetailsBottomSheet
import com.example.quizapp.ui.common.PackRowState
import com.example.quizapp.ui.screens.PingUiState
import com.example.quizapp.ui.screens.SettingsViewModel
import com.example.quizapp.ui.screens.SyncUiState
import kotlinx.coroutines.launch

/**
 * Composable screen providing developer diagnostics, dev server connection configuration,
 * server ping testing, local IP inspection, and QAP pack management.
 *
 * @param onBack Callback to navigate back.
 * @param modifier Optional layout modifier.
 * @param viewModel ViewModel managing developer settings state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeveloperSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // val clipboardManager = LocalClipboardManager.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var pendingRow by remember { mutableStateOf<PackRowState?>(null) }
    var pendingUninstallRow by remember { mutableStateOf<PackRowState?>(null) }

    val scrollState = rememberScrollState()

    AppScreen(
        modifier = modifier,
        onBack = onBack,
        scrollState = scrollState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.settings_developer_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 48.dp),
            )

            OutlinedTextField(
                value = state.devServer,
                onValueChange = viewModel::setDevServer,
                label = { Text("Dev server") },
                placeholder = { Text("192.168.1.20") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            Button(
                onClick = viewModel::pingAdmin,
                enabled = state.pingState !is PingUiState.InFlight && state.devServer.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.pingState is PingUiState.InFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.developer_ping))
            }

            val pingState = state.pingState
            val (statusText, statusColor) = when (pingState) {
                is PingUiState.Idle -> "idle" to MaterialTheme.colorScheme.onSurfaceVariant
                is PingUiState.InFlight -> "connecting..." to MaterialTheme.colorScheme.primary
                is PingUiState.Success -> "${pingState.rttMs} ms" to Color(0xFF2E7D32)
                is PingUiState.Timeout -> "timeout after 5s" to Color(0xFFE65100)
                is PingUiState.Failure -> pingState.reason to MaterialTheme.colorScheme.error
            }
            Text(
                text = "Status: $statusText",
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
            )

            Spacer(Modifier.height(4.dp))
            Text("Packs", style = MaterialTheme.typography.titleSmall)

            Button(
                onClick = viewModel::forceSyncFeedback,
                enabled = state.syncState !is SyncUiState.InProgress && state.devServer.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.syncState is SyncUiState.InProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Force Sync Feedback")
                }
            }

            when (val s = state.syncState) {
                is SyncUiState.Success -> Text("Synced ${s.count} items", color = Color(0xFF2E7D32))
                is SyncUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                else -> {}
            }

            Button(
                onClick = viewModel::refreshPackCatalog,
                enabled = state.devServer.isNotBlank() && !state.packCatalogLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.packCatalogLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.developer_refresh_catalog))
            }

            state.packCatalogError?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            pendingRow?.let { row ->
                val entry = row.entry
                val isUpdate = row.updateAvailable
                val titleText = if (isUpdate) "Update ${entry.id}?" else "Install ${entry.id}?"
                val prevVersion = state.installedPacks[entry.id]?.version?.takeIf { it.isNotBlank() }
                val versionStr = if (isUpdate && prevVersion != null) "$prevVersion → ${entry.version}" else entry.version
                val baseMsg = if (isUpdate) {
                    "Update ${entry.id} $versionStr? ${"%.1f".format(entry.sizeMb)} MB"
                } else {
                    "Install ${entry.id} $versionStr? ${"%.1f".format(entry.sizeMb)} MB · ${entry.languages.joinToString(", ")}"
                }
                val msgText = if (row.missingLanguages.isNotEmpty()) {
                    "$baseMsg · missing: ${row.missingLanguages.joinToString(", ")}"
                } else {
                    baseMsg
                }
                val confirmText = if (isUpdate) "Update" else "Install"

                AlertDialog(
                    onDismissRequest = { pendingRow = null },
                    title = { Text(titleText) },
                    text = { Text(msgText) },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingRow = null
                            viewModel.installPack(entry)
                        }) { Text(confirmText) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingRow = null }) { Text("Cancel") }
                    },
                )
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

            for (row in state.packCatalog) {
                PackRow(
                    row = row,
                    installState = state.installState,
                    onInstallOrUpdate = { pendingRow = row },
                    onShowDetails = { viewModel.showPackDetails(row) },
                )
            }

            val selectedPack = state.selectedPackForDetails
            if (selectedPack != null) {
                PackDetailsBottomSheet(
                    row = selectedPack,
                    installState = state.installState,
                    enabledDomains = if (selectedPack.installed) state.preferredDomainSlugs else null,
                    enabledLanguages = if (selectedPack.installed) state.contentLanguages else null,
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

            when (val s = state.installState) {
                is QapInstallState.Downloading -> {
                    val frac = if (s.totalBytes > 0) s.bytesRead.toFloat() / s.totalBytes else 0f
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Downloading ${s.id} · ${s.bytesRead / 1024} / ${s.totalBytes / 1024} KB",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is QapInstallState.Verifying -> Text("Verifying ${s.id}…", style = MaterialTheme.typography.bodySmall)
                is QapInstallState.Importing -> Text("Importing ${s.id}…", style = MaterialTheme.typography.bodySmall)
                is QapInstallState.Deleting -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Uninstalling ${s.id}…", style = MaterialTheme.typography.bodySmall)
                }
                is QapInstallState.Done -> Text(
                    "Installed ${s.id}: ${s.domains} domains, ${s.questions} questions",
                    color = Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { viewModel.clearInstallState() },
                )
                is QapInstallState.Uninstalled -> Text(
                    "Uninstalled ${s.id} successfully",
                    color = Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { viewModel.clearInstallState() },
                )
                is QapInstallState.Failed -> Text(
                    "Failed ${s.id}: ${s.reason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { viewModel.clearInstallState() },
                )
                QapInstallState.Idle -> Unit
            }

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.developer_this_device), style = MaterialTheme.typography.titleSmall)
            if (state.localAddresses.isEmpty()) {
                Text(
                    text = "No network addresses found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (address in state.localAddresses) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            // onClick = {
                            //     clipboardManager.setText(AnnotatedString(address))
                            // },
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText("address", address).toClipEntry()
                                    )
                                }
                            },
                        ) {
                            Text(stringResource(R.string.action_copy), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.developer_incoming), style = MaterialTheme.typography.titleSmall)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.incomingEvents.isEmpty()) {
                        Text(
                            text = "No incoming pings yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        for (event in state.incomingEvents) {
                            Text(
                                text = event,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackRow(
    row: PackRowState,
    installState: QapInstallState,
    onInstallOrUpdate: () -> Unit,
    onShowDetails: () -> Unit,
) {
    val entry = row.entry
    val isInstalling = installState is QapInstallState.Downloading && installState.id == entry.id ||
                      installState is QapInstallState.Verifying && installState.id == entry.id ||
                      installState is QapInstallState.Importing && installState.id == entry.id
    val isDeleting = installState is QapInstallState.Deleting && installState.id == entry.id
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isInstalling || isDeleting) {
                        // ignore click while installing or deleting
                    } else if (!row.installed || row.updateAvailable) {
                        onInstallOrUpdate()
                    } else {
                        onShowDetails()
                    }
                },
                onLongClick = onShowDetails
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.id, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${entry.version} · ${entry.kind} · ${"%.1f".format(entry.sizeMb)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (row.coveredLanguages.isNotEmpty()) {
                        Text(
                            text = "covered: ${row.coveredLanguages.joinToString(" ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32),
                        )
                    }
                    if (row.missingLanguages.isNotEmpty()) {
                        Text(
                            text = "missing: ${row.missingLanguages.joinToString(" ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                isDeleting -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Uninstalling...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                isInstalling -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Installing...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                !row.installed -> {
                    Button(onClick = onInstallOrUpdate) {
                        Text("Install", style = MaterialTheme.typography.bodySmall)
                    }
                }
                row.updateAvailable -> {
                    Button(onClick = onInstallOrUpdate) {
                        Text("Update", style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                    ) {
                        Text("Installed", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
