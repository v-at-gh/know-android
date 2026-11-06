package com.example.quizapp.ui.screens

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizapp.QuizApplication
import com.example.quizapp.R
import com.example.quizapp.data.DomainStats
import com.example.quizapp.data.PlayerStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the UI state of the player profile screen.
 */
sealed interface ProfileUiState {
    /** Profile data is currently loading. */
    data object Loading : ProfileUiState

    /**
     * Profile loading failed with an error message.
     * @property errorResId String resource ID of the error message.
     */
    data class Error(@StringRes val errorResId: Int) : ProfileUiState

    /**
     * Profile data loaded successfully.
     * @property playerName Name of the player.
     * @property stats Overall player statistics.
     * @property byDomain Breakdown of performance statistics by domain.
     */
    data class Ready(
        val playerName: String,
        val stats: PlayerStats,
        val byDomain: List<DomainStats>,
    ) : ProfileUiState
}

/**
 * ViewModel responsible for loading and managing player profile statistics and domain breakdowns.
 *
 * @property app Application reference for accessing repositories and preferences.
 * @property state Reactive state flow representing [ProfileUiState].
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuizApplication
    private val playerRepository = app.playerRepository
    private val attemptRepository = app.attemptRepository
    private val domainRepository = app.domainRepository
    private val contentLanguage: String
        get() = app.contentLanguage
    private var loadedFor: Pair<Long, String>? = null
    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /**
     * Loads profile statistics and domain performance breakdowns for the specified player.
     *
     * @param playerId Unique identifier of the player.
     */
    fun load(playerId: Long) {
        val language = contentLanguage
        val key = playerId to language
        if (loadedFor == key) return
        loadedFor = key
        _state.value = ProfileUiState.Loading

        viewModelScope.launch {
            try {
                val player = playerRepository.getById(playerId)
                    ?: error("Player with id=$playerId not found")
                val stats = runCatching { attemptRepository.getStatsForPlayer(playerId) }
                    .getOrDefault(PlayerStats(0, 0, 0))
                val rawByDomain = runCatching { attemptRepository.getStatsByDomain(playerId) }
                    .getOrDefault(emptyList())
                val domainTranslations = runCatching { domainRepository.getAll(language) }
                    .getOrDefault(emptyList())
                val slugToName = domainTranslations.associate { it.slug to it.name }
                val installedSlugs = domainTranslations.map { it.slug }.toSet()

                val byDomain = rawByDomain.map { ds ->
                    val name = slugToName[ds.domainSlug]
                        ?: if (installedSlugs.isNotEmpty() && ds.domainSlug !in installedSlugs) "Deleted Domain"
                        else ds.domainName
                    ds.copy(domainName = name)
                }

                _state.value = ProfileUiState.Ready(
                    playerName = player.name,
                    stats = stats,
                    byDomain = byDomain,
                )
            } catch (t: Throwable) {
                Log.e("ProfileViewModel", "Failed to load profile for player=$playerId", t)
                _state.value = ProfileUiState.Error(R.string.error_profile_load_failed)
            }
        }
    }
}
