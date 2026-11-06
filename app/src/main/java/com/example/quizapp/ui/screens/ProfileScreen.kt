package com.example.quizapp.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.R
import com.example.quizapp.data.DomainStats
import com.example.quizapp.data.PlayerStats
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.theme.QuizAppTheme

/**
 * Composable screen displaying a player's profile, including total quiz stats and domain breakdown.
 *
 * @param playerId The ID of the target player profile.
 * @param onBack Callback invoked to navigate back.
 * @param viewModel ViewModel providing profile state and statistics.
 * @param modifier Optional modifier for the layout.
 */
@Composable
fun ProfileScreen(
    playerId: Long,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppScreen(modifier = modifier) {
        LaunchedEffect(playerId) {
            viewModel.load(playerId)
        }
        ProfileContentState(
            state = state,
            onBack = onBack,
        )
    }
}

/**
 * Renders the state-dependent UI content for [ProfileScreen].
 */
@Composable
fun ProfileContentState(
    state: ProfileUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            ProfileUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is ProfileUiState.Error -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.profile_back)) }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.error_generic, stringResource(state.errorResId)),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is ProfileUiState.Ready -> ReadyContent(state = state, onBack = onBack)
        }
    }
}

@Composable
private fun ReadyContent(state: ProfileUiState.Ready, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = state.playerName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.profile_back))
            }
        }
        Spacer(Modifier.height(16.dp))
        ProfileContent(state)
    }
}

@Composable
private fun ProfileContent(state: ProfileUiState.Ready) {
    val stats = state.stats
    val percent = if (stats.total > 0) stats.correct * 100 / stats.total else 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.profile_summary_title),
                    style = MaterialTheme.typography.titleMedium
                )
                StatRow(stringResource(R.string.profile_total_answers), stats.total.toString())
                StatRow(stringResource(R.string.profile_correct_answers), stats.correct.toString())
                StatRow(stringResource(R.string.profile_accuracy), "$percent%")
                StatRow(stringResource(R.string.profile_hints_used), stats.hintsUsed.toString())
            }
        }
        if (state.byDomain.isNotEmpty()) {
            Text(
                stringResource(R.string.profile_by_domain),
                style = MaterialTheme.typography.titleMedium
            )
            state.byDomain.forEach { ds ->
                val dp = if (ds.total > 0) ds.correct * 100 / ds.total else 0
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(ds.domainName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                R.string.profile_domain_ratio, ds.correct,
                                ds.total, dp
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (stats.total == 0) {
            Text(
                text = stringResource(R.string.profile_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// ===== PREVIEWS & FIXTURES =====

private val sampleProfileState = ProfileUiState.Ready(
    playerName = "Alice",
    stats = PlayerStats(total = 20, correct = 16, hintsUsed = 3),
    byDomain = listOf(
        DomainStats(domainSlug = "math", domainName = "Mathematics", total = 10, correct = 8),
        DomainStats(domainSlug = "physics", domainName = "Physics", total = 10, correct = 8),
    ),
)

@Preview(name = "Default", showBackground = true)
@Composable
private fun ProfileScreenPreview_Default() {
    QuizAppTheme {
        ProfileContentState(
            state = sampleProfileState,
            onBack = {},
        )
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun ProfileScreenPreview_Loading() {
    QuizAppTheme {
        ProfileContentState(
            state = ProfileUiState.Loading,
            onBack = {},
        )
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun ProfileScreenPreview_Error() {
    QuizAppTheme {
        ProfileContentState(
            state = ProfileUiState.Error(R.string.error_profile_load_failed),
            onBack = {},
        )
    }
}

@Preview(
    name = "Dark Theme",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileScreenPreview_DarkTheme() {
    QuizAppTheme(darkTheme = true) {
        ProfileContentState(
            state = sampleProfileState,
            onBack = {},
        )
    }
}

@Preview(
    name = "Landscape Mode",
    device = "spec:parent=pixel_5,orientation=landscape",
    showBackground = true,
)
@Composable
private fun ProfileScreenPreview_Landscape() {
    QuizAppTheme {
        ProfileContentState(
            state = sampleProfileState,
            onBack = {},
        )
    }
}
