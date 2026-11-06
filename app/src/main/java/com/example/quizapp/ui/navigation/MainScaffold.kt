package com.example.quizapp.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.quizapp.R
import com.example.quizapp.ui.common.rememberHapticTap

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.Home -> Icons.Filled.Home
    MainTab.QuizCatalog -> Icons.AutoMirrored.Filled.List
    MainTab.KnowledgeCatalog -> Icons.Filled.Info
    MainTab.Progress -> Icons.Filled.Person
    MainTab.Saved -> Icons.Filled.Favorite
    MainTab.Settings -> Icons.Filled.Settings
}

/**
 * Main application scaffold accommodating portrait bottom navigation bars and landscape dual side-rails.
 */
@Composable
fun MainScaffold(
    currentTab: MainTab,
    isRailOnLeft: Boolean,
    hapticEnabled: Boolean = true,
    hideTabs: Boolean = false,
    portraitTopBar: @Composable () -> Unit = {},
    railExtrasGroup1: @Composable ColumnScope.() -> Unit = {},
    railExtrasGroup2: @Composable ColumnScope.() -> Unit = {},
    onTabSelected: (MainTab) -> Unit,
    onToggleRailSide: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = rememberHapticTap(hapticEnabled)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val group1Tabs = listOf(MainTab.Home, MainTab.QuizCatalog, MainTab.KnowledgeCatalog)
    val group2Tabs = listOf(MainTab.Saved, MainTab.Progress, MainTab.Settings)

    val railContent: @Composable (List<MainTab>, @Composable ColumnScope.() -> Unit) -> Unit = { tabs, extras ->
        NavigationRail(
            header = {
                IconButton(
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        onToggleRailSide()
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_swap_horizontal),
                        contentDescription = stringResource(R.string.swap_positions),
                    )
                }
            }
        ) {
            if (!hideTabs) {
                tabs.forEach { tab ->
                    NavigationRailItem(
                        selected = tab == currentTab,
                        onClick = {
                            haptic(HapticFeedbackType.LongPress)
                            onTabSelected(tab)
                        },
                        icon = { Icon(tab.icon(), contentDescription = stringResource(tab.titleResId)) },
                        label = null,
                    )
                }
            }
            extras()
        }
    }

    if (!isLandscape) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = portraitTopBar,
            bottomBar = {
                if (!hideTabs) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                                        MaterialTheme.colorScheme.background,
                                    )
                                )
                            )
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                        ) {
                            MainTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = tab == currentTab,
                                    onClick = {
                                        haptic(HapticFeedbackType.LongPress)
                                        onTabSelected(tab)
                                    },
                                    icon = { Icon(tab.icon(), contentDescription = null) },
                                    label = { Text(stringResource(tab.titleResId)) },
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                content()
            }
        }
    } else {
        val railWidth = 80.dp
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                Box(
                    modifier = Modifier
                        .width(railWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    railContent(
                        if (isRailOnLeft) group1Tabs else group2Tabs,
                        if (isRailOnLeft) railExtrasGroup1 else railExtrasGroup2,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    content()
                }
                Box(
                    modifier = Modifier
                        .width(railWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    railContent(
                        if (isRailOnLeft) group2Tabs else group1Tabs,
                        if (isRailOnLeft) railExtrasGroup2 else railExtrasGroup1,
                    )
                }
            }
        }
    }
}
