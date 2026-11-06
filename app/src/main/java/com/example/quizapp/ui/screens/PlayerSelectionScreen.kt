package com.example.quizapp.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.R
import com.example.quizapp.data.entity.Player
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.theme.QuizAppTheme

/**
 * Composable screen for selecting an existing player profile or initiating new player creation.
 *
 * @param onPlayerSelected Callback invoked when a player profile is selected, passing player ID.
 * @param onCreateRequested Callback invoked when new player creation is requested.
 * @param viewModel ViewModel providing the list of available player profiles.
 */
@Composable
fun PlayerScreen(
    onPlayerSelected: (Long) -> Unit,
    onCreateRequested: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val players by viewModel.players.collectAsStateWithLifecycle(initialValue = emptyList())
    PlayerContent(
        players = players,
        onPlayerSelected = onPlayerSelected,
        onCreateRequested = onCreateRequested,
    )
}

/**
 * Stateless UI content for [PlayerScreen].
 */
@Composable
fun PlayerContent(
    players: List<Player>,
    onPlayerSelected: (Long) -> Unit,
    onCreateRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.player_select_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            if (players.isEmpty()) {
                Text(
                    text = stringResource(R.string.player_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(players, key = { it.id }) { player ->
                        Card(
                            onClick = { onPlayerSelected(player.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = player.name,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onCreateRequested,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.player_create))
            }
        }
    }
}

// ===== PREVIEWS & FIXTURES =====

private val samplePlayers = listOf(
    Player(id = 1L, name = "Alice"),
    Player(id = 2L, name = "Bob"),
)

@Preview(name = "Default", showBackground = true)
@Composable
private fun PlayerScreenPreview_Default() {
    QuizAppTheme {
        PlayerContent(
            players = samplePlayers,
            onPlayerSelected = {},
            onCreateRequested = {},
        )
    }
}

@Preview(
    name = "Dark Theme",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PlayerScreenPreview_DarkTheme() {
    QuizAppTheme(darkTheme = true) {
        PlayerContent(
            players = samplePlayers,
            onPlayerSelected = {},
            onCreateRequested = {},
        )
    }
}

@Preview(
    name = "Landscape Mode",
    device = "spec:parent=pixel_5,orientation=landscape",
    showBackground = true,
)
@Composable
private fun PlayerScreenPreview_Landscape() {
    QuizAppTheme {
        PlayerContent(
            players = samplePlayers,
            onPlayerSelected = {},
            onCreateRequested = {},
        )
    }
}
