package com.example.quizapp.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.R
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.screens.SettingsViewModel

@Composable
fun SettingsLookAndFeelScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 24.dp), // Space for floating back button
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Look & feel",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 48.dp),
            )

            // Haptics Toggle (moved from SettingsScreen)
            HapticFeedbackRow(
                enabled = state.hapticFeedback,
                onToggle = viewModel::setHapticFeedback,
            )
            
            // Placeholder for future Accent Color selection
            // Text("Accent Color", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HapticFeedbackRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Card(
        onClick = {
            val newState = !enabled
            if (newState) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onToggle(newState)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_haptic_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_haptic_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { newState ->
                    if (newState) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onToggle(newState)
                },
            )
        }
    }
}
