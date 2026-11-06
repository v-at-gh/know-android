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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.QuizApplication
import com.example.quizapp.QuizConfig
import com.example.quizapp.R
import com.example.quizapp.data.dao.DomainWithTranslation
import com.example.quizapp.data.importer.Progress
import com.example.quizapp.ui.common.DomainOption
import com.example.quizapp.ui.theme.QuizAppTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Composable screen for the player home view.
 *
 * Displays player greetings, domain selections grouped by campaigns, quick quiz configuration dialogs,
 * and primary actions to start a quiz or manage player profiles.
 *
 * @param playerId The ID of the active player.
 * @param onStartQuiz Callback invoked to start a quiz with a [QuizConfig].
 * @param onOpenProfile Callback invoked to open the profile screen.
 * @param onOpenDomainsSettings Callback invoked to open domain settings.
 * @param viewModel ViewModel backing the player home screen.
 */
@Composable
fun PlayerHomeScreen(
    playerId: Long,
    onStartQuiz: (QuizConfig) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDomainsSettings: () -> Unit,
    viewModel: PlayerHomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as? QuizApplication
    val contentLang by app?.preferences?.contentLanguage?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("en") }
    LaunchedEffect(playerId, contentLang) {
        viewModel.load(playerId, contentLang)
    }
    PlayerHomeContent(
        state = state,
        contentLang = contentLang,
        onToggleDomain = viewModel::toggleDomain,
        onToggleCampaign = viewModel::toggleCampaignCollapsed,
        onToggleCampaignSelection = viewModel::toggleCampaignSelection,
        onSelectAllDomains = viewModel::selectAllDomains,
        onClearAllDomains = viewModel::clearAllDomains,
        onStartQuiz = onStartQuiz,
        onOpenProfile = onOpenProfile,
        onOpenDomainsSettings = onOpenDomainsSettings,
    )
}

/**
 * Stateless content layout for [PlayerHomeScreen].
 */
@Composable
fun PlayerHomeContent(
    state: PlayerHomeUiState,
    contentLang: String,
    onToggleDomain: (Long) -> Unit,
    onToggleCampaign: (String) -> Unit,
    onToggleCampaignSelection: (String) -> Unit,
    onSelectAllDomains: () -> Unit,
    onClearAllDomains: () -> Unit,
    onStartQuiz: (QuizConfig) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDomainsSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.errorResId != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(state.errorResId),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> HomeContent(
                state = state,
                contentLang = contentLang,
                onToggleDomain = onToggleDomain,
                onToggleCampaign = onToggleCampaign,
                onToggleCampaignSelection = onToggleCampaignSelection,
                onSelectAllDomains = onSelectAllDomains,
                onClearAllDomains = onClearAllDomains,
                onStartQuiz = onStartQuiz,
                onOpenProfile = onOpenProfile,
                onOpenDomainsSettings = onOpenDomainsSettings,
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: PlayerHomeUiState,
    contentLang: String,
    onToggleDomain: (Long) -> Unit,
    onToggleCampaign: (String) -> Unit,
    onToggleCampaignSelection: (String) -> Unit,
    onSelectAllDomains: () -> Unit,
    onClearAllDomains: () -> Unit,
    onStartQuiz: (QuizConfig) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDomainsSettings: () -> Unit,
) {
    var showQuickQuizDialog by rememberSaveable { mutableStateOf(false) }

    if (showQuickQuizDialog) {
        QuickQuizDialog(
            onDismiss = { showQuickQuizDialog = false },
            onStart = { config ->
                showQuickQuizDialog = false
                onStartQuiz(config.copy(domainIds = state.selectedDomainIds))
            },
        )
    }

    val app = LocalContext.current.applicationContext as? QuizApplication
    val dummyIdle = remember { MutableStateFlow(true) }
    val dummyProgress = remember { MutableStateFlow<Progress?>(null) }
    val isIdle by (app?.contentBootstrapper?.isIdle ?: dummyIdle).collectAsStateWithLifecycle()
    val progress by (app?.contentBootstrapper?.importProgress ?: dummyProgress).collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        if (!isIdle && progress != null && progress!!.domainsTotal > 0 && progress!!.domainsDone < progress!!.domainsTotal) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Content loading: ${progress!!.domainsDone} / ${progress!!.domainsTotal} domains",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.player_home_greeting, state.playerName),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onOpenProfile,
            ) {
                Text(stringResource(R.string.player_home_profile))
            }
        }
        if (state.needsDomainSetup) {
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.player_home_no_domains_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.player_home_no_domains_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenDomainsSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.player_home_select_domains))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.player_home_domain_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSelectAllDomains) {
                        Text(stringResource(R.string.domain_select_all))
                    }
                    TextButton(onClick = onClearAllDomains) {
                        Text(stringResource(R.string.domain_clear_all))
                    }
                }
                state.domainGroups.forEach { group ->
                    val collapsed = group.campaignSlug in state.collapsedCampaignSlugs
                    val groupIds = group.domains.map { it.id }.toSet()
                    val selectedInGroup = state.selectedDomainIds.intersect(groupIds).size
                    CampaignHeader(
                        name = group.campaignName,
                        description = group.campaignDescription,
                        domainCount = group.domains.size,
                        selectedCount = selectedInGroup,
                        collapsed = collapsed,
                        onToggleCollapse = { onToggleCampaign(group.campaignSlug) },
                        onToggleAll = { onToggleCampaignSelection(group.campaignSlug) },
                    )
                    if (!collapsed) {
                        group.domains.forEach { domain ->
                            DomainOption(
                                label = domain.name,
                                selected = domain.id in state.selectedDomainIds,
                                onToggle = { onToggleDomain(domain.id) },
                                enabled = domain.hasTranslation,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                val playerContentLanguages = state.playerContentLanguages
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.unavailableLanguagesByDomain.forEach { (domainName, unavailableLangs) ->
                        if (contentLang in unavailableLangs) {
                            val availableLangs = (playerContentLanguages.toSet() - unavailableLangs).sorted()
                            val availableStr = if (availableLangs.isNotEmpty()) availableLangs.joinToString(", ") else "none"
                            val unavailableStr = unavailableLangs.sorted().joinToString(", ")
                            Text(
                                text = "Domain $domainName: quiz available in $availableStr · not available in $unavailableStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (!state.canPlay) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.player_home_select_at_least_one),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        }

        // Quick Quiz FAB (Left)
        ExtendedFloatingActionButton(
            onClick = { if (state.canPlay) showQuickQuizDialog = true },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            text = { Text(stringResource(R.string.player_home_quick_quiz)) },
            expanded = true,
            containerColor = if (state.canPlay) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (state.canPlay) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp)
        )

        // Play FAB (Right)
        ExtendedFloatingActionButton(
            onClick = { if (state.canPlay) onStartQuiz(QuizConfig(domainIds = state.selectedDomainIds)) },
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            text = { Text(stringResource(R.string.player_home_play)) },
            expanded = true,
            containerColor = if (state.canPlay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (state.canPlay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun CampaignHeader(
    name: String,
    description: String,
    domainCount: Int,
    selectedCount: Int,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val triState = when {
        selectedCount <= 0 -> ToggleableState.Off
        selectedCount >= domainCount -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Surface(
        onClick = onToggleCollapse,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$selectedCount / $domainCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            TriStateCheckbox(
                state = triState,
                onClick = onToggleAll,
            )
            Icon(
                imageVector = if (collapsed) Icons.Default.KeyboardArrowDown
                    else Icons.Default.KeyboardArrowUp,
                contentDescription = if (collapsed) {
                    stringResource(R.string.cd_expand_group, name)
                } else {
                    stringResource(R.string.cd_collapse_group, name)
                },
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun QuickQuizDialog(
    onDismiss: () -> Unit,
    onStart: (QuizConfig) -> Unit,
) {
    var selectedDifficulty by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedCount by rememberSaveable { mutableStateOf<Int?>(10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_quiz_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.quick_quiz_difficulty_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = selectedDifficulty == null,
                            onClick = { selectedDifficulty = null },
                            label = { Text(stringResource(R.string.quick_quiz_difficulty_any)) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = selectedDifficulty == 1,
                            onClick = { selectedDifficulty = 1 },
                            label = { Text(stringResource(R.string.quick_quiz_difficulty_easy)) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = selectedDifficulty == 2,
                            onClick = { selectedDifficulty = 2 },
                            label = { Text(stringResource(R.string.quick_quiz_difficulty_medium)) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = selectedDifficulty == 3,
                            onClick = { selectedDifficulty = 3 },
                            label = { Text(stringResource(R.string.quick_quiz_difficulty_hard)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.quick_quiz_count_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(5, 10, 20, null).forEach { count ->
                            val label = if (count == null) stringResource(R.string.quick_quiz_count_all) else count.toString()
                            FilterChip(
                                selected = selectedCount == count,
                                onClick = { selectedCount = count },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStart(
                        QuizConfig(
                            domainIds = emptySet(),
                            difficulties = selectedDifficulty?.let { setOf(it) },
                            questionLimit = selectedCount,
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.quick_quiz_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// ===== PREVIEWS & FIXTURES =====

private val sampleDomains = listOf(
    DomainWithTranslation(
        id = 1L,
        slug = "math",
        name = "Mathematics",
        description = "Math questions"
    ),
    DomainWithTranslation(
        id = 2L,
        slug = "physics",
        name = "Physics",
        description = "Physics questions"
    ),
    DomainWithTranslation(
        id = 3L,
        slug = "biology",
        name = "Biology",
        description = "Biology questions"
    ),
    DomainWithTranslation(
        id = 4L,
        slug = "chemistry",
        name = "Chemistry",
        description = "Chemistry questions"
    ),
)

private val sampleDomainGroups = listOf(
    DomainGroup(
        campaignSlug = "basics",
        campaignName = "Basics",
        campaignDescription = "Foundational technical domains",
        domains = sampleDomains,
    )
)

private val sampleStateDefault = PlayerHomeUiState(
    isLoading = false,
    playerName = "Alice",
    domains = sampleDomains,
    domainGroups = sampleDomainGroups,
    selectedDomainIds = setOf(1L, 2L),
)

private val sampleStateNoDomains = PlayerHomeUiState(
    isLoading = false,
    playerName = "Alice",
    domains = sampleDomains,
    domainGroups = sampleDomainGroups,
    selectedDomainIds = emptySet(),
)

private val sampleStateLoading = PlayerHomeUiState(
    isLoading = true,
)

private val sampleStateError = PlayerHomeUiState(
    isLoading = false,
    errorResId = R.string.error_profile_load_failed,
)

@Preview(name = "Default", showBackground = true)
@Composable
private fun PlayerHomeScreenPreview_Default() {
    QuizAppTheme {
        PlayerHomeContent(
            state = sampleStateDefault,
            contentLang = "en",
            onToggleDomain = {},
            onToggleCampaign = {},
            onToggleCampaignSelection = {},
            onSelectAllDomains = {},
            onClearAllDomains = {},
            onStartQuiz = {},
            onOpenProfile = {},
            onOpenDomainsSettings = {},
        )
    }
}

@Preview(name = "Collapsed Campaign", showBackground = true)
@Composable
private fun PlayerHomeScreenPreview_CollapsedCampaign() {
    QuizAppTheme {
        PlayerHomeContent(
            state = sampleStateDefault.copy(collapsedCampaignSlugs = setOf("basics")),
            contentLang = "en",
            onToggleDomain = {},
            onToggleCampaign = {},
            onToggleCampaignSelection = {},
            onSelectAllDomains = {},
            onClearAllDomains = {},
            onStartQuiz = {},
            onOpenProfile = {},
            onOpenDomainsSettings = {},
        )
    }
}

@Preview(
    name = "Dark Theme",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PlayerHomeScreenPreview_DarkTheme() {
    QuizAppTheme(darkTheme = true) {
        PlayerHomeContent(
            state = sampleStateDefault,
            contentLang = "en",
            onToggleDomain = {},
            onToggleCampaign = {},
            onToggleCampaignSelection = {},
            onSelectAllDomains = {},
            onClearAllDomains = {},
            onStartQuiz = {},
            onOpenProfile = {},
            onOpenDomainsSettings = {},
        )
    }
}

@Preview(name = "No Domains Setup", showBackground = true)
@Composable
private fun PlayerHomeScreenPreview_NoDomains() {
    QuizAppTheme {
        PlayerHomeContent(
            state = sampleStateNoDomains,
            contentLang = "en",
            onToggleDomain = {},
            onToggleCampaign = {},
            onToggleCampaignSelection = {},
            onSelectAllDomains = {},
            onClearAllDomains = {},
            onStartQuiz = {},
            onOpenProfile = {},
            onOpenDomainsSettings = {},
        )
    }
}

@Preview(
    name = "Landscape Mode",
    device = "spec:parent=pixel_5,orientation=landscape",
    showBackground = true,
)
@Composable
private fun PlayerHomeScreenPreview_Landscape() {
    QuizAppTheme {
        PlayerHomeContent(
            state = sampleStateDefault,
            contentLang = "en",
            onToggleDomain = {},
            onToggleCampaign = {},
            onToggleCampaignSelection = {},
            onSelectAllDomains = {},
            onClearAllDomains = {},
            onStartQuiz = {},
            onOpenProfile = {},
            onOpenDomainsSettings = {},
        )
    }
}

@Preview(name = "Loading State", showBackground = true)
@Composable
private fun PlayerHomeScreenPreview_Loading() {
    QuizAppTheme {
        PlayerHomeContent(
            state = sampleStateLoading,
            contentLang = "en",
            onToggleDomain = {},
            onToggleCampaign = {},
            onToggleCampaignSelection = {},
            onSelectAllDomains = {},
            onClearAllDomains = {},
            onStartQuiz = {},
            onOpenProfile = {},
            onOpenDomainsSettings = {},
        )
    }
}

@Preview(name = "Error State", showBackground = true)
@Composable
private fun PlayerHomeScreenPreview_Error() {
    QuizAppTheme {
        PlayerHomeContent(
            state = sampleStateError,
            contentLang = "en",
            onToggleDomain = {},
            onToggleCampaign = {},
            onToggleCampaignSelection = {},
            onSelectAllDomains = {},
            onClearAllDomains = {},
            onStartQuiz = {},
            onOpenProfile = {},
            onOpenDomainsSettings = {},
        )
    }
}
