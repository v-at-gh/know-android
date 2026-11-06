package com.example.quizapp.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizapp.QuizApplication
import com.example.quizapp.data.dao.CampaignDao
import com.example.quizapp.data.dao.DomainWithTranslation
import com.example.quizapp.data.entity.Player
import com.example.quizapp.data.net.PingClient
import com.example.quizapp.data.net.PingResult
import com.example.quizapp.data.prefs.InstalledPack
import com.example.quizapp.data.prefs.PreferencesRepository
import com.example.quizapp.data.qap.QapInstallState
import com.example.quizapp.data.qap.QapPackEntry
import com.example.quizapp.ui.common.PackRowState
import com.example.quizapp.ui.common.buildPackRows
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

sealed interface PingUiState {
    data object Idle : PingUiState
    data object InFlight : PingUiState
    data class Success(val rttMs: Long) : PingUiState
    data class Timeout(val ms: Long) : PingUiState
    data class Failure(val reason: String) : PingUiState
}

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data object InProgress : SyncUiState
    data class Success(val count: Int) : SyncUiState
    data class Error(val message: String) : SyncUiState
}

/**
 * Represents UI state for the settings screen.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val availableLanguages: List<String> = emptyList(),
    val importedLanguages: Set<String> = emptySet(),
    val currentLanguage: String = PreferencesRepository.DEFAULT_LANGUAGE,
    val hapticFeedback: Boolean = true,
    val primaryLanguage: String = PreferencesRepository.DEFAULT_LANGUAGE,
    val contentLanguages: Set<String> = setOf(PreferencesRepository.DEFAULT_LANGUAGE),
    val availableDomains: List<DomainWithTranslation> = emptyList(),
    val domainGroups: List<DomainGroup> = emptyList(),
    val collapsedCampaignSlugs: Set<String> = emptySet(),
    val preferredDomainSlugs: Set<String> = emptySet(),
    val developerMode: Boolean = false,
    val devServer: String = "",
    val pingState: PingUiState = PingUiState.Idle,
    val syncState: SyncUiState = SyncUiState.Idle,
    val localAddresses: List<String> = emptyList(),
    val incomingEvents: List<String> = emptyList(),
    val packCatalog: List<PackRowState> = emptyList(),
    val packCatalogError: String? = null,
    val packCatalogLoading: Boolean = false,
    val installState: QapInstallState = QapInstallState.Idle,
    val installedPacks: Map<String, InstalledPack> = emptyMap(),
    val selectedPackForDetails: PackRowState? = null,
)

/**
 * ViewModel managing settings screen state, language preferences, domain selections,
 * developer mode unlock counters, and local server ping diagnostics.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuizApplication
    private val playerRepository = app.playerRepository
    private val preferences = app.preferences
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val domainRepository = app.domainRepository
    private var loadJob: Job? = null
    private var loadedPlayerId: Long? = null
    private var rawPackCatalog: List<QapPackEntry> = emptyList()

    private var versionTapCount = 0
    private var lastVersionTapAt: Long = 0L

    init {
        app.qapInstaller.state
            .onEach { s -> _state.update { it.copy(installState = s) } }
            .launchIn(viewModelScope)

        app.qapInstaller.state
            .filter { it is QapInstallState.Done || it is QapInstallState.Uninstalled }
            .onEach {
                loadedPlayerId?.let { pid ->
                    loadedPlayerId = null // force reload
                    load(pid)
                }
            }
            .launchIn(viewModelScope)

        app.importedLanguages
            .onEach { imported ->
                _state.update { s ->
                    val sortedAvailable = s.availableLanguages.sortedWith(
                        compareByDescending<String> { it == "en" || it in imported }
                            .thenBy { it != "en" && it != com.example.quizapp.ui.common.Localizations.systemLanguageCode() }
                            .thenBy { com.example.quizapp.ui.common.languageDisplayName(it) }
                    )
                    s.copy(
                        importedLanguages = imported,
                        availableLanguages = sortedAvailable,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun setDevServer(value: String) {
        if (value == _state.value.devServer) return
        _state.update { it.copy(devServer = value) }
        preferences.setDevServer(value)
    }

    fun refreshPackCatalog() {
        val host = _state.value.devServer.trim()
        if (host.isBlank()) {
            _state.update { it.copy(packCatalogError = "enter a dev server first") }
            return
        }
        val url = "http://$host:8000"
        _state.update { it.copy(packCatalogLoading = true, packCatalogError = null) }
        viewModelScope.launch {
            try {
                val catalog = app.qapInstaller.fetchCatalog(url)
                rawPackCatalog = catalog
                val rows = buildPackRows(catalog, _state.value.installedPacks, _state.value.contentLanguages)
                _state.update { it.copy(packCatalog = rows, packCatalogLoading = false) }
            } catch (t: Throwable) {
                rawPackCatalog = emptyList()
                _state.update {
                    it.copy(
                        packCatalog = emptyList(),
                        packCatalogLoading = false,
                        packCatalogError = t.message ?: "failed to fetch catalog",
                    )
                }
            }
        }
    }

    fun installPack(entry: QapPackEntry) {
        val host = _state.value.devServer.trim()
        if (host.isBlank()) return
        val url = "http://$host:8000"
        viewModelScope.launch {
            app.qapInstaller.install(url, entry)
        }
    }

    fun uninstallPack(entry: QapPackEntry) {
        viewModelScope.launch {
            app.qapInstaller.uninstall(entry)
        }
    }

    fun showPackDetails(row: PackRowState) {
        _state.update { it.copy(selectedPackForDetails = row) }
    }

    fun dismissPackDetails() {
        _state.update { it.copy(selectedPackForDetails = null) }
    }

    fun clearInstallState() {
        app.qapInstaller.resetState()
    }

    fun load(playerId: Long) {
        if (loadedPlayerId == playerId) return
        loadedPlayerId = playerId
        loadJob?.cancel()
        _state.value = SettingsUiState(isLoading = true)
        val localAddrs = getLocalAddresses()

        data class PrefsGroup1(
            val lang: String,
            val haptic: Boolean,
            val devMode: Boolean,
            val devServer: String,
        )

        data class PrefsGroup2(
            val installedPacks: Map<String, InstalledPack>,
            val player: Player?,
        )

        data class SettingsData(
            val lang: String,
            val haptic: Boolean,
            val devMode: Boolean,
            val devServer: String,
            val installedPacks: Map<String, InstalledPack>,
            val player: Player?,
        )

        loadJob = viewModelScope.launch {
            combine(
                combine(
                    preferences.contentLanguage,
                    preferences.hapticFeedback,
                    preferences.developerMode,
                    preferences.devServer,
                ) { lang, haptic, devMode, devServer ->
                    PrefsGroup1(lang, haptic, devMode, devServer)
                },
                combine(
                    preferences.installedPacks,
                    playerRepository.observeById(playerId),
                ) { installedPacks, player ->
                    PrefsGroup2(installedPacks, player)
                }
            ) { g1, g2 ->
                SettingsData(
                    lang = g1.lang,
                    haptic = g1.haptic,
                    devMode = g1.devMode,
                    devServer = g1.devServer,
                    installedPacks = g2.installedPacks,
                    player = g2.player,
                )
            }.collectLatest { data ->
                if (data.player == null) {
                    _state.value = SettingsUiState(isLoading = false, localAddresses = localAddrs)
                    return@collectLatest
                }
                val domains = domainRepository.getAll(data.lang)
                val preferred = data.player.preferredDomainSlugs.toSet()
                val validPreferred = preferred.filter { slug ->
                    domains.find { it.slug == slug }?.hasTranslation == true
                }.toSet()

                Log.d("QuizDomainDebug", "SettingsVM.collect lang=${data.lang} " +
                    "preferred=${preferred.sorted()} valid=${validPreferred.sorted()} " +
                    "diff=${(preferred - validPreferred).sorted()}")

                if (validPreferred != preferred) {
                    val validSelected = data.player.selectedDomainSlugs?.filter { slug ->
                        slug in validPreferred
                    }
                    viewModelScope.launch {
                        playerRepository.updatePreferredDomains(playerId, validPreferred.toList().sorted())
                        playerRepository.updateSelectedDomains(playerId, validSelected)
                    }
                }

                val campaignDao = try {
                    app.contentDatabase.campaignDao()
                } catch (t: Throwable) {
                    null
                }

                val domainGroups = buildDomainGroups(
                    domains = domains,
                    language = data.lang,
                    campaignDao = campaignDao,
                )

                val contentLangs = data.player.contentLanguages.toSet()
                val packRows = buildPackRows(rawPackCatalog, data.installedPacks, contentLangs)

                val fromAssets = app.assets.list("datasets")?.toList().orEmpty()
                val fromDb = app.questionRepository.getAllAvailableLanguages()
                val fromUi = com.example.quizapp.ui.common.Localizations.uiLanguages
                val availableSet = (fromAssets + fromDb + fromUi + data.player.contentLanguages).distinct()
                val imported = app.importedLanguages.value

                val sortedAvailable = availableSet.sortedWith(
                    compareByDescending<String> { it == "en" || it in imported }
                        .thenBy { it != "en" && it != com.example.quizapp.ui.common.Localizations.systemLanguageCode() }
                        .thenBy { com.example.quizapp.ui.common.languageDisplayName(it) }
                )

                _state.update {
                    it.copy(
                        isLoading = false,
                        availableLanguages = sortedAvailable,
                        importedLanguages = imported,
                        currentLanguage = data.lang,
                        hapticFeedback = data.haptic,
                        primaryLanguage = data.player.primaryContentLanguage,
                        contentLanguages = contentLangs,
                        availableDomains = domains,
                        domainGroups = domainGroups,
                        preferredDomainSlugs = validPreferred,
                        developerMode = data.devMode,
                        devServer = data.devServer,
                        installedPacks = data.installedPacks,
                        packCatalog = packRows,
                        localAddresses = localAddrs,
                    )
                }
            }
        }
    }

    private suspend fun buildDomainGroups(
        domains: List<DomainWithTranslation>,
        language: String,
        campaignDao: CampaignDao?,
    ): List<DomainGroup> {
        if (domains.isEmpty()) return emptyList()

        val domainIdToSlug = domains.associate { it.id to it.slug }
        val slugToDomain = domains.associateBy { it.slug }
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
                .mapNotNull { slugToDomain[it] }
                .filter { it.slug !in assignedSlugs }

            Log.d("QuizDomainDebug", "SettingsVM.group campaign=${campaign.slug} " +
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

        Log.d("QuizDomainDebug", "SettingsVM.groups campaigns=${campaigns.map { it.slug }} " +
            "groups=${groups.map { it.campaignSlug to it.domains.map { d -> d.slug } }}")

        return groups
    }

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

    fun setHapticFeedback(enabled: Boolean) {
        preferences.setHapticFeedback(enabled)
    }

    fun pingAdmin() {
        val host = _state.value.devServer.trim()
        if (host.isBlank()) {
            _state.update { it.copy(pingState = PingUiState.Failure("enter a host first")) }
            return
        }
        if (_state.value.pingState is PingUiState.InFlight) return
        _state.update { it.copy(pingState = PingUiState.InFlight) }
        viewModelScope.launch {
            val client = PingClient()
            val result = client.ping(host)
            val pingUiState = when (result) {
                is PingResult.Ok -> PingUiState.Success(result.rttMs)
                is PingResult.Timeout -> PingUiState.Timeout(result.ms)
                is PingResult.Refused -> PingUiState.Failure("connection refused")
                is PingResult.Unreachable -> PingUiState.Failure("unreachable")
                is PingResult.Malformed -> PingUiState.Failure("malformed response")
                is PingResult.Error -> PingUiState.Failure(result.message)
            }
            _state.update { it.copy(pingState = pingUiState) }
        }
    }

    fun forceSyncFeedback() {
        val host = _state.value.devServer.trim()
        if (host.isBlank()) {
            _state.update { it.copy(syncState = SyncUiState.Error("Dev server empty")) }
            return
        }
        _state.update { it.copy(syncState = SyncUiState.InProgress) }
        viewModelScope.launch {
            try {
                val count = app.syncRepository.syncFeedback("http://$host:8001")
                _state.update { it.copy(syncState = SyncUiState.Success(count)) }
            } catch (e: Exception) {
                _state.update { it.copy(syncState = SyncUiState.Error(e.message ?: "Unknown error")) }
            }
        }
    }

    fun recordIncomingEvent(event: String) {
        _state.update {
            it.copy(incomingEvents = (it.incomingEvents + event).takeLast(20))
        }
    }

    fun handleVersionTap() {
        val now = System.currentTimeMillis()
        if (now - lastVersionTapAt > TAP_WINDOW_MS) versionTapCount = 0
        lastVersionTapAt = now
        versionTapCount++
        if (versionTapCount >= TAPS_TO_UNLOCK) {
            _state.update { it.copy(developerMode = true) }
            preferences.setDeveloperMode(true)
            versionTapCount = 0
        }
    }

    private fun getLocalAddresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addrs = networkInterface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        addr.hostAddress?.let { addresses.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Error getting local addresses", e)
        }
        return addresses.sorted()
    }

    fun setPrimaryLanguage(lang: String) {
        val s = _state.value
        if (lang == s.primaryLanguage) return
        if (lang !in s.availableLanguages) return
        val newContentLanguages = (s.contentLanguages + lang).toSet()
        val packRows = buildPackRows(rawPackCatalog, s.installedPacks, newContentLanguages)
        _state.update {
            it.copy(
                primaryLanguage = lang,
                contentLanguages = newContentLanguages,
                currentLanguage = lang,
                packCatalog = packRows,
            )
        }
        preferences.setContentLanguage(lang)
        if (lang != PreferencesRepository.DEFAULT_LANGUAGE) {
            app.ensureLanguageImported(lang)
        }
        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updatePrimaryLanguage(pid, lang)
            playerRepository.updateContentLanguages(pid, newContentLanguages.toList().sorted())
        }
    }

    fun toggleContentLanguage(lang: String) {
        val s = _state.value
        if (lang == s.primaryLanguage) return
        if (lang !in s.availableLanguages) return
        val wasEnabled = lang in s.contentLanguages
        val newContentLanguages = if (wasEnabled) {
            s.contentLanguages - lang
        } else {
            s.contentLanguages + lang
        }
        val packRows = buildPackRows(rawPackCatalog, s.installedPacks, newContentLanguages)
        _state.update {
            it.copy(
                contentLanguages = newContentLanguages,
                packCatalog = packRows,
            )
        }
        if (!wasEnabled && lang != PreferencesRepository.DEFAULT_LANGUAGE) {
            app.ensureLanguageImported(lang)
        } else if (wasEnabled && lang == s.currentLanguage) {
            preferences.setContentLanguage(s.primaryLanguage)
        }
        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updateContentLanguages(pid, newContentLanguages.toList().sorted())
        }
    }

    fun toggleDomain(slug: String) {
        val s = _state.value
        val isEnabling = slug !in s.preferredDomainSlugs
        val newSet = if (isEnabling) {
            s.preferredDomainSlugs + slug
        } else {
            s.preferredDomainSlugs - slug
        }
        Log.d("QuizDomainDebug", "SettingsVM.toggleDomain slug=$slug " +
            "wasPreferred=$isEnabling newSet=$newSet")
        _state.update { it.copy(preferredDomainSlugs = newSet) }

        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updatePreferredDomains(pid, newSet.toList().sorted())
            val player = playerRepository.getById(pid)
            if (player?.selectedDomainSlugs != null) {
                val updatedSelected = if (isEnabling) {
                    (player.selectedDomainSlugs + slug).distinct().sorted()
                } else {
                    player.selectedDomainSlugs - slug
                }
                playerRepository.updateSelectedDomains(pid, updatedSelected)
            }
        }
    }

    fun toggleCampaignSelection(campaignSlug: String) {
        val s = _state.value
        val group = s.domainGroups.find { it.campaignSlug == campaignSlug } ?: return
        val groupSlugs = group.domains.map { it.slug }.toSet()
        if (groupSlugs.isEmpty()) return

        val selectedInGroup = s.preferredDomainSlugs.intersect(groupSlugs)
        val newSet = if (selectedInGroup.size == groupSlugs.size) {
            s.preferredDomainSlugs - groupSlugs
        } else {
            s.preferredDomainSlugs + groupSlugs
        }

        _state.update { it.copy(preferredDomainSlugs = newSet) }

        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updatePreferredDomains(pid, newSet.toList().sorted())
            val player = playerRepository.getById(pid)
            if (player?.selectedDomainSlugs != null) {
                val updatedSelected = player.selectedDomainSlugs.filter { it in newSet }
                playerRepository.updateSelectedDomains(pid, updatedSelected)
            }
        }
    }

    fun togglePackLanguages(entry: QapPackEntry, enable: Boolean) {
        val s = _state.value
        val otherLangs = computeOtherPackLanguages(entry.id, s)
        val newLangs = computeToggleLanguages(s.contentLanguages, entry.languages, otherLangs, enable)
        if (newLangs == s.contentLanguages) return
        applyContentLanguages(newLangs)
    }

    fun togglePackDomains(entry: QapPackEntry, enable: Boolean) {
        val s = _state.value
        val otherDomains = computeOtherPackDomains(entry.id, s)
        val newDomains = computeToggleDomains(s.preferredDomainSlugs, entry.domains, otherDomains, enable)
        if (newDomains == s.preferredDomainSlugs) return
        applyPreferredDomains(newDomains)
    }

    private fun computeToggleLanguages(
        current: Set<String>,
        packLangs: List<String>,
        otherPackLangs: Set<String>,
        enable: Boolean,
    ): Set<String> {
        return if (enable) {
            current + packLangs
        } else {
            val removable = packLangs.filter { it !in otherPackLangs }.toSet()
            current - removable
        }
    }

    private fun computeToggleDomains(
        current: Set<String>,
        packDomains: List<String>,
        otherPackDomains: Set<String>,
        enable: Boolean,
    ): Set<String> {
        return if (enable) {
            current + packDomains
        } else {
            val removable = packDomains.filter { it !in otherPackDomains }.toSet()
            current - removable
        }
    }

    private fun computeOtherPackLanguages(excludingPackId: String, s: SettingsUiState): Set<String> {
        val fromInstalled = s.installedPacks
            .filterKeys { it != excludingPackId }
            .values
            .flatMap { it.languages }
            .toSet()
        val fromCatalog = s.packCatalog
            .filter { it.installed && it.entry.id != excludingPackId }
            .flatMap { it.entry.languages }
            .toSet()
        return fromInstalled + fromCatalog
    }

    private fun computeOtherPackDomains(excludingPackId: String, s: SettingsUiState): Set<String> {
        val fromInstalled = s.installedPacks
            .filterKeys { it != excludingPackId }
            .values
            .flatMap { it.domains }
            .toSet()
        val fromCatalog = s.packCatalog
            .filter { it.installed && it.entry.id != excludingPackId }
            .flatMap { it.entry.domains }
            .toSet()
        return fromInstalled + fromCatalog
    }

    private fun applyContentLanguages(newSet: Set<String>) {
        val s = _state.value
        val newPrimary = if (s.primaryLanguage !in newSet) {
            newSet.sorted().firstOrNull() ?: s.primaryLanguage
        } else s.primaryLanguage
        val packRows = buildPackRows(rawPackCatalog, s.installedPacks, newSet)
        _state.update {
            it.copy(
                contentLanguages = newSet,
                primaryLanguage = newPrimary,
                currentLanguage = newPrimary,
                packCatalog = packRows,
            )
        }
        preferences.setContentLanguage(newPrimary)
        if (newPrimary != PreferencesRepository.DEFAULT_LANGUAGE) {
            app.ensureLanguageImported(newPrimary)
        }
        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updatePrimaryLanguage(pid, newPrimary)
            playerRepository.updateContentLanguages(pid, newSet.toList().sorted())
        }
    }

    private fun applyPreferredDomains(newSet: Set<String>) {
        _state.update { it.copy(preferredDomainSlugs = newSet) }
        val pid = loadedPlayerId ?: return
        viewModelScope.launch {
            playerRepository.updatePreferredDomains(pid, newSet.toList().sorted())
            val player = playerRepository.getById(pid)
            if (player?.selectedDomainSlugs != null) {
                val updatedSelected = player.selectedDomainSlugs.filter { it in newSet }
                playerRepository.updateSelectedDomains(pid, updatedSelected)
            }
        }
    }

    private companion object {
        const val TAPS_TO_UNLOCK = 7
        const val TAP_WINDOW_MS = 3000L
    }
}
