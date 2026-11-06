package com.example.quizapp.data.qap

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses JSON strings into strongly typed [QapManifest] instances, enforcing format version checks
 * (expected version 2) and validating mandatory fields.
 */
object QapManifestParser {

    /**
     * Parses the manifest JSON string into a [QapManifest] object.
     *
     * @param jsonString The raw JSON string of the manifest.
     * @return The parsed [QapManifest] instance.
     * @throws QapManifestException if the JSON is malformed, version is unsupported, or required fields/arrays are missing.
     */
    fun parse(jsonString: String): QapManifest {
        val root = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            throw QapManifestException("Malformed JSON string", e)
        }

        if (!root.has("qapFormatVersion") || root.isNull("qapFormatVersion")) {
            throw QapManifestException("Missing or invalid qapFormatVersion")
        }

        val version = try {
            root.getInt("qapFormatVersion")
        } catch (e: JSONException) {
            throw QapManifestException("Missing or invalid qapFormatVersion", e)
        }

        if (version != 2) {
            throw QapManifestException("Unsupported qapFormatVersion: $version. Expected 2.")
        }

        val id = root.optString("id").takeIf { it.isNotBlank() }
            ?: throw QapManifestException("Missing or blank id")

        val manifestVersion = root.optString("version").takeIf { it.isNotBlank() }
            ?: throw QapManifestException("Missing or blank version")

        val createdAt = root.optString("createdAt").takeIf { it.isNotBlank() }
            ?: throw QapManifestException("Missing or blank createdAt")

        val publisher = if (root.has("publisher") && !root.isNull("publisher")) {
            val pubObj = root.getJSONObject("publisher")
            QapManifest.Publisher(
                name = pubObj.optString("name", ""),
                url = if (pubObj.has("url") && !pubObj.isNull("url")) pubObj.getString("url") else null,
            )
        } else null

        val signature = if (root.has("signature") && !root.isNull("signature")) {
            root.getString("signature")
        } else null

        val languages = root.optJSONArray("languages")?.toStringList() ?: emptyList()

        val domainsArray = root.optJSONArray("domains")
            ?: throw QapManifestException("Missing domains array")
        val domains = mutableListOf<QapManifest.DomainEntry>()
        for (i in 0 until domainsArray.length()) {
            val dObj = domainsArray.getJSONObject(i)
            domains.add(
                QapManifest.DomainEntry(
                    slug = dObj.optString("slug").takeIf { it.isNotBlank() }
                        ?: throw QapManifestException("Domain at index $i missing slug"),
                    name = dObj.optJSONObject("name")?.toMap() ?: emptyMap(),
                    description = dObj.optJSONObject("description")?.toMap() ?: emptyMap(),
                    languages = dObj.optJSONArray("languages")?.toStringList() ?: emptyList(),
                )
            )
        }

        val campaignsArray = root.optJSONArray("campaigns")
            ?: throw QapManifestException("Missing campaigns array")
        val campaigns = mutableListOf<QapManifest.CampaignEntry>()
        for (i in 0 until campaignsArray.length()) {
            val cObj = campaignsArray.getJSONObject(i)
            campaigns.add(
                QapManifest.CampaignEntry(
                    slug = cObj.optString("slug").takeIf { it.isNotBlank() }
                        ?: throw QapManifestException("Campaign at index $i missing slug"),
                    order = cObj.optInt("order", 0),
                    name = cObj.optJSONObject("name")?.toMap() ?: emptyMap(),
                    description = cObj.optJSONObject("description")?.toMap() ?: emptyMap(),
                    domainSlugs = cObj.optJSONArray("domainSlugs")?.toStringList() ?: emptyList(),
                    prerequisiteDomainSlugs = cObj.optJSONArray("prerequisiteDomainSlugs")?.toStringList() ?: emptyList(),
                    prerequisiteCampaignSlugs = cObj.optJSONArray("prerequisiteCampaignSlugs")?.toStringList() ?: emptyList(),
                )
            )
        }

        val checksums = root.optJSONObject("checksums")?.toMap()

        return QapManifest(
            qapFormatVersion = version,
            id = id,
            version = manifestVersion,
            createdAt = createdAt,
            publisher = publisher,
            signature = signature,
            languages = languages,
            domains = domains,
            campaigns = campaigns,
            checksums = checksums,
        )
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { getString(it) }

    private fun JSONObject.toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!isNull(key)) {
                map[key] = getString(key)
            }
        }
        return map
    }
}
