package com.example.quizapp.data.importer

import android.content.Context
import android.util.Log
import com.example.quizapp.data.ContentDatabase
import com.example.quizapp.data.entity.ArticleFts
import com.example.quizapp.data.knowledge.AssetTextResourceLoader
import com.example.quizapp.data.knowledge.FileFirstLoader
import com.example.quizapp.data.qap.QapImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates offline content and knowledge base imports.
 *
 * Implements a two-stage import strategy: metadata (domains, catalog structure) is imported
 * synchronously on startup, while question datasets and knowledge assets are imported asynchronously
 * in the background.
 */
open class ContentBootstrapper(
    protected val context: Context,
    protected val db: ContentDatabase,
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val importMutex = Mutex()
    private val qapImporter = QapImporter(context, db)
    private val datasetImporter = DatasetImporter(context, db)

    open val _appReady = MutableStateFlow(false)
    val appReady: StateFlow<Boolean> = _appReady.asStateFlow()

    open val _isIdle = MutableStateFlow(true)
    open val isIdle: StateFlow<Boolean> = _isIdle.asStateFlow()

    open val _showSplashProgressBar = MutableStateFlow(false)
    val showSplashProgressBar: StateFlow<Boolean> = _showSplashProgressBar.asStateFlow()

    open val _importProgress = MutableStateFlow<Progress?>(null)
    val importProgress: StateFlow<Progress?> = _importProgress.asStateFlow()

    open val _importingDomainIds = MutableStateFlow<Set<Long>>(emptySet())
    open val importingDomainIds: StateFlow<Set<Long>> = _importingDomainIds.asStateFlow()

    open val _importedLanguages = MutableStateFlow<Set<String>>(emptySet())
    open val importedLanguages: StateFlow<Set<String>> = _importedLanguages.asStateFlow()

    open val _importingLanguages = MutableStateFlow<Set<String>>(emptySet())
    open val importingLanguages: StateFlow<Set<String>> = _importingLanguages.asStateFlow()

    fun markLanguagesImported(langs: Set<String>) {
        if (langs.isEmpty()) return
        _importedLanguages.update { it + langs }
    }

    /**
     * Initializes app bootstrapping, detects fresh installs, runs synchronous metadata import,
     * sets `appReady = true`, and triggers background English question import and FTS index rebuilding.
     */
    fun bootstrap() {
        scope.launch {
            try {
                val initialTranslationCount = db.questionDao().countTranslationsByLanguage("en")
                val isFreshInstall = initialTranslationCount == 0
                _showSplashProgressBar.value = isFreshInstall

                // 1. ALWAYS import metadata synchronously
                val qapMetaReport = qapImporter.importMetadataFromAssets("qap")
                if (!qapMetaReport.isImported) {
                    datasetImporter.importMetadata("en")
                }

                // 2. Set appReady = true immediately so UI can render
                _appReady.value = true

                // 3. ALWAYS launch background question import for "en"
                importLanguageInternal("en", isInitial = true)
                scanImportedLanguages()
                rebuildFtsIndex()
            } catch (t: Throwable) {
                Log.e("ContentBootstrapper", "Bootstrap failed", t)
            } finally {
                _appReady.value = true
                _showSplashProgressBar.value = false
            }
        }
    }

    suspend fun scanImportedLanguages() = importMutex.withLock {
        val available = context.assets.list("datasets")?.toList().orEmpty()
        val imported = available.filter { datasetImporter.isLanguageImported(it) }.toSet()
        if (imported.isNotEmpty()) {
            _importedLanguages.update { it + imported }
        }
    }

    private suspend fun rebuildFtsIndex() {
        val dao = db.articleFtsDao()
        dao.clear()
        val allLangs = db.questionDao().getAllLanguagesWithQuestions() + "en"
        val records = mutableListOf<ArticleFts>()
        val loader = FileFirstLoader(context.filesDir, AssetTextResourceLoader(context))
        
        for (lang in allLangs.distinct()) {
            val indexJson = try {
                loader.load("knowledge/$lang/index.json")
            } catch (_: Exception) { continue } ?: continue
            val arr = org.json.JSONArray(indexJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val domain = obj.getString("domainSlug")
                val slug = obj.getString("slug").split("/").last()
                val title = obj.getString("title")
                val path = obj.getString("path")
                
                val mdText = try {
                    loader.load("knowledge/$lang/$path")
                } catch (_: Exception) { "" } ?: ""
                
                if (mdText.isNotBlank()) {
                    records.add(ArticleFts(lang = lang, domainSlug = domain, slug = slug, title = title, content = mdText))
                }
            }
        }
        if (records.isNotEmpty()) dao.insertAll(records)
    }

    fun ensureLanguageImported(lang: String) {
        scope.launch { importLanguageInternal(lang, isInitial = false) }
    }

    /**
     * Imports questions for a specific language using a coroutine [Mutex] (`importMutex`) to prevent
     * concurrent race conditions. Tracks progress, updates importing domain IDs, and marks languages
     * as imported upon success.
     *
     * @param lang The language code to import (e.g., "en").
     * @param isInitial Whether this is part of initial startup bootstrapping.
     * @return `true` if import succeeded or was already imported, `false` otherwise.
     */
    private suspend fun importLanguageInternal(lang: String, isInitial: Boolean): Boolean = importMutex.withLock {
        if (!isInitial && lang in _importedLanguages.value) return@withLock true
        if (lang in _importingLanguages.value) return@withLock false

        _importingLanguages.update { it + lang }
        _isIdle.value = false

        try {
            val allDomains = db.domainDao().getAll()
            val domainMap = allDomains.associate { it.slug to it.id }
            _importingDomainIds.value = domainMap.values.toSet()

            Log.i("ContentBootstrapper", "Importing questions for language=$lang ...")

            val onProgress: (Progress) -> Unit = { progress ->
                _importProgress.value = progress
                if (progress.currentDomainSlug.isNotEmpty()) {
                    domainMap[progress.currentDomainSlug]?.let { doneId ->
                        _importingDomainIds.update { it - doneId }
                    }
                }
            }

            val qapReport = qapImporter.importFromAssets("qap", onProgress = onProgress)
            val isSuccess = if (qapReport.questionsImported > 0 || qapReport.errors.isEmpty()) {
                true
            } else {
                val report = datasetImporter.importLanguage(lang, onProgress = onProgress)
                report.isImported || report.errors.isEmpty()
            }

            if (isSuccess) {
                _importedLanguages.update { it + lang }
            }
            isSuccess
        } catch (t: Throwable) {
            Log.e("ContentBootstrapper", "Import $lang failed", t)
            false
        } finally {
            _importingLanguages.update { it - lang }
            _importingDomainIds.value = emptySet()
            _isIdle.value = true
            if (isInitial) {
                _showSplashProgressBar.value = false
            }
        }
    }
}
