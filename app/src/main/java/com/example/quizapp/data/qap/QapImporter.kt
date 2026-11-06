package com.example.quizapp.data.qap

import android.content.Context
import com.example.quizapp.data.ContentDatabase
import com.example.quizapp.data.entity.Campaign
import com.example.quizapp.data.entity.CampaignDomain
import com.example.quizapp.data.entity.CampaignTranslation
import com.example.quizapp.data.entity.Domain
import com.example.quizapp.data.entity.DomainTranslation
import com.example.quizapp.data.importer.DatasetImporter
import com.example.quizapp.data.importer.MetadataReport
import com.example.quizapp.data.importer.Progress
import java.io.IOException

/**
 * Responsible for parsing and importing QAP package assets (metadata, campaigns, domains, translations,
 * domain links, and question JSON datasets) into the Room database ([ContentDatabase]).
 */
class QapImporter(
    private val db: ContentDatabase,
    private val assetReader: (String) -> String?,
) {
    constructor(context: Context, db: ContentDatabase) : this(
        db = db,
        assetReader = { path ->
            try {
                context.assets.open(path).bufferedReader().use { it.readText() }
            } catch (_: IOException) {
                null
            }
        }
    )

    /**
     * Imports pack metadata (campaigns, domains, translations, and domain-campaign links) from assets
     * without importing question datasets.
     *
     * @param bundlePath The root asset directory path for the QAP package.
     * @return A [MetadataReport] detailing import success and any encountered errors.
     */
    suspend fun importMetadataFromAssets(bundlePath: String = "qap"): MetadataReport {
        val manifestPath = if (bundlePath.isBlank()) "manifest.json" else "$bundlePath/manifest.json"
        val manifestJson = assetReader(manifestPath)
            ?: return MetadataReport(
                errors = listOf("manifest.json not found at $manifestPath"),
            )

        val manifest = try {
            QapManifestParser.parse(manifestJson)
        } catch (e: Exception) {
            return MetadataReport(
                errors = listOf("Failed to parse manifest: ${e.message}"),
            )
        }

        var campaignsImported = 0
        var domainsImported = 0
        val errors = mutableListOf<String>()

        for (cEntry in manifest.campaigns) {
            val campaignId = upsertCampaign(cEntry, manifest)
            if (campaignId == -1L) {
                errors.add("${cEntry.slug}: failed to upsert campaign")
                continue
            }
            campaignsImported++

            val languages = (cEntry.name.keys + cEntry.description.keys).toSet()
            for (lang in languages) {
                val name = cEntry.name[lang] ?: cEntry.slug
                val desc = cEntry.description[lang] ?: ""
                upsertCampaignTranslation(campaignId, lang, name, desc)
            }
        }

        val domainIdMap = mutableMapOf<String, Long>()
        for ((slug, name1, description) in manifest.domains) {
            val domainId = upsertDomain(slug)
            if (domainId == -1L) {
                errors.add("$slug: failed to upsert domain")
                continue
            }
            domainIdMap[slug] = domainId
            domainsImported++

            val languages = (name1.keys + description.keys).toSet()
            for (lang in languages) {
                val name = name1[lang] ?: slug
                val desc = description[lang] ?: ""
                upsertDomainTranslation(domainId, lang, name, desc)
            }
        }

        for ((slug, _, _, _, domainSlugs) in manifest.campaigns) {
            val campaignId = db.campaignDao().getBySlug(slug)?.id ?: continue
            domainSlugs.forEachIndexed { position, dSlug ->
                val domainId = domainIdMap[dSlug] ?: db.domainDao().getBySlug(dSlug)?.id
                if (domainId != null) {
                    upsertCampaignDomain(campaignId, domainId, position)
                } else {
                    errors.add("$slug: domain $dSlug not found to link")
                }
            }
        }

        return MetadataReport(
            domainsImported = domainsImported,
            campaignsImported = campaignsImported,
            errors = errors,
        )
    }

    /**
     * Performs a full import of both metadata and question/hint datasets for all supported domains and languages,
     * reporting import progress via [onProgress].
     *
     * @param bundlePath The root asset directory path for the QAP package.
     * @param onProgress Optional callback invoked with import progress updates.
     * @return A [QapImportReport] summarizing imported entities and errors.
     */
    suspend fun importFromAssets(
        bundlePath: String = "qap",
        onProgress: ((Progress) -> Unit)? = null,
    ): QapImportReport {
        val manifestPath = if (bundlePath.isBlank()) "manifest.json" else "$bundlePath/manifest.json"
        val manifestJson = assetReader(manifestPath)
            ?: return QapImportReport(
                packId = "",
                packVersion = "",
                errors = listOf("manifest.json not found at $manifestPath"),
            )

        val manifest = try {
            QapManifestParser.parse(manifestJson)
        } catch (e: Exception) {
            return QapImportReport(
                packId = "",
                packVersion = "",
                errors = listOf("Failed to parse manifest: ${e.message}"),
            )
        }

        var campaignsImported = 0
        var domainsImported = 0
        var questionsImported = 0
        var hintsImported = 0
        val errors = mutableListOf<String>()

        // 1. Campaigns and campaign translations
        for (cEntry in manifest.campaigns) {
            val campaignId = upsertCampaign(cEntry, manifest)
            if (campaignId == -1L) {
                errors.add("${cEntry.slug}: failed to upsert campaign")
                continue
            }
            campaignsImported++

            val languages = (cEntry.name.keys + cEntry.description.keys).toSet()
            for (lang in languages) {
                val name = cEntry.name[lang] ?: cEntry.slug
                val desc = cEntry.description[lang] ?: ""
                upsertCampaignTranslation(campaignId, lang, name, desc)
            }
        }

        // 2. Domains and domain translations
        val domainIdMap = mutableMapOf<String, Long>()
        for ((slug, name1, description) in manifest.domains) {
            val domainId = upsertDomain(slug)
            if (domainId == -1L) {
                errors.add("$slug: failed to upsert domain")
                continue
            }
            domainIdMap[slug] = domainId
            domainsImported++

            val languages = (name1.keys + description.keys).toSet()
            for (lang in languages) {
                val name = name1[lang] ?: slug
                val desc = description[lang] ?: ""
                upsertDomainTranslation(domainId, lang, name, desc)
            }
        }

        // 3. Link campaigns to domains
        for ((slug, _, _, _, domainSlugs) in manifest.campaigns) {
            val campaignId = db.campaignDao().getBySlug(slug)?.id ?: continue
            domainSlugs.forEachIndexed { position, dSlug ->
                val domainId = domainIdMap[dSlug] ?: db.domainDao().getBySlug(dSlug)?.id
                if (domainId != null) {
                    upsertCampaignDomain(campaignId, domainId, position)
                } else {
                    errors.add("$slug: domain $dSlug not found to link")
                }
            }
        }

        // 4. Import questions and hints for each domain and language
        onProgress?.invoke(Progress(0, manifest.domains.size, "", 0))
        var domainsDoneCount = 0

        val datasetImporter = DatasetImporter(db, assetReader)
        for ((slug, _, _, languages) in manifest.domains) {
            val domainId = domainIdMap[slug] ?: continue
            for (lang in languages) {
                val relPath = if (bundlePath.isBlank()) "datasets/$lang/$slug.json" else "$bundlePath/datasets/$lang/$slug.json"
                val altRelPath = "datasets/$lang/$slug.json"
                val questionsJson = assetReader(relPath) ?: assetReader(altRelPath)
                if (questionsJson == null) {
                    errors.add("$slug ($lang): questions file not found at $relPath")
                    continue
                }

                val result = datasetImporter.importQuestionsJson(
                    domainId = domainId,
                    domainSlug = slug,
                    language = lang,
                    questionsJson = questionsJson,
                )
                questionsImported += result.questionsImported
                hintsImported += result.hintsImported
                errors.addAll(result.errors)
            }
            domainsDoneCount++
            onProgress?.invoke(
                Progress(
                    domainsDone = domainsDoneCount,
                    domainsTotal = manifest.domains.size,
                    currentDomainSlug = slug,
                    questionsImported = questionsImported,
                )
            )
        }

        return QapImportReport(
            packId = manifest.id,
            packVersion = manifest.version,
            campaignsImported = campaignsImported,
            domainsImported = domainsImported,
            questionsImported = questionsImported,
            hintsImported = hintsImported,
            errors = errors,
        )
    }

    private suspend fun upsertCampaign(entry: QapManifest.CampaignEntry, manifest: QapManifest): Long {
        val existing = db.campaignDao().getBySlug(entry.slug)
        if (existing != null) {
            val updated = existing.copy(
                order = entry.order,
                sourcePackId = manifest.id,
                sourcePackVersion = manifest.version,
                signature = manifest.signature,
                prerequisiteDomainSlugs = entry.prerequisiteDomainSlugs,
                prerequisiteCampaignSlugs = entry.prerequisiteCampaignSlugs,
            )
            db.campaignDao().update(updated)
            return existing.id
        }
        val newId = db.campaignDao().insert(
            Campaign(
                slug = entry.slug,
                order = entry.order,
                sourcePackId = manifest.id,
                sourcePackVersion = manifest.version,
                signature = manifest.signature,
                prerequisiteDomainSlugs = entry.prerequisiteDomainSlugs,
                prerequisiteCampaignSlugs = entry.prerequisiteCampaignSlugs,
            )
        )
        if (newId != -1L) return newId
        return db.campaignDao().getBySlug(entry.slug)?.id ?: -1L
    }

    private suspend fun upsertCampaignTranslation(campaignId: Long, language: String, name: String, description: String) {
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

    private suspend fun upsertCampaignDomain(campaignId: Long, domainId: Long, position: Int) {
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

    private suspend fun upsertDomain(slug: String): Long {
        val existing = db.domainDao().getBySlug(slug)
        if (existing != null) return existing.id
        val id = db.domainDao().insert(Domain(slug = slug))
        if (id != -1L) return id
        return db.domainDao().getBySlug(slug)?.id ?: -1L
    }

    private suspend fun upsertDomainTranslation(domainId: Long, language: String, name: String, description: String) {
        val existing = db.domainDao().getTranslation(domainId, language)
        if (existing != null) {
            db.domainDao().updateTranslation(
                DomainTranslation(
                    id = existing.id,
                    domainId = domainId,
                    language = language,
                    name = name,
                    description = description,
                )
            )
        } else {
            db.domainDao().insertTranslation(
                DomainTranslation(
                    domainId = domainId,
                    language = language,
                    name = name,
                    description = description,
                )
            )
        }
    }
}
