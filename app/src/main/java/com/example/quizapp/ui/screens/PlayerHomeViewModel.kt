package com.example.quizapp.ui.screens

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizapp.QuizApplication
import com.example.quizapp.R
import com.example.quizapp.data.dao.CampaignDao
import com.example.quizapp.data.dao.DomainWithTranslation
import com.example.quizapp.data.qap.QapInstallState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents a group of quiz domains organized under a campaign.
 *
 * @property campaignSlug Unique slug identifying the campaign.
 * @property campaignName Display name of the campaign.
 * @property campaignDescription Description of the campaign.
 * @property domains List of domains belonging to this campaign.
 */
data class DomainGroup(
    val campaignSlug: String,
    val campaignName: String,
    val campaignDescription: String,
    val domains: List<DomainWithTranslation>,
)

/**
 * UI state representing the player home dashboard screen.
 *
 * @property isLoading True if player and domain data are currently loading.
 * @property playerName Name of the active player.
 * @property domains List of active visible domains for the player.
 * @property domainGroups Grouped domains organized by campaigns.
 * @property collapsedCampaignSlugs Set of collapsed campaign slugs.
 * @property selectedDomainIds Set of domain IDs currently selected for quiz sessions.
 * @property unavailableLanguagesByDomain Map of domain names to sets of unavailable content languages.
 * @property playerContentLanguages List of content languages configured for the player.
 * @property errorResId Optional error message string resource ID.
 * @property canPlay Whether at least one domain is selected to start a quiz.
 * @property needsDomainSetup Whether the player needs to configure their initial domains.
 */
data class PlayerHomeUiState(
    val isLoading: Boolean = true,
    val playerName: String = "",
    val domains: List<DomainWithTranslation> = emptyList(),
    val domainGroups: List<DomainGroup> = emptyList(),
    val collapsedCampaignSlugs: Set<String> = emptySet(),
    val selectedDomainIds: Set<Long> = emptySet(),
    val unavailableLanguagesByDomain: Map<String, Set<String>> = emptyMap(),
    val playerContentLanguages: List<String> = emptyList(),
    @StringRes val errorResId: Int? = null,
) {
    val canPlay: Boolean get() = selectedDomainIds.isNotEmpty()
    val needsDomainSetup: Boolean get() = !isLoading && errorResId == null && domains.isEmpty()
}

/**
 * ViewModel managing player home dashboard state, domain selections, campaign groupings, and content availability.
 */
class PlayerHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as QuizApplication
    private val playerRepository = app.playerRepository
    private val domainRepository = app.domainRepository
    private val _state = MutableStateFlow(PlayerHomeUiState())
    val state: StateFlow<PlayerHomeUiState> = _state.asStateFlow()
    private var loadedPlayerId: Long? = null
    private var loadedFor: Pair<Long, String>? = null
    private var loadJob: Job? = null
    private var unavailableLanguagesJob: Job? = null

    init {
        app.qapInstaller.state
            .filter { it is QapInstallState.Done || it is QapInstallState.Uninstalled }
            .onEach {
                loadedFor?.let { (pid, lang) ->
                    loadedFor = null
                    load(pid, lang)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Loads player data, domains, and campaign groupings for the specified player and language.
     *
     * @param playerId Unique identifier of the player.
     * @param language Content language code.
     */
    fun load(playerId: Long, language: String) {
        val key = playerId to language
        if (loadedFor == key) return
        loadedFor = key
        loadedPlayerId = playerId
        loadJob?.cancel()
        _state.value = PlayerHomeUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                playerRepository.observeById(playerId).collect { player ->
                    val allDomains = domainRepository.getAll(language)
                    if (player == null) {
                        _state.value = PlayerHomeUiState(
                            isLoading = false,
                            errorResId = R.string.error_profile_load_failed,
                        )
                        return@collect
                    }
                    val preferred = player.preferredDomainSlugs.toSet()
                    val validPreferred = preferred.filter { slug ->
                        allDomains.find { it.slug == slug }?.hasTranslation == true
                    }.toSet()

                    Log.d("QuizDomainDebug", "HomeVM.collect lang=$language " +
                        "allDomainsCount=${allDomains.size} " +
                        "preferred=${preferred.sorted()} valid=${validPreferred.sorted()} " +
                        "diff=${(preferred - validPreferred).sorted()}")

                    val needsSetup = validPreferred.isEmpty()
                    val visibleDomains = if (needsSetup) {
                        emptyList()
                    } else {
                        allDomains.filter { it.slug in validPreferred }
                    }
                    val selectedSlugs = player.selectedDomainSlugs?.toSet()?.intersect(validPreferred)
                        ?: validPreferred
                    val selectedIds = visibleDomains
                        .filter { it.slug in selectedSlugs }
                        .map { it.id }
                        .toSet()

                    val campaignDao = try {
                        app.contentDatabase.campaignDao()
                    } catch (t: Throwable) {
                        null
                    }

                    val domainGroups = buildDomainGroups(
                        domains = visibleDomains,
                        allDomains = allDomains,
                        language = language,
                        campaignDao = campaignDao,
                    )

                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            playerName = player.name,
                            domains = visibleDomains,
                            domainGroups = domainGroups,
                            selectedDomainIds = selectedIds,
                            playerContentLanguages = player.contentLanguages,
                        )
                    }
                    updateUnavailableLanguages(visibleDomains, selectedIds, player.contentLanguages)
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.e("PlayerHomeViewModel", "Failed to load home", t)
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorResId = R.string.error_profile_load_failed,
                    )
                }
            }
        }
    }

    private fun updateUnavailableLanguages(
        domains: List<DomainWithTranslation>,
        selectedIds: Set<Long>,
        playerLangs: List<String>,
    ) {
        unavailableLanguagesJob?.cancel()
        unavailableLanguagesJob = viewModelScope.launch {
            val map = mutableMapOf<String, Set<String>>()
            for (domain in domains) {
                if (domain.id in selectedIds) {
                    val withContent = app.questionRepository.getLanguagesWithContentForDomains(playerLangs, setOf(domain.id))
                    val unavailable = playerLangs.toSet() - withContent
                    if (unavailable.isNotEmpty()) {
                        map[domain.name] = unavailable
                    }
                }
            }
            _state.update { it.copy(unavailableLanguagesByDomain = map) }
        }
    }

    private suspend fun buildDomainGroups(
        domains: List<DomainWithTranslation>,
        allDomains: List<DomainWithTranslation>,
        language: String,
        campaignDao: CampaignDao?,
    ): List<DomainGroup> {
        if (domains.isEmpty()) return emptyList()

        val domainIdToSlug = allDomains.associate { it.id to it.slug }
        val slugToVisibleDomain = domains.associateBy { it.slug }
        val campaigns = try {
            campaignDao?.getAll().orEmpty()
        } catch (t: Throwable) {
            emptyList()
        }

        val assignedSlugs = mutableSetOf<String>()
        val groups = mutableListOf<DomainGroup>()

        for (campaign in campaigns.sortedBy { it.order }) {
            val translations = try {
                campaignDao?.getTranslation(campaign.id, language)
            } catch (t: Throwable) {
                null
            }
            val name = translations?.name ?: campaign.slug
            val description = translations?.description ?: ""

            val campaignDomains = try {
                campaignDao?.getCampaignDomains(campaign.id).orEmpty()
            } catch (t: Throwable) {
                emptyList()
            }

            val dSlugs = (campaignDomains.mapNotNull { domainIdToSlug[it.domainId] } + campaign.prerequisiteDomainSlugs).distinct()
            val matchedDomains = dSlugs
                .mapNotNull { slugToVisibleDomain[it] }
                .filter { it.slug !in assignedSlugs }

            Log.d("QuizDomainDebug", "HomeVM.group campaign=${campaign.slug} " +
                "campaignDomains=${campaignDomains.size} " +
                "dSlugs=$dSlugs matched=${matchedDomains.map { it.slug }}")

            if (matchedDomains.isEmpty()) continue

            matchedDomains.forEach { assignedSlugs += it.slug }
            groups += DomainGroup(
                campaignSlug = campaign.slug,
                campaignName = name,
                campaignDescription = description,
                domains = matchedDomains.sortedBy { it.name },
            )
        }

        val unassigned = domains
            .filter { it.slug !in assignedSlugs }
            .sortedBy { it.name }

        if (unassigned.isNotEmpty()) {
            groups += DomainGroup(
                campaignSlug = "other",
                campaignName = "Other Domains",
                campaignDescription = "",
                domains = unassigned,
            )
        }

        Log.d("QuizDomainDebug", "HomeVM.groups campaigns=${campaigns.map { it.slug }} " +
            "groups=${groups.map { it.campaignSlug to it.domains.map { d -> d.slug } }}")

        return groups
    }

    /** Toggles the collapsed state of a campaign group. */
    fun toggleCampaignCollapsed(slug: String) {
        _state.update { current ->
            val next = if (slug in current.collapsedCampaignSlugs) {
                current.collapsedCampaignSlugs - slug
            } else {
                current.collapsedCampaignSlugs + slug
            }
            current.copy(collapsedCampaignSlugs = next)
        }
    }

    /** Toggles selection state of all domains within a campaign group. */
    fun toggleCampaignSelection(campaignSlug: String) {
        val currentState = _state.value
        val group = currentState.domainGroups.find { it.campaignSlug == campaignSlug } ?: return
        val groupIds = group.domains.map { it.id }.toSet()
        if (groupIds.isEmpty()) return

        val selectedInGroup = currentState.selectedDomainIds.intersect(groupIds)
        val newSelectedIds = if (selectedInGroup.size == groupIds.size) {
            // all selected → clear all in this group
            currentState.selectedDomainIds - groupIds
        } else {
            // none or partial → select all in this group
            currentState.selectedDomainIds + groupIds
        }

        val newSelectedSlugs = currentState.domains
            .filter { it.id in newSelectedIds }
            .map { it.slug }

        _state.update { it.copy(selectedDomainIds = newSelectedIds) }
        updateUnavailableLanguages(
            currentState.domains,
            newSelectedIds,
            currentState.playerContentLanguages,
        )
        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updateSelectedDomains(pid, newSelectedSlugs)
        }
    }

    /** Toggles selection state of an individual domain. */
    fun toggleDomain(domainId: Long) {
        val currentState = _state.value
        if (currentState.domains.none { it.id == domainId }) return
        val currentSelectedIds = currentState.selectedDomainIds
        val newSelectedIds = if (domainId in currentSelectedIds) {
            currentSelectedIds - domainId
        } else {
            currentSelectedIds + domainId
        }
        val newSelectedSlugs = currentState.domains
            .filter { it.id in newSelectedIds }
            .map { it.slug }
        _state.update { it.copy(selectedDomainIds = newSelectedIds) }
        updateUnavailableLanguages(currentState.domains, newSelectedIds, currentState.playerContentLanguages)
        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updateSelectedDomains(pid, newSelectedSlugs)
        }
    }

    /** Selects all available domains. */
    fun selectAllDomains() {
        val currentState = _state.value
        val allIds = currentState.domains.map { d -> d.id }.toSet()
        val allSlugs = currentState.domains.map { d -> d.slug }
        _state.update { it.copy(selectedDomainIds = allIds) }
        updateUnavailableLanguages(currentState.domains, allIds, currentState.playerContentLanguages)
        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updateSelectedDomains(pid, allSlugs)
        }
    }

    /** Clears all selected domains. */
    fun clearAllDomains() {
        unavailableLanguagesJob?.cancel()
        val currentState = _state.value
        _state.update { it.copy(selectedDomainIds = emptySet(), unavailableLanguagesByDomain = emptyMap()) }
        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updateSelectedDomains(pid, emptyList())
        }
    }
}