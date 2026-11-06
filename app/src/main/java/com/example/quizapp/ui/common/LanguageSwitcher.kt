package com.example.quizapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.quizapp.R

/**
 * Composable dropdown button allowing quick switching between available content languages.
 */
@Composable
fun LanguageSwitcher(
    availableLanguages: List<String>,
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    unavailableLanguages: Set<String> = emptySet(),
    onUnavailableTapped: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticEnabled: Boolean = true,
    trailingIcon: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticTap(hapticEnabled)
    Box(modifier = modifier) {
        TextButton(
            onClick = {
                haptic(HapticFeedbackType.TextHandleMove)
                expanded = true
            },
            enabled = enabled && availableLanguages.size > 1,
        ) {
            if (!trailingIcon) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_language_globe),
                    contentDescription = stringResource(R.string.cd_language_switcher),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(currentLanguage.uppercase())
            if (trailingIcon) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_language_globe),
                    contentDescription = stringResource(R.string.cd_language_switcher),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sortLanguages(availableLanguages).forEach { lang ->
                val isSelected = lang == currentLanguage
                val isUnavailable = lang in unavailableLanguages
                val textColor = if (isUnavailable) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                } else if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = languageDisplayName(lang),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                            )
                            Spacer(Modifier.width(16.dp))
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                enabled = !isUnavailable,
                            )
                        }
                    },
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        expanded = false
                        if (isUnavailable) {
                            onUnavailableTapped(lang)
                        } else if (lang != currentLanguage) {
                            onLanguageSelected(lang)
                        }
                    },
                    enabled = true,
                )
            }
        }
    }
}
