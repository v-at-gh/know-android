package com.example.quizapp.ui.screens.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.R
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.common.DomainOption

/**
 * Composable screen step in player creation for selecting initial preferred knowledge domains
 * and completing player creation.
 *
 * @param onDone Callback invoked when player creation completes, passing new player ID.
 * @param onBack Callback to return to the previous step.
 * @param viewModel ViewModel holding player creation state.
 */
@Composable
fun CreatePlayerDomainsScreen(
    onDone: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CreatePlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.selectedPrimaryLanguage, state.importedLanguages) {
        viewModel.loadDomains()
    }
    AppScreen(modifier = Modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.create_player_domains_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.create_player_domains_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = viewModel::selectAllDomains) {
                        Text(stringResource(R.string.domain_select_all))
                    }
                    TextButton(onClick = viewModel::clearAllDomains) {
                        Text(stringResource(R.string.domain_clear_all))
                    }
                }
                state.availableDomains.forEach { domain ->
                    DomainOption(
                        label = domain.name,
                        selected = domain.slug in state.selectedDomainSlugs,
                        onToggle = { viewModel.toggleDomain(domain.slug) },
                    )
                }

                if (state.availableDomains.isEmpty()) {
                    Text(
                        text = "No domains available in the bundled assets. You can download content packs later in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            if (state.errorResId != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(state.errorResId!!),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = !state.isCreating,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_back))
                }
                Button(
                    onClick = {
                        viewModel.createPlayer { id ->
                            viewModel.reset()
                            onDone(id)
                        }
                    },
                    enabled = !state.isCreating,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(if (state.availableDomains.isEmpty()) R.string.action_continue else R.string.action_done))
                    }
                }
            }
        }
    }
}