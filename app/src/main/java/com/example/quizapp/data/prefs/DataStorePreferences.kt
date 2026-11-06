package com.example.quizapp.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "quiz_app_prefs",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "quiz_app_prefs"))
    }
)

/**
 * DataStore implementation of [PreferencesRepository] backed by [androidx.datastore.preferences.core.Preferences].
 * Automatically migrates legacy entries from [android.content.SharedPreferences] ("quiz_app_prefs").
 */
class DataStorePreferences(context: Context) : PreferencesRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore = context.dataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val packsSerializer = MapSerializer(String.serializer(), InstalledPack.serializer())

    private val KEY_UI_LANGUAGE = stringPreferencesKey("ui_language")
    private val KEY_CONTENT_LANGUAGE = stringPreferencesKey("content_language")
    private val KEY_LAST_PLAYER_ID = longPreferencesKey("last_player_id")
    private val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    private val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
    private val KEY_DEV_SERVER = stringPreferencesKey("dev_server")
    private val KEY_SYNC_SERVER = stringPreferencesKey("sync_server")
    private val KEY_SYNC_TOKEN = stringPreferencesKey("sync_token")
    private val KEY_INSTALLED_PACKS = stringPreferencesKey("installed_packs")

    override val uiLanguage: StateFlow<String?> = dataStore.data
        .map { it[KEY_UI_LANGUAGE] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun setUiLanguage(lang: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (lang == null) {
                    prefs.remove(KEY_UI_LANGUAGE)
                } else {
                    prefs[KEY_UI_LANGUAGE] = lang
                }
            }
        }
    }

    override val contentLanguage: StateFlow<String> = dataStore.data
        .map { it[KEY_CONTENT_LANGUAGE] ?: PreferencesRepository.DEFAULT_LANGUAGE }
        .stateIn(scope, SharingStarted.Eagerly, PreferencesRepository.DEFAULT_LANGUAGE)

    override fun setContentLanguage(lang: String) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_CONTENT_LANGUAGE] = lang
            }
        }
    }

    override val lastPlayerId: StateFlow<Long> = dataStore.data
        .map { it[KEY_LAST_PLAYER_ID] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    override fun setLastPlayerId(id: Long) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_LAST_PLAYER_ID] = id
            }
        }
    }

    override fun clearLastPlayerId() = setLastPlayerId(0L)

    override val hapticFeedback: StateFlow<Boolean> = dataStore.data
        .map { it[KEY_HAPTIC_FEEDBACK] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setHapticFeedback(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_HAPTIC_FEEDBACK] = enabled
            }
        }
    }

    override val developerMode: StateFlow<Boolean> = dataStore.data
        .map { it[KEY_DEVELOPER_MODE] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override fun setDeveloperMode(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_DEVELOPER_MODE] = enabled
            }
        }
    }

    override val devServer: StateFlow<String> = dataStore.data
        .map { it[KEY_DEV_SERVER] ?: "" }
        .stateIn(scope, SharingStarted.Eagerly, "")

    override fun setDevServer(value: String) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_DEV_SERVER] = value
            }
        }
    }

    override val syncServer: StateFlow<String> = dataStore.data
        .map { it[KEY_SYNC_SERVER] ?: "" }
        .stateIn(scope, SharingStarted.Eagerly, "")

    override fun setSyncServer(url: String) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_SYNC_SERVER] = url
            }
        }
    }

    override val syncToken: StateFlow<String?> = dataStore.data
        .map { it[KEY_SYNC_TOKEN] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun setSyncToken(token: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (token == null) {
                    prefs.remove(KEY_SYNC_TOKEN)
                } else {
                    prefs[KEY_SYNC_TOKEN] = token
                }
            }
        }
    }

    override val installedPacks: StateFlow<Map<String, InstalledPack>> = dataStore.data
        .map { prefs ->
            val jsonStr = prefs[KEY_INSTALLED_PACKS]
            if (jsonStr.isNullOrBlank()) {
                emptyMap()
            } else {
                try {
                    json.decodeFromString(packsSerializer, jsonStr)
                } catch (_: Exception) {
                    emptyMap()
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override fun markInstalled(
        id: String,
        version: String,
        sha256: String,
        languages: List<String>,
        domains: List<String>,
        installedAt: Long,
    ) {
        scope.launch {
            dataStore.edit { prefs ->
                val currentStr = prefs[KEY_INSTALLED_PACKS]
                val current: Map<String, InstalledPack> = if (currentStr.isNullOrBlank()) {
                    emptyMap()
                } else {
                    try {
                        json.decodeFromString(packsSerializer, currentStr)
                    } catch (_: Exception) {
                        emptyMap()
                    }
                }
                val updated = current + (id to InstalledPack(
                    version = version,
                    sha256 = sha256,
                    installedAt = installedAt,
                    languages = languages,
                    domains = domains,
                ))
                prefs[KEY_INSTALLED_PACKS] = json.encodeToString(packsSerializer, updated)
            }
        }
    }

    override fun removeInstalledPack(id: String) {
        scope.launch {
            dataStore.edit { prefs ->
                val currentStr = prefs[KEY_INSTALLED_PACKS]
                val current: Map<String, InstalledPack> = if (currentStr.isNullOrBlank()) {
                    emptyMap()
                } else {
                    try {
                        json.decodeFromString(packsSerializer, currentStr)
                    } catch (_: Exception) {
                        emptyMap()
                    }
                }
                val updated = current - id
                prefs[KEY_INSTALLED_PACKS] = json.encodeToString(packsSerializer, updated)
            }
        }
    }
}
