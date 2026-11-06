package com.example.quizapp

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.quizapp.data.ContentDatabase
import com.example.quizapp.data.UserDatabase
import com.example.quizapp.data.importer.ContentBootstrapper
import com.example.quizapp.data.knowledge.AssetKnowledgeRepository
import com.example.quizapp.data.knowledge.AssetTextResourceLoader
import com.example.quizapp.data.knowledge.FileFirstLoader
import com.example.quizapp.data.knowledge.KnowledgeRepository
import com.example.quizapp.data.prefs.DataStorePreferences
import com.example.quizapp.data.prefs.PreferencesRepository
import com.example.quizapp.data.qap.QapInstaller
import com.example.quizapp.data.repository.AttemptRepository
import com.example.quizapp.data.repository.DomainRepository
import com.example.quizapp.data.repository.PlayerRepository
import com.example.quizapp.data.repository.QuestionRepository
import com.example.quizapp.data.repository.RoomAttemptRepository
import com.example.quizapp.data.repository.RoomDomainRepository
import com.example.quizapp.data.repository.RoomPlayerRepository
import com.example.quizapp.data.repository.RoomQuestionRepository
import com.example.quizapp.ui.common.Localizations
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Custom [Application] class for QuizApp.
 * Initializes core databases, preferences, content bootstrappers, repositories, and application localization settings.
 */
open class QuizApplication : Application() {
    /** Application preferences accessor. */
    val preferences: PreferencesRepository by lazy { DataStorePreferences(this) }

    /** Currently active content language code. */
    open val contentLanguage: String
        get() = preferences.contentLanguage.value

    // --- Databases ---
    /** Room database storing quiz content, questions, and articles. */
    val contentDatabase: ContentDatabase by lazy { ContentDatabase.getInstance(this) }
    /** Room database storing user profile, player, and attempt data. */
    val userDatabase: UserDatabase by lazy { UserDatabase.getInstance(this) }

    // --- Content Bootstrapper ---
    /** Bootstrapper managing initial asset extraction and dataset imports. */
    open val contentBootstrapper: ContentBootstrapper by lazy { ContentBootstrapper(this, contentDatabase) }

    /** Flow emitting the set of successfully imported language codes. */
    val importedLanguages: StateFlow<Set<String>>
        get() = contentBootstrapper.importedLanguages

    /** Flow emitting the set of language codes currently being imported. */
    val importingLanguages: StateFlow<Set<String>>
        get() = contentBootstrapper.importingLanguages

    /** Flow indicating whether the application content database is fully ready. */
    val appReady: StateFlow<Boolean>
        get() = contentBootstrapper.appReady

    private val _deepLinkEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    /** Shared flow emitting deep link events containing server URL and package ID. */
    val deepLinkEvents: SharedFlow<Pair<String, String>> = _deepLinkEvents.asSharedFlow()

    /**
     * Handles incoming deep links by emitting a server and pack ID pair.
     *
     * @param server Dev server address from the deep link.
     * @param packId Package ID to install.
     */
    fun handleDeepLink(server: String, packId: String) {
        _deepLinkEvents.tryEmit(server to packId)
    }

    // --- Repositories ---
    /** Repository for player management operations. */
    open val playerRepository: PlayerRepository by lazy { RoomPlayerRepository(userDatabase.playerDao()) }
    /** Repository for accessing quiz questions. */
    open val questionRepository: QuestionRepository by lazy { RoomQuestionRepository(contentDatabase.questionDao()) }
    /** Repository for tracking quiz attempts and statistics. */
    open val attemptRepository: AttemptRepository by lazy { RoomAttemptRepository(userDatabase.attemptDao()) }
    /** Repository for domain hierarchy and translations. */
    open val domainRepository: DomainRepository by lazy { RoomDomainRepository(contentDatabase.domainDao()) }
    /** Repository for network sync operations. */
    open val syncRepository: com.example.quizapp.data.sync.SyncRepository by lazy { com.example.quizapp.data.sync.SyncRepository(userDatabase) }

    private val _assetKnowledgeRepository by lazy {
        AssetKnowledgeRepository(
            FileFirstLoader(filesDir, AssetTextResourceLoader(this))
        )
    }

    /** Repository for accessing knowledge base articles. */
    open val knowledgeRepository: KnowledgeRepository
        get() = _assetKnowledgeRepository

    /** Installer for QAP content packs. */
    open val qapInstaller: QapInstaller by lazy {
        QapInstaller(this, preferences, contentDatabase, _assetKnowledgeRepository, contentBootstrapper)
    }

    override fun onCreate() {
        super.onCreate()
        Localizations.updateSystemLocale(readSystemLocale())
        
        // Synchronous read to avoid locale flash on startup
        val initialUiLang = runBlocking { preferences.uiLanguage.first() }
        applyUiLanguage(initialUiLang)

        contentBootstrapper.bootstrap()
        scheduleSyncWorker()
    }

    private fun scheduleSyncWorker() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val request = androidx.work.PeriodicWorkRequestBuilder<com.example.quizapp.data.sync.SyncWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun readSystemLocale(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = getSystemService(LocaleManager::class.java)
            val locales = lm?.systemLocales
            if (locales != null && !locales.isEmpty) {
                locales[0].language
            } else {
                ""
            }
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale.language
        }.ifBlank { "en" }
    }

    /**
     * Applies the specified UI language locale to the application.
     *
     * @param lang Language tag code, or null to follow system default.
     */
    fun applyUiLanguage(lang: String?) {
        val locales = if (lang == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Ensures that the specified language dataset is imported.
     *
     * @param lang Language code to import.
     */
    fun ensureLanguageImported(lang: String) {
        contentBootstrapper.ensureLanguageImported(lang)
    }
}
