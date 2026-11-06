package com.example.quizapp.data.importer

import android.content.Context
import android.util.Log
import com.example.quizapp.data.ContentDatabase
import com.example.quizapp.data.entity.Campaign
import com.example.quizapp.data.entity.CampaignDomain
import com.example.quizapp.data.entity.CampaignTranslation
import com.example.quizapp.data.entity.Domain
import com.example.quizapp.data.entity.DomainTranslation
import com.example.quizapp.data.entity.Hint
import com.example.quizapp.data.entity.HintTranslation
import com.example.quizapp.data.entity.Question
import com.example.quizapp.data.entity.QuestionTranslation
import java.io.IOException

/**
 * Encapsulates the results of importing questions for a specific domain.
 *
 * @property questionsImported Number of questions successfully imported.
 * @property hintsImported Number of hints successfully imported.
 * @property errors List of error messages encountered during import.
 */
data class QuestionsImportResult(
    val questionsImported: Int,
    val hintsImported: Int,
    val errors: List<String> = emptyList(),
)

/**
 * Imports knowledge domains, V1 campaigns, questions, translations, and hints from JSON assets
 * into the Room database ([ContentDatabase]).
 */
class DatasetImporter(
    private val db: ContentDatabase,
    private val assetReader: (String) -> String?,
) {
    constructor(context: Context, db: ContentDatabase) : this(
        db = db,
        assetReader = { path ->
            try {
                context.assets.open(path).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                Log.w("DatasetImporter", "Asset not found or unreadable: $path", e)
                null
            }
        }
    )

    /**
     * Checks whether question translations have already been imported for the specified [language].
     *
     * @param language The language code to check (e.g., "en").
     * @return `true` if at least one question translation exists in the database.
     */
    suspend fun isLanguageImported(language: String): Boolean =
        db.questionDao().countTranslationsByLanguage(language) > 0

    /**
     * Imports domain and campaign metadata for a language from assets without importing question payloads.
     *
     * @param language The target language code.
     * @return A [MetadataReport] summarizing imported metadata and errors.
     */
    suspend fun importMetadata(language: String): MetadataReport {
        val basePath = "datasets/$language"
        val domainsJson = assetReader("$basePath/domains.json")
            ?: return MetadataReport(
                errors = listOf("domains.json not found at $basePath"),
            )

        val parsedDomains = try {
            DatasetParser.parseDomains(domainsJson)
        } catch (t: Throwable) {
            return MetadataReport(
                errors = listOf("Failed to parse domains.json: ${t.message}"),
            )
        }

        var domainsImported = 0
        val errors = mutableListOf<String>()

        for (entry in parsedDomains) {
            val domainId = upsertDomain(entry, language)
            if (domainId == null) {
                errors.add("${entry.slug}: failed to upsert domain")
            } else {
                domainsImported++
            }
        }

        val campaignsJson = assetReader("$basePath/campaigns.json")
        var campaignsImported = 0
        if (campaignsJson != null) {
            try {
                val array = org.json.JSONArray(campaignsJson)
                campaignsImported = array.length()
                importV1Campaigns(language, campaignsJson)
            } catch (e: Exception) {
                errors.add("Failed to import v1 campaigns: ${e.message}")
            }
        }

        return MetadataReport(
            domainsImported = domainsImported,
            campaignsImported = campaignsImported,
            errors = errors,
        )
    }

    /**
     * Performs a full dataset import for a language (domains, questions, hints, and campaigns),
     * reporting real-time progress via [onProgress].
     *
     * @param language The target language code.
     * @param onProgress Optional callback invoked with import progress updates.
     * @return An [ImportReport] summarizing imported items and errors.
     */
    suspend fun importLanguage(
        language: String,
        onProgress: ((Progress) -> Unit)? = null,
    ): ImportReport {
        val basePath = "datasets/$language"
        val domainsJson = assetReader("$basePath/domains.json")
            ?: return ImportReport(
                language = language,
                errors = listOf("domains.json not found at $basePath"),
            )

        val parsedDomains = try {
            DatasetParser.parseDomains(domainsJson)
        } catch (t: Throwable) {
            return ImportReport(
                language = language,
                errors = listOf("Failed to parse domains.json: ${t.message}"),
            )
        }

        onProgress?.invoke(Progress(0, parsedDomains.size, "", 0))

        var domainsImported = 0
        var questionsImported = 0
        var hintsImported = 0
        val skippedDomains = mutableListOf<String>()
        val skippedQuestions = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (entry in parsedDomains) {
            val domainId = upsertDomain(entry, language) ?: run {
                errors.add("${entry.slug}: failed to upsert domain")
                continue
            }

            val questionsJson = assetReader("$basePath/${entry.slug}.json")
            if (questionsJson == null) {
                errors.add("${entry.slug}: questions file not found")
                continue
            }

            val result = importQuestionsJson(
                domainId = domainId,
                domainSlug = entry.slug,
                language = language,
                questionsJson = questionsJson,
            )

            questionsImported += result.questionsImported
            hintsImported += result.hintsImported
            errors.addAll(result.errors)
            domainsImported++

            onProgress?.invoke(
                Progress(
                    domainsDone = domainsImported,
                    domainsTotal = parsedDomains.size,
                    currentDomainSlug = entry.slug,
                    questionsImported = questionsImported,
                )
            )
        }

        val campaignsJson = assetReader("$basePath/campaigns.json")
        if (campaignsJson != null) {
            importV1Campaigns(language, campaignsJson)
        }

        return ImportReport(
            language = language,
            domainsImported = domainsImported,
            questionsImported = questionsImported,
            hintsImported = hintsImported,
            skippedDomains = skippedDomains,
            skippedQuestions = skippedQuestions,
            errors = errors,
        )
    }

    /**
     * Parses and upserts questions and progressive hints from JSON for a specific domain and language.
     *
     * @param domainId The database ID of the domain.
     * @param domainSlug The slug identifier of the domain.
     * @param language The target language code.
     * @param questionsJson Raw JSON string containing the question dataset.
     * @return A [QuestionsImportResult] containing counts of inserted questions, hints, and errors.
     */
    suspend fun importQuestionsJson(
        domainId: Long,
        domainSlug: String,
        language: String,
        questionsJson: String,
    ): QuestionsImportResult {
        val (parsedQuestions, parseErrors) = try {
            DatasetParser.parseQuestions(questionsJson)
        } catch (t: Throwable) {
            return QuestionsImportResult(
                0,
                0,
                listOf("$domainSlug: failed to parse questions.json: ${t.message}")
            )
        }

        val errors = mutableListOf<String>()
        parseErrors.forEach { errors.add("$domainSlug: $it") }

        if (parsedQuestions.isEmpty()) {
            errors.add("$domainSlug: no valid questions after filtering")
            return QuestionsImportResult(0, 0, errors)
        }

        var domainQuestionsInserted = 0
        var domainHintsInserted = 0

        parsedQuestions.forEach { q ->
            val questionId = resolveQuestionId(domainId, q) ?: run {
                errors.add("$domainSlug: failed to insert question ${q.uuid}")
                return@forEach
            }

            val translationInserted = insertTranslationIdempotent(
                questionId = questionId,
                language = language,
                entry = q,
            )
            if (translationInserted) domainQuestionsInserted++

            val hintCount = insertHintsIdempotent(
                questionId = questionId,
                language = language,
                hintTexts = q.hints,
            )
            domainHintsInserted += hintCount
        }

        return QuestionsImportResult(
            questionsImported = domainQuestionsInserted,
            hintsImported = domainHintsInserted,
            errors = errors,
        )
    }

    private suspend fun importV1Campaigns(language: String, json: String) {
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val slug = obj.getString("slug")
                val order = obj.optInt("order", 0)
                val name = obj.optString("name", slug)
                val description = obj.optString("description", "")
                val domainSlugs = obj.optJSONArray("domainSlugs")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: emptyList()
                val prereqDomains = obj.optJSONArray("prerequisiteDomainSlugs")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: emptyList()
                val prereqCampaigns = obj.optJSONArray("prerequisiteCampaignSlugs")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: emptyList()

                val campaignId = upsertV1Campaign(
                    slug = slug,
                    order = order,
                    prerequisiteDomainSlugs = prereqDomains,
                    prerequisiteCampaignSlugs = prereqCampaigns,
                )
                if (campaignId != -1L) {
                    upsertV1CampaignTranslation(campaignId, language, name, description)
                    domainSlugs.forEachIndexed { position, dSlug ->
                        val domainId = db.domainDao().getBySlug(dSlug)?.id
                        if (domainId != null) {
                            upsertV1CampaignDomain(campaignId, domainId, position)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("DatasetImporter", "Failed to parse or import v1 campaigns.json for $language", e)
        }
    }

    private suspend fun upsertV1Campaign(
        slug: String,
        order: Int,
        prerequisiteDomainSlugs: List<String>,
        prerequisiteCampaignSlugs: List<String>,
    ): Long {
        val existing = db.campaignDao().getBySlug(slug)
        if (existing != null) {
            val updated = existing.copy(
                order = order,
                prerequisiteDomainSlugs = prerequisiteDomainSlugs,
                prerequisiteCampaignSlugs = prerequisiteCampaignSlugs,
            )
            db.campaignDao().update(updated)
            return existing.id
        }
        val newId = db.campaignDao().insert(
            Campaign(
                slug = slug,
                order = order,
                sourcePackId = null,
                sourcePackVersion = null,
                signature = null,
                prerequisiteDomainSlugs = prerequisiteDomainSlugs,
                prerequisiteCampaignSlugs = prerequisiteCampaignSlugs,
            )
        )
        if (newId != -1L) return newId
        return db.campaignDao().getBySlug(slug)?.id ?: -1L
    }

    private suspend fun upsertV1CampaignTranslation(
        campaignId: Long,
        language: String,
        name: String,
        description: String,
    ) {
        val existing = db.campaignDao().getTranslation(campaignId, language)
        if (existing != null) {
            db.campaignDao().updateTranslation(existing.copy(name = name, description = description))
        } else {
            db.campaignDao().insertTranslation(
                CampaignTranslation(
                    campaignId = campaignId,
                    language = language,
                    name = name,
                    description = description,
                )
            )
        }
    }

    private suspend fun upsertV1CampaignDomain(campaignId: Long, domainId: Long, position: Int) {
        val existing = db.campaignDao().getCampaignDomain(campaignId, domainId)
        if (existing != null) {
            if (existing.position != position) {
                db.campaignDao().updateCampaignDomain(existing.copy(position = position))
            }
        } else {
            db.campaignDao().insertCampaignDomain(
                CampaignDomain(
                    campaignId = campaignId,
                    domainId = domainId,
                    position = position,
                )
            )
        }
    }

    private suspend fun resolveQuestionId(
        domainId: Long,
        q: DatasetParser.QuestionEntry,
    ): Long? {
        db.questionDao().getByUuid(q.uuid)?.let { return it.id }
        val newId = db.questionDao().insert(
            Question(uuid = q.uuid, domainId = domainId, difficulty = q.difficulty, type = q.type)
        )
        return newId.takeIf { it != -1L }
    }

    private suspend fun insertTranslationIdempotent(
        questionId: Long,
        language: String,
        entry: DatasetParser.QuestionEntry,
    ): Boolean {
        val result = db.questionDao().insertTranslation(
            QuestionTranslation(
                questionId = questionId,
                language = language,
                text = entry.text,
                answer = entry.answer,
                distractors = entry.distractors,
                answerWhy = entry.answerWhy,
                relatedArticleSlug = entry.relatedArticleSlug,
                payloadJson = entry.payloadJson,
            )
        )
        return result != -1L
    }

    private suspend fun insertHintsIdempotent(
        questionId: Long,
        language: String,
        hintTexts: List<String>,
    ): Int {
        if (hintTexts.isEmpty()) return 0
        var inserted = 0
        hintTexts.forEachIndexed { index, text ->
            var hint = db.hintDao().getByQuestionAndOrder(questionId, index)
            if (hint == null) {
                db.hintDao().insert(
                    Hint(questionId = questionId, order = index)
                )
                hint = db.hintDao().getByQuestionAndOrder(questionId, index)
            }
            if (hint == null) return@forEachIndexed

            val result = db.hintDao().insertTranslation(
                HintTranslation(hintId = hint.id, language = language, text = text)
            )
            if (result != -1L) inserted++
        }
        return inserted
    }

    private suspend fun upsertDomain(
        entry: DatasetParser.DomainEntry,
        language: String,
    ): Long? {
        val existing = db.domainDao().getBySlug(entry.slug)
        val domainId = existing?.id ?: run {
            val id = db.domainDao().insert(Domain(slug = entry.slug))
            if (id == -1L) return null
            id
        }
        val existingTranslation = db.domainDao().getTranslation(domainId, language)
        if (existingTranslation != null) {
            db.domainDao().updateTranslation(
                DomainTranslation(
                    id = existingTranslation.id,
                    domainId = domainId,
                    language = language,
                    name = entry.name,
                    description = entry.description,
                )
            )
        } else {
            db.domainDao().insertTranslation(
                DomainTranslation(
                    domainId = domainId,
                    language = language,
                    name = entry.name,
                    description = entry.description,
                )
            )
        }
        return domainId
    }
}
