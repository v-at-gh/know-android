package com.example.quizapp.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizapp.QuizApplication
import com.example.quizapp.data.prefs.InstalledPack
import com.example.quizapp.data.prefs.PreferencesRepository
import com.example.quizapp.data.qap.QapInstallState
import com.example.quizapp.data.qap.QapPackEntry
import com.example.quizapp.ui.common.PackRowState
import com.example.quizapp.ui.common.buildPackRows
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents the UI state for the quiz pack catalog screen.
 *
 * @property isLoading Whether catalog fetching is currently in progress.
 * @property errorMessage Error message string if catalog fetching or installation fails.
 * @property packs List of available [PackRowState] items.
 * @property selectedPackForDetails The pack row currently selected for viewing details, or null.
 * @property installState Current installation state of active QAP pack operations.
 * @property currentPlayerDomains Currently active player's preferred domain slugs.
 * @property currentPlayerLanguages Currently active player's content languages.
 */
data class QuizCatalogUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val packs: List<PackRowState> = emptyList(),
    val selectedPackForDetails: PackRowState? = null,
    val installState: QapInstallState = QapInstallState.Idle,
    val currentPlayerDomains: Set<String> = emptySet(),
    val currentPlayerLanguages: Set<String> = emptySet(),
)

/**
 * ViewModel managing the catalog of remote downloadable QAP packs, orchestrating catalog fetching,
 * pack installation, updates, and uninstallation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizCatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuizApplication
    private val preferences = app.preferences
    private val _state = MutableStateFlow(QuizCatalogUiState())
    val state: StateFlow<QuizCatalogUiState> = _state.asStateFlow()
    private var rawPackCatalog: List<QapPackEntry> = emptyList()

    init {
        app.qapInstaller.state
            .onEach { s -> _state.update { it.copy(installState = s) } }
            .launchIn(viewModelScope)

        combine(
            preferences.devServer,
            preferences.installedPacks,
            preferences.contentLanguage,
        ) { devServer, installedPacks, contentLang ->
            Triple(devServer, installedPacks, contentLang)
        }.onEach { (devServer, installedPacks, contentLang) ->
            fetchCatalogInternal(devServer, installedPacks, setOf(contentLang))
        }.launchIn(viewModelScope)

        preferences.lastPlayerId
            .flatMapLatest { pid ->
                if (pid <= 0L) flowOf(null)
                else app.playerRepository.observeById(pid)
            }
            .onEach { player ->
                _state.update {
                    it.copy(
                        currentPlayerDomains = player?.preferredDomainSlugs?.toSet().orEmpty(),
                        currentPlayerLanguages = player?.contentLanguages?.toSet().orEmpty(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val devServer = preferences.devServer.value
        val installedPacks = preferences.installedPacks.value
        val contentLang = preferences.contentLanguage.value
        fetchCatalogInternal(devServer, installedPacks, setOf(contentLang))
    }

    private fun fetchCatalogInternal(devServer: String, installedPacks: Map<String, InstalledPack>, contentLanguages: Set<String>) {
        val host = devServer.trim()
        if (host.isBlank()) {
            _state.update {
                it.copy(
                    isLoading = false,
                    packs = emptyList(),
                    errorMessage = "No dev server configured. Go to Settings -> Developer to configure a server.",
                )
            }
            return
        }
        val url = "http://$host:8000"
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val catalog = app.qapInstaller.fetchCatalog(url)
                rawPackCatalog = catalog
                val rows = buildPackRows(catalog, installedPacks, contentLanguages)
                _state.update {
                    it.copy(
                        isLoading = false,
                        packs = rows,
                        errorMessage = null,
                    )
                }
            } catch (t: Throwable) {
                rawPackCatalog = emptyList()
                _state.update {
                    it.copy(
                        isLoading = false,
                        packs = emptyList(),
                        errorMessage = t.message ?: "Failed to fetch catalog",
                    )
                }
            }
        }
    }

    fun installPack(entry: QapPackEntry) {
        val host = preferences.devServer.value.trim()
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

    fun togglePackLanguages(entry: QapPackEntry, enable: Boolean) {
        val playerId = preferences.lastPlayerId.value
        if (playerId == 0L) return
        viewModelScope.launch {
            val player = app.playerRepository.getById(playerId) ?: return@launch
            val current = player.contentLanguages.toSet()
            val otherLangs = _state.value.packs
                .filter { it.installed && it.entry.id != entry.id }
                .flatMap { it.entry.languages }
                .toSet()
            val newSet = computeToggleLanguages(current, entry.languages, otherLangs, enable)
            if (newSet == current) return@launch
            app.playerRepository.updateContentLanguages(playerId, newSet.toList().sorted())

            if (player.primaryContentLanguage !in newSet) {
                val newPrimary = newSet.sorted().firstOrNull() ?: player.primaryContentLanguage
                app.playerRepository.updatePrimaryLanguage(playerId, newPrimary)
                preferences.setContentLanguage(newPrimary)
            }
            if (enable) {
                entry.languages.forEach { lang ->
                    if (lang != PreferencesRepository.DEFAULT_LANGUAGE) {
                        app.ensureLanguageImported(lang)
                    }
                }
            }
        }
    }

    fun togglePackDomains(entry: QapPackEntry, enable: Boolean) {
        val playerId = preferences.lastPlayerId.value
        if (playerId == 0L) return
        viewModelScope.launch {
            val player = app.playerRepository.getById(playerId) ?: return@launch
            val current = player.preferredDomainSlugs.toSet()
            val otherDomains = _state.value.packs
                .filter { it.installed && it.entry.id != entry.id }
                .flatMap { it.entry.domains }
                .toSet()
            val newSet = computeToggleDomains(current, entry.domains, otherDomains, enable)
            if (newSet == current) return@launch
            app.playerRepository.updatePreferredDomains(playerId, newSet.toList().sorted())

            if (player.selectedDomainSlugs != null) {
                val updatedSelected = player.selectedDomainSlugs.filter { it in newSet }
                app.playerRepository.updateSelectedDomains(playerId, updatedSelected)
            }
        }
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
}
