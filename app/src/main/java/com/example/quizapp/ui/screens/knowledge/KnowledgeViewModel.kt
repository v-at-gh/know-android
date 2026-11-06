package com.example.quizapp.ui.screens.knowledge

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizapp.QuizApplication
import com.example.quizapp.R
import com.example.quizapp.data.knowledge.ArticleMeta
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DomainGroup(
    val domainSlug: String,
    val domainName: String,
    val articles: List<ArticleMeta>,
)

data class CampaignGroup(
    val campaignSlug: String,
    val campaignName: String,
    val campaignDescription: String,
    val domainGroups: List<DomainGroup>,
)

private data class CampaignInfo(
    val slug: String,
    val name: String,
    val description: String,
    val order: Int,
)

/**
 * Represents the UI state for the knowledge catalog screen.
 */
data class KnowledgeCatalogUiState(
    val isLoading: Boolean = true,
    val campaignGroups: List<CampaignGroup> = emptyList(),
    val collapsedCampaignSlugs: Set<String> = emptySet(),
    val collapsedDomainSlugs: Set<String> = emptySet(),
    @StringRes val errorResId: Int? = null,
)

/**
 * ViewModel managing campaign and domain article groupings, collapse state toggling,
 * and knowledge base catalog index loading.
 */
class KnowledgeViewModel(application: Application) : AndroidViewModel(application) {
    val listState: LazyListState = LazyListState()
    private val app = application as QuizApplication
    private val repository = app.knowledgeRepository
    private val domainRepository = app.domainRepository
    private val playerRepository = app.playerRepository

    private val _state = MutableStateFlow(KnowledgeCatalogUiState())
    val state: StateFlow<KnowledgeCatalogUiState> = _state.asStateFlow()

    private var loadedKey: Pair<Long, String>? = null
    private var loadJob: Job? = null

    fun load(playerId: Long, language: String) {
        val key = playerId to language
        if (loadedKey == key) return
        loadedKey = key

        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, errorResId = null) }

        loadJob = viewModelScope.launch {
            try {
                playerRepository.observeById(playerId).collectLatest { player ->
                    if (player == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                campaignGroups = emptyList(),
                                errorResId = R.string.error_profile_load_failed,
                            )
                        }
                        return@collectLatest
                    }

                    val enabledDomains = player.preferredDomainSlugs.toSet()
                    val index = repository.getIndex(language)
                    val domains = domainRepository.getAll(language)
                    val nameBySlug: Map<String, String> = domains.associateBy(
                        keySelector = { it.slug },
                        valueTransform = { it.name },
                    )

                    val campaigns = try {
                        app.contentDatabase.campaignDao().getAll()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val domainIdToSlug = domains.associate { it.id to it.slug }
                    val domainSlugToCampaigns = mutableMapOf<String, MutableList<CampaignInfo>>()

                    for (campaign in campaigns) {
                        val trans = try {
                            app.contentDatabase.campaignDao().getTranslation(campaign.id, language)
                        } catch (e: Exception) {
                            null
                        }
                        val cName = trans?.name ?: campaign.slug
                        val cDesc = trans?.description ?: ""
                        val cInfo = CampaignInfo(campaign.slug, cName, cDesc, campaign.order)

                        val campaignDomains = try {
                            app.contentDatabase.campaignDao().getCampaignDomains(campaign.id)
                        } catch (e: Exception) {
                            emptyList()
                        }
                        val dSlugs = campaignDomains.mapNotNull { domainIdToSlug[it.domainId] } + campaign.prerequisiteDomainSlugs
                        for (dSlug in dSlugs.distinct()) {
                            domainSlugToCampaigns.getOrPut(dSlug) { mutableListOf<CampaignInfo>() }.add(cInfo)
                        }
                    }

                    val defaultCampaign = CampaignInfo(
                        slug = "other",
                        name = "Other Domains",
                        description = "",
                        order = 999,
                    )

                    val domainArticlesMap = index.groupBy { it.domainSlug }
                    val campaignToDomainGroups = mutableMapOf<CampaignInfo, MutableList<DomainGroup>>()

                    for (dSlug in enabledDomains) {
                        val articles = domainArticlesMap[dSlug] ?: emptyList()
                        if (articles.isEmpty()) continue
                        val domainGroup = DomainGroup(
                            domainSlug = dSlug,
                            domainName = nameBySlug[dSlug] ?: dSlug,
                            articles = articles,
                        )
                        val assignedCampaigns = domainSlugToCampaigns[dSlug]
                        if (assignedCampaigns.isNullOrEmpty()) {
                            campaignToDomainGroups.getOrPut(defaultCampaign) { mutableListOf() }.add(domainGroup)
                        } else {
                            val primaryCampaign = assignedCampaigns.minByOrNull { it.order } ?: defaultCampaign
                            campaignToDomainGroups.getOrPut(primaryCampaign) { mutableListOf() }.add(domainGroup)
                        }
                    }

                    val campaignGroups = campaignToDomainGroups
                        .map { (cInfo, dGroups) ->
                            CampaignGroup(
                                campaignSlug = cInfo.slug,
                                campaignName = cInfo.name,
                                campaignDescription = cInfo.description,
                                domainGroups = dGroups.sortedBy { it.domainName },
                            )
                        }
                        .sortedWith(compareBy({ it.campaignSlug == "other" }, { it.campaignName }))

                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            campaignGroups = campaignGroups,
                            errorResId = null,
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.e("KnowledgeViewModel", "Failed to load knowledge index", t)
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        campaignGroups = emptyList(),
                        errorResId = R.string.error_knowledge_load_failed,
                    )
                }
            }
        }
    }

    fun toggleCampaignCollapsed(campaignSlug: String) {
        _state.update { current ->
            val newSet = if (campaignSlug in current.collapsedCampaignSlugs) {
                current.collapsedCampaignSlugs - campaignSlug
            } else {
                current.collapsedCampaignSlugs + campaignSlug
            }
            current.copy(collapsedCampaignSlugs = newSet)
        }
    }

    fun toggleDomainCollapsed(domainSlug: String) {
        _state.update { current ->
            val newSet = if (domainSlug in current.collapsedDomainSlugs) {
                current.collapsedDomainSlugs - domainSlug
            } else {
                current.collapsedDomainSlugs + domainSlug
            }
            current.copy(collapsedDomainSlugs = newSet)
        }
    }
}
