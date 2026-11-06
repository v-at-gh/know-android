package com.example.quizapp.ui.screens.create

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizapp.QuizApplication
import com.example.quizapp.R
import com.example.quizapp.data.dao.DomainWithTranslation
import com.example.quizapp.data.entity.Player
import com.example.quizapp.data.prefs.PreferencesRepository
import com.example.quizapp.ui.common.Localizations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state representing the player creation wizard flow.
 *
 * @property name Player's entered name.
 * @property uiLanguage Selected UI language code.
 * @property availableLanguages List of available content languages.
 * @property selectedPrimaryLanguage Selected primary content language.
 * @property availableDomains List of available domains with translations.
 * @property selectedDomainSlugs Set of selected domain slugs.
 * @property importedLanguages Set of imported content languages.
 * @property importingLanguages Set of languages currently importing.
 * @property errorResId Optional error message string resource ID.
 * @property isCreating True if player creation is in progress.
 * @property canContinueFromLanguage Whether the user can proceed from the language step.
 */
data class CreatePlayerUiState(
    val name: String = "",
    val uiLanguage: String? = null,
    val availableLanguages: List<String> = emptyList(),
    val selectedPrimaryLanguage: String = PreferencesRepository.DEFAULT_LANGUAGE,
    val availableDomains: List<DomainWithTranslation> = emptyList(),
    val selectedDomainSlugs: Set<String> = emptySet(),
    val importedLanguages: Set<String> = emptySet(),
    val importingLanguages: Set<String> = emptySet(),
    @StringRes val errorResId: Int? = null,
    val isCreating: Boolean = false,
    val isBundledMode: Boolean = false,
    val bundledCompleteLanguages: List<String> = emptyList(),
    val installingTargetLang: String? = null,
) {
    val canContinueFromLanguage: Boolean
        get() = selectedPrimaryLanguage.isNotBlank()
}

/**
 * ViewModel managing state and business logic for creating a new player, including language and domain preferences.
 */
class CreatePlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as QuizApplication
    private val playerRepository = app.playerRepository
    private val domainRepository = app.domainRepository
    private val preferences = app.preferences

    private val _state = MutableStateFlow(CreatePlayerUiState())
    val state: StateFlow<CreatePlayerUiState> = _state.asStateFlow()

    private var domainsLoadedFor: String? = null

    init {
        val currentUiLang = preferences.uiLanguage.value
        val qapLangs = try {
            val manifestJson = app.assets.open("qap/manifest.json").bufferedReader().use { it.readText() }
            com.example.quizapp.data.qap.QapManifestParser.parse(manifestJson).languages
        } catch (_: Exception) { emptyList() }
        val datasetLangs = app.assets.list("datasets")?.toList().orEmpty()
        val initialLangs = (qapLangs + datasetLangs + Localizations.uiLanguages).distinct().sorted()
        val defaultContentLang = deriveDefaultContentLanguage(currentUiLang, initialLangs)
        _state.update {
            it.copy(
                uiLanguage = currentUiLang,
                availableLanguages = initialLangs,
                selectedPrimaryLanguage = defaultContentLang,
                importedLanguages = app.importedLanguages.value,
                importingLanguages = app.importingLanguages.value,
            )
        }

        viewModelScope.launch {
            val qapLangsAsync = withContext(Dispatchers.IO) {
                try {
                    val manifestJson = app.assets.open("qap/manifest.json").bufferedReader().use { it.readText() }
                    com.example.quizapp.data.qap.QapManifestParser.parse(manifestJson).languages
                } catch (e: Exception) { emptyList() }
            }
            val fromAssets = app.assets.list("datasets")?.toList().orEmpty()
            val fromDb = app.questionRepository.getAllAvailableLanguages()
            val fromUi = Localizations.uiLanguages
            val allLangs = (qapLangsAsync + fromAssets + fromDb + fromUi).distinct().sorted()
            _state.update { current ->
                val defLang = deriveDefaultContentLanguage(current.uiLanguage, allLangs)
                current.copy(
                    availableLanguages = allLangs,
                    selectedPrimaryLanguage = if (current.selectedPrimaryLanguage == PreferencesRepository.DEFAULT_LANGUAGE) defLang else current.selectedPrimaryLanguage
                )
            }
        }

        combine(
            app.importedLanguages,
            app.importingLanguages,
        ) { imported, importing -> imported to importing }
            .onEach { (imported, importing) ->
                val target = _state.value.installingTargetLang
                if (target != null && target in imported) {
                    _state.update {
                        it.copy(
                            importedLanguages = imported,
                            importingLanguages = importing,
                            selectedPrimaryLanguage = target,
                            installingTargetLang = null,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            importedLanguages = imported,
                            importingLanguages = importing,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val bundledLangs = withContext(Dispatchers.IO) {
                val hasQap = try {
                    app.assets.list("qap")?.contains("manifest.json") == true
                } catch (e: Exception) { false }

                if (hasQap) {
                    try {
                        val manifestJson = app.assets.open("qap/manifest.json").bufferedReader().use { it.readText() }
                        com.example.quizapp.data.qap.QapManifestParser.parse(manifestJson).languages
                    } catch (e: Exception) { emptyList() }
                } else {
                    val allLangs = app.assets.list("datasets")?.toList() ?: emptyList()
                    allLangs.filter { lang ->
                        val basePath = "datasets/$lang"
                        val campaignsFile = "$basePath/campaigns.json"
                        val domainsFile = "$basePath/domains.json"
                        val hasCampaigns = try {
                            app.assets.open(campaignsFile).use { it.read() > 0 }
                        } catch (e: Exception) { false }
                        
                        val hasDomains = try {
                            app.assets.open(domainsFile).use { it.read() > 0 }
                        } catch (e: Exception) { false }
                        
                        hasCampaigns && hasDomains
                    }
                }
            }
            val hasQap = try {
                app.assets.list("qap")?.contains("manifest.json") == true
            } catch (e: Exception) { false }
            val hasRawDatasets = app.assets.list("datasets")?.isNotEmpty() == true
            val hasBundledContent = hasQap || hasRawDatasets

            _state.update { it.copy(
                isBundledMode = hasBundledContent || bundledLangs.isNotEmpty(),
                bundledCompleteLanguages = bundledLangs
            ) }
        }
    }

    /** Installs/ensures a language is imported and sets it as target for auto-select. */
    fun installLanguage(lang: String) {
        _state.update { it.copy(installingTargetLang = lang) }
        app.ensureLanguageImported(lang)
    }

    /** Sets the player name input. */
    fun setName(value: String) {
        _state.update { it.copy(name = value, errorResId = null) }
    }

    /** Sets the UI language and updates default content language accordingly. */
    fun setUiLanguage(lang: String?) {
        preferences.setUiLanguage(lang)
        app.applyUiLanguage(lang)
        _state.update { current ->
            val defaultContent = deriveDefaultContentLanguage(lang, current.availableLanguages)
            current.copy(
                uiLanguage = lang,
                selectedPrimaryLanguage = defaultContent,
            )
        }
        preferences.setContentLanguage(_state.value.selectedPrimaryLanguage)
    }

    /** Sets the primary content language for the player. */
    fun setPrimaryLanguage(lang: String) {
        if (lang == _state.value.selectedPrimaryLanguage) return
        if (lang !in _state.value.availableLanguages) return
        _state.update { it.copy(selectedPrimaryLanguage = lang, errorResId = null) }
        preferences.setContentLanguage(lang)
        if (lang != PreferencesRepository.DEFAULT_LANGUAGE && lang !in _state.value.importedLanguages) {
            app.ensureLanguageImported(lang)
        }
    }

    /** Loads available domains for the current primary content language. */
    fun loadDomains() {
        val lang = _state.value.selectedPrimaryLanguage
        if (domainsLoadedFor == lang) return
        if (lang != PreferencesRepository.DEFAULT_LANGUAGE && lang !in _state.value.importedLanguages) return
        domainsLoadedFor = lang
        viewModelScope.launch {
            val domains = domainRepository.getAll(lang)
            _state.update {
                it.copy(
                    availableDomains = domains,
                    selectedDomainSlugs = domains.map { d -> d.slug }.toSet(),
                )
            }
        }
    }

    /** Toggles selection state of a domain by its slug. */
    fun toggleDomain(slug: String) {
        _state.update { current ->
            val newSet = if (slug in current.selectedDomainSlugs) {
                current.selectedDomainSlugs - slug
            } else {
                current.selectedDomainSlugs + slug
            }
            current.copy(selectedDomainSlugs = newSet)
        }
    }

    /** Selects all available domains. */
    fun selectAllDomains() {
        _state.update { it.copy(selectedDomainSlugs = it.availableDomains.map { d -> d.slug }.toSet()) }
    }

    /** Clears any active error message. */
    fun clearError() {
        _state.update { it.copy(errorResId = null) }
    }

    /** Clears all selected domains. */
    fun clearAllDomains() {
        _state.update { it.copy(selectedDomainSlugs = emptySet()) }
    }

    /**
     * Creates a new player quickly (for poweruser mode without content/domains steps).
     */
    fun createPlayerQuick(onCreated: (Long) -> Unit) {
        val s = _state.value
        val trimmed = s.name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(errorResId = R.string.error_player_name_empty) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true) }
            try {
                val contentLanguages = listOf(PreferencesRepository.DEFAULT_LANGUAGE)
                val id = playerRepository.create(
                    Player(
                        name = trimmed,
                        contentLanguages = contentLanguages,
                        primaryContentLanguage = s.selectedPrimaryLanguage,
                        preferredDomainSlugs = emptyList(),
                    )
                )
                if (id == -1L) {
                    _state.update {
                        it.copy(isCreating = false, errorResId = R.string.error_player_name_duplicate)
                    }
                } else {
                    _state.update { it.copy(isCreating = false) }
                    onCreated(id)
                }
            } catch (t: Throwable) {
                Log.e("CreatePlayerViewModel", "Failed to create player quick", t)
                _state.update {
                    it.copy(isCreating = false, errorResId = R.string.error_generic)
                }
            }
        }
    }

    /**
     * Creates a new player with the configured name, languages, and domains.
     *
     * @param onCreated Callback invoked with the new player ID upon successful creation.
     */
    fun createPlayer(onCreated: (Long) -> Unit) {
        val s = _state.value
        val trimmed = s.name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(errorResId = R.string.error_player_name_empty) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true) }
            try {
                val contentLanguages = (setOf(s.selectedPrimaryLanguage, PreferencesRepository.DEFAULT_LANGUAGE))
                    .toList()
                    .sorted()
                
                val domainsToSelect = if (s.isBundledMode) {
                    domainRepository.getAll(s.selectedPrimaryLanguage).map { it.slug }
                } else {
                    s.selectedDomainSlugs.toList()
                }

                val id = playerRepository.create(
                    Player(
                        name = trimmed,
                        contentLanguages = contentLanguages,
                        primaryContentLanguage = s.selectedPrimaryLanguage,
                        preferredDomainSlugs = domainsToSelect.sorted(),
                    )
                )
                if (id == -1L) {
                    _state.update {
                        it.copy(isCreating = false, errorResId = R.string.error_player_name_duplicate)
                    }
                } else {
                    contentLanguages
                        .filter { it != PreferencesRepository.DEFAULT_LANGUAGE }
                        .forEach { app.ensureLanguageImported(it) }
                    _state.update { it.copy(isCreating = false) }
                    onCreated(id)
                }
            } catch (t: Throwable) {
                Log.e("CreatePlayerViewModel", "Failed to create player", t)
                _state.update {
                    it.copy(isCreating = false, errorResId = R.string.error_generic)
                }
            }
        }
    }

    /** Resets the creation wizard state. */
    fun reset() {
        domainsLoadedFor = null
        val currentUiLang = preferences.uiLanguage.value
        _state.update { current ->
            CreatePlayerUiState(
                uiLanguage = currentUiLang,
                availableLanguages = current.availableLanguages,
                selectedPrimaryLanguage = deriveDefaultContentLanguage(currentUiLang, current.availableLanguages),
                importedLanguages = app.importedLanguages.value,
                importingLanguages = app.importingLanguages.value,
            )
        }
    }

    private fun deriveDefaultContentLanguage(uiLang: String?, availableLangs: List<String>): String {
        if (availableLangs.isEmpty()) return PreferencesRepository.DEFAULT_LANGUAGE
        if (uiLang != null && uiLang in availableLangs) return uiLang
        val sysLang = Localizations.systemLanguageCode()
        if (sysLang in availableLangs) return sysLang
        if (PreferencesRepository.DEFAULT_LANGUAGE in availableLangs) return PreferencesRepository.DEFAULT_LANGUAGE
        return availableLangs.first()
    }
}
