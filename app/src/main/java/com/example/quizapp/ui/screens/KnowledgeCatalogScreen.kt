package com.example.quizapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.QuizApplication
import com.example.quizapp.R
import com.example.quizapp.data.knowledge.ArticleMeta
import com.example.quizapp.ui.screens.knowledge.CampaignGroup
import com.example.quizapp.ui.screens.knowledge.DomainGroup
import com.example.quizapp.ui.screens.knowledge.KnowledgeViewModel

/**
 * Composable screen displaying a catalog of knowledge base articles grouped by campaigns and domains.
 *
 * @param playerId The ID of the active player.
 * @param onOpenArticle Callback invoked when an article is selected, passing the article slug.
 * @param viewModel ViewModel backing the knowledge catalog screen.
 */
@Composable
fun KnowledgeCatalogScreen(
    playerId: Long,
    onOpenArticle: (String) -> Unit,
    viewModel: KnowledgeViewModel = viewModel(),
) {
    val app = LocalContext.current.applicationContext as QuizApplication
    val contentLang by app.preferences.contentLanguage.collectAsStateWithLifecycle()
    LaunchedEffect(playerId, contentLang) {
        viewModel.load(playerId, contentLang)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.isLoading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        state.errorResId != null -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(state.errorResId!!),
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.campaignGroups.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.knowledge_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> LazyColumn(
            state = viewModel.listState,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.campaignGroups.forEach { campaignGroup ->
                val isCampaignCollapsed = campaignGroup.campaignSlug in state.collapsedCampaignSlugs
                item(key = "campaign-${campaignGroup.campaignSlug}") {
                    CampaignGroupHeader(
                        group = campaignGroup,
                        isCollapsed = isCampaignCollapsed,
                        onToggleCollapse = { viewModel.toggleCampaignCollapsed(campaignGroup.campaignSlug) },
                    )
                }
                if (!isCampaignCollapsed) {
                    campaignGroup.domainGroups.forEach { domainGroup ->
                        val isDomainCollapsed = domainGroup.domainSlug in state.collapsedDomainSlugs
                        item(key = "domain-${campaignGroup.campaignSlug}-${domainGroup.domainSlug}") {
                            DomainGroupHeader(
                                group = domainGroup,
                                isCollapsed = isDomainCollapsed,
                                onToggleCollapse = { viewModel.toggleDomainCollapsed(domainGroup.domainSlug) },
                            )
                        }
                        if (!isDomainCollapsed) {
                            items(domainGroup.articles, key = { "article-${it.slug}" }) { meta ->
                                ArticleCard(meta = meta) { onOpenArticle(meta.slug) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampaignGroupHeader(
    group: CampaignGroup,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggleCollapse,
        modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.campaignName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (group.campaignDescription.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = group.campaignDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                val totalArticles = group.domainGroups.sumOf { it.articles.size }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.knowledge_article_count,
                        totalArticles,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (isCollapsed) {
                    stringResource(R.string.cd_expand_group, group.campaignName)
                } else {
                    stringResource(R.string.cd_collapse_group, group.campaignName)
                },
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun DomainGroupHeader(
    group: DomainGroup,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggleCollapse,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.domainName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.knowledge_article_count,
                        group.articles.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (isCollapsed) {
                    stringResource(R.string.cd_expand_group, group.domainName)
                } else {
                    stringResource(R.string.cd_collapse_group, group.domainName)
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleCard(
    meta: ArticleMeta,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(meta.title, style = MaterialTheme.typography.bodyLarge)
            if (meta.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meta.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
