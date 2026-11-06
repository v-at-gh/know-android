package com.example.quizapp.data.prefs

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class InstalledPack(
    val version: String = "",
    val sha256: String = "",
    val installedAt: Long = 0L,
    val languages: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
)

/**
 * Repository interface for managing persistent application preferences
 * (e.g., UI/content language, player identity, haptic feedback, developer settings,
 * installed content packs).
 */
interface PreferencesRepository {
    val uiLanguage: StateFlow<String?>
    fun setUiLanguage(lang: String?)

    val contentLanguage: StateFlow<String>
    fun setContentLanguage(lang: String)

    val lastPlayerId: StateFlow<Long>
    fun setLastPlayerId(id: Long)
    fun clearLastPlayerId()

    val hapticFeedback: StateFlow<Boolean>
    fun setHapticFeedback(enabled: Boolean)

    val developerMode: StateFlow<Boolean>
    fun setDeveloperMode(enabled: Boolean)

    val devServer: StateFlow<String>
    fun setDevServer(value: String)

    val syncServer: StateFlow<String>
    fun setSyncServer(url: String)

    val syncToken: StateFlow<String?>
    fun setSyncToken(token: String?)

    val installedPacks: StateFlow<Map<String, InstalledPack>>
    fun markInstalled(
        id: String,
        version: String,
        sha256: String,
        languages: List<String> = emptyList(),
        domains: List<String> = emptyList(),
        installedAt: Long = System.currentTimeMillis(),
    )
    fun removeInstalledPack(id: String)

    companion object {
        const val DEFAULT_LANGUAGE = "en"
    }
}
