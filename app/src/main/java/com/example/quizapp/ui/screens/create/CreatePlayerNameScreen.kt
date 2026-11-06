package com.example.quizapp.ui.screens.create

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.R
import com.example.quizapp.ui.theme.QuizAppTheme

/**
 * Composable screen step in player creation for entering a new player's name.
 *
 * @param onNext Callback to proceed to the next step.
 * @param onBack Callback to return to the previous step.
 * @param viewModel ViewModel holding player creation state.
 */
@Composable
fun CreatePlayerNameScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: CreatePlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CreatePlayerNameContent(
        state = state,
        onNameChange = {
            viewModel.setName(it)
            viewModel.clearError()
        },
        onNext = onNext,
        onBack = onBack,
    )
}

@Composable
fun CreatePlayerNameContent(
    state: CreatePlayerUiState,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (isLandscape) {
            CreatePlayerNameLandscape(
                state = state,
                onNameChange = onNameChange,
                onNext = onNext,
                onBack = onBack,
            )
        } else {
            CreatePlayerNamePortrait(
                state = state,
                onNameChange = onNameChange,
                onNext = onNext,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CreatePlayerNamePortrait(
    state: CreatePlayerUiState,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            CreatePlayerNameFields(
                state = state,
                onNameChange = onNameChange,
                onNext = onNext,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CreatePlayerNameLandscape(
    state: CreatePlayerUiState,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top,
        ) {
            CreatePlayerNameFields(
                state = state,
                onNameChange = onNameChange,
                onNext = onNext,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CreatePlayerNameFields(
    state: CreatePlayerUiState,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = stringResource(R.string.create_player_name_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.player_new_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = state.errorResId != null,
    )

    if (state.errorResId != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(state.errorResId),
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
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.action_back))
        }
        Button(
            onClick = onNext,
            enabled = state.name.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.action_next))
        }
    }
}

// ===== PREVIEWS & FIXTURES =====

private val sampleStateEmpty = CreatePlayerUiState(
    name = "",
)

private val sampleStateFilled = CreatePlayerUiState(
    name = "Alex",
)

private val sampleStateError = CreatePlayerUiState(
    name = "",
    errorResId = R.string.error_player_name_empty,
)

@Preview(name = "Empty State", showBackground = true)
@Composable
private fun CreatePlayerNamePreview_Empty() {
    QuizAppTheme {
        CreatePlayerNameContent(
            state = sampleStateEmpty,
            onNameChange = {},
            onNext = {},
            onBack = {},
        )
    }
}

@Preview(name = "Filled State", showBackground = true)
@Composable
private fun CreatePlayerNamePreview_Filled() {
    QuizAppTheme {
        CreatePlayerNameContent(
            state = sampleStateFilled,
            onNameChange = {},
            onNext = {},
            onBack = {},
        )
    }
}

@Preview(name = "Error State", showBackground = true)
@Composable
private fun CreatePlayerNamePreview_Error() {
    QuizAppTheme {
        CreatePlayerNameContent(
            state = sampleStateError,
            onNameChange = {},
            onNext = {},
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
private fun CreatePlayerNamePreview_DarkTheme() {
    QuizAppTheme(darkTheme = true) {
        CreatePlayerNameContent(
            state = sampleStateFilled,
            onNameChange = {},
            onNext = {},
            onBack = {},
        )
    }
}
