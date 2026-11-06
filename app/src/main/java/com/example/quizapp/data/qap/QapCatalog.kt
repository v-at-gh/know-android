package com.example.quizapp.data.qap

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a downloadable QAP (Quiz Archive Package) entry available in the remote catalog.
 *
 * @property id Unique identifier of the package.
 * @property file Relative file path or filename of the package archive on the server.
 * @property size Size of the package archive in bytes.
 * @property sha256 Cryptographic SHA256 checksum of the package archive.
 * @property version Version string of the package.
 * @property kind The package kind (e.g., "campaign").
 * @property createdAt Timestamp when the package was created.
 * @property languages List of language codes supported by this package.
 * @property campaigns List of campaigns included in this package.
 * @property domains List of knowledge domains included in this package.
 * @property totals Map of category totals (e.g., question counts) for the package.
 */
data class QapPackEntry(
    val id: String,
    val file: String,
    val size: Long,
    val sha256: String,
    val version: String,
    val kind: String,
    val createdAt: String,
    val languages: List<String>,
    val campaigns: List<String>,
    val domains: List<String>,
    val totals: Map<String, Int>,
) {
    /** Size of the package archive converted into megabytes (MB). */
    val sizeMb: Double get() = size / (1 shl 20).toDouble()
}

/**
 * Utility object responsible for parsing remote QAP catalog JSON strings into lists of [QapPackEntry] objects.
 */
object QapCatalog {
    /**
     * Parses a QAP catalog JSON string into a list of [QapPackEntry] items.
     *
     * Entries containing an error field are filtered out.
     *
     * @param json The raw JSON string of the QAP catalog.
     * @return A list of successfully parsed [QapPackEntry] items.
     */
    fun parse(json: String): List<QapPackEntry> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("packs") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.has("error")) continue
                add(QapPackEntry(
                    id = o.getString("id"),
                    file = o.getString("file"),
                    size = o.optLong("size", 0L),
                    sha256 = o.optString("sha256", ""),
                    version = o.optString("version", ""),
                    kind = o.optString("kind", "campaign"),
                    createdAt = o.optString("createdAt", ""),
                    languages = o.optJSONArray("languages").toStringList(),
                    campaigns = o.optJSONArray("campaigns").toStringList(),
                    domains = o.optJSONArray("domains").toStringList(),
                    totals = o.optJSONObject("totals")?.toIntMap() ?: emptyMap(),
                ))
            }
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { getString(it) }
}

private fun JSONObject.toIntMap(): Map<String, Int> {
    val out = mutableMapOf<String, Int>()
    val it = keys()
    while (it.hasNext()) {
        val k = it.next()
        out[k] = optInt(k, 0)
    }
    return out
}
