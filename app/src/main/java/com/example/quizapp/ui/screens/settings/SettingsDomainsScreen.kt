package com.example.quizapp.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.R
import com.example.quizapp.data.dao.DomainWithTranslation
import com.example.quizapp.ui.common.AppScreen
import com.example.quizapp.ui.common.DomainOption
import com.example.quizapp.ui.common.languageDisplayName
import com.example.quizapp.ui.screens.DomainGroup
import com.example.quizapp.ui.screens.SettingsUiState
import com.example.quizapp.ui.screens.SettingsViewModel
import com.example.quizapp.ui.theme.QuizAppTheme

/**
 * Composable screen for configuring preferred knowledge domains grouped by campaign.
 *
 * @param playerId The active player ID.
 * @param onBack Callback to navigate back.
 * @param modifier Optional layout modifier.
 * @param viewModel ViewModel backing settings state.
 */
@Composable
fun SettingsDomainsScreen(
    playerId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(playerId) {
        viewModel.load(playerId)
    }
    val scrollState = rememberScrollState()
    AppScreen(
        modifier = modifier,
        onBack = onBack,
        scrollState = scrollState,
    ) {
        SettingsDomainsContent(
            state = state,
            onToggleDomain = viewModel::toggleDomain,
            onToggleCampaign = viewModel::toggleCampaignCollapsed,
            onToggleCampaignSelection = viewModel::toggleCampaignSelection,
            scrollState = scrollState,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Stateless UI content for [SettingsDomainsScreen].
 */
@Composable
fun SettingsDomainsContent(
    state: SettingsUiState,
    onToggleDomain: (String) -> Unit,
    onToggleCampaign: (String) -> Unit,
    onToggleCampaignSelection: (String) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 64.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_domains_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 48.dp),
        )
        Text(
            text = stringResource(R.string.settings_domains_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        state.domainGroups.forEach { group ->
            val collapsed = group.campaignSlug in state.collapsedCampaignSlugs
            val selectedInGroup = state.preferredDomainSlugs.intersect(group.domains.map { it.slug }.toSet()).size
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
                    val isPreferred = domain.slug in state.preferredDomainSlugs
                    if (domain.hasTranslation) {
                        DomainOption(
                            label = domain.name,
                            selected = isPreferred,
                            onToggle = { onToggleDomain(domain.slug) },
                        )
                    } else {
                        UntranslatedDomainRow(domain = domain)
                    }
                }
            }
        }
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
                imageVector = if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
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
private fun UntranslatedDomainRow(
    domain: DomainWithTranslation,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = domain.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (domain.availableLanguages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    domain.availableLanguages.forEach { lang ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = languageDisplayName(lang),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Not translated",
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private val sampleDomainGroups = listOf(
    DomainGroup(
        campaignSlug = "basics",
        campaignName = "Basics",
        campaignDescription = "Foundational domains",
        domains = listOf(
            DomainWithTranslation(1L, "math", "Mathematics", "", true, listOf("en", "fr")),
            DomainWithTranslation(2L, "physics", "Physics", "", false, listOf("en")),
            DomainWithTranslation(3L, "biology", "Biology", "", true, listOf("en", "fr", "ru")),
        ),
    )
)

private val sampleSettingsDomainsState = SettingsUiState(
    availableDomains = listOf(
        DomainWithTranslation(1L, "math", "Mathematics", "", true, listOf("en", "fr")),
        DomainWithTranslation(2L, "physics", "Physics", "", false, listOf("en")),
        DomainWithTranslation(3L, "biology", "Biology", "", true, listOf("en", "fr", "ru")),
    ),
    domainGroups = sampleDomainGroups,
    preferredDomainSlugs = setOf("math", "physics"),
)

@Preview(name = "Default", showBackground = true)
@Composable
private fun SettingsDomainsContentPreview_Default() {
    QuizAppTheme {
        AppScreen(onBack = {}) {
            SettingsDomainsContent(
                state = sampleSettingsDomainsState,
                onToggleDomain = {},
                onToggleCampaign = {},
                onToggleCampaignSelection = {},
                scrollState = rememberScrollState(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(
    name = "Dark Theme",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsDomainsContentPreview_DarkTheme() {
    QuizAppTheme(darkTheme = true) {
        AppScreen(onBack = {}) {
            SettingsDomainsContent(
                state = sampleSettingsDomainsState,
                onToggleDomain = {},
                onToggleCampaign = {},
                onToggleCampaignSelection = {},
                scrollState = rememberScrollState(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
