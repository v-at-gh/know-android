package com.example.quizapp.data.qap

import android.content.Context
import com.example.quizapp.data.ContentDatabase
import com.example.quizapp.data.entity.ArticleFts
import com.example.quizapp.data.importer.ContentBootstrapper
import com.example.quizapp.data.knowledge.ArticleMeta
import com.example.quizapp.data.knowledge.AssetKnowledgeRepository
import com.example.quizapp.data.prefs.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Represents the lifecycle and progress states of downloading, verifying, importing, or uninstalling a QAP pack.
 */
sealed interface QapInstallState {
    data object Idle : QapInstallState
    data class Downloading(val id: String, val bytesRead: Long, val totalBytes: Long) : QapInstallState
    data class Verifying(val id: String) : QapInstallState
    data class Importing(val id: String) : QapInstallState
    data class Deleting(val id: String) : QapInstallState
    data class Done(val id: String, val domains: Int, val questions: Int) : QapInstallState
    data class Uninstalled(val id: String) : QapInstallState
    data class Failed(val id: String, val reason: String) : QapInstallState
}

/**
 * Manages downloading, cryptographic SHA256 verification, extraction, Room database import,
 * knowledge base article merging, FTS index updating, and uninstallation of QAP packs from a remote server.
 */
open class QapInstaller(
    private val context: Context? = null,
    private val preferences: PreferencesRepository? = null,
    private val contentDatabase: ContentDatabase? = null,
    private val knowledgeRepository: AssetKnowledgeRepository? = null,
    private val bootstrapper: ContentBootstrapper? = null,
) {
    private val _state = MutableStateFlow<QapInstallState>(QapInstallState.Idle)
    open val state: StateFlow<QapInstallState> = _state.asStateFlow()

    /**
     * Fetches and parses the remote QAP catalog manifest from the specified [serverUrl].
     *
     * @param serverUrl The base URL of the remote content server.
     * @return A list of available [QapPackEntry] items.
     */
    suspend fun fetchCatalog(serverUrl: String): List<QapPackEntry> = withContext(Dispatchers.IO) {
        val url = URL("${serverUrl.trimEnd('/')}/qaps/manifest.json")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 15_000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode != 200) {
                error("HTTP ${conn.responseCode} from $url")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            QapCatalog.parse(body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Orchestrates the complete installation workflow for a QAP pack: downloads the archive,
     * verifies its SHA256 checksum, extracts files, imports questions into Room, merges knowledge
     * articles, updates FTS indices, marks languages as imported, and saves installation preferences.
     *
     * @param serverUrl The base URL of the remote content server.
     * @param entry The [QapPackEntry] to install.
     */
    suspend fun install(serverUrl: String, entry: QapPackEntry) {
        val ctx = context ?: return
        val incoming = File(ctx.cacheDir, "qap-incoming/${entry.id}.qap")
        val extracted = File(ctx.cacheDir, "qap-extracted/${entry.id}")
        try {
            incoming.parentFile?.mkdirs()
            extracted.deleteRecursively()

            download(serverUrl, entry, incoming)
            _state.value = QapInstallState.Verifying(entry.id)
            verifySha256(incoming, entry.sha256)

            _state.value = QapInstallState.Importing(entry.id)
            extract(incoming, extracted)
            val importResult = importIntoRoom(extracted)
            mergeKnowledge(extracted, entry)

            val records = mutableListOf<ArticleFts>()
            for (lang in entry.languages) {
                val indexFile = File(extracted, "knowledge/$lang/index.json")
                if (!indexFile.isFile) continue
                val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val domain = obj.getString("domainSlug")
                    val slug = obj.getString("slug").split("/").last()
                    val title = obj.getString("title")
                    val path = obj.getString("path")

                    val mdFile = File(extracted, "knowledge/$lang/$path").canonicalFile
                    if (mdFile.isFile && mdFile.startsWith(extracted.canonicalFile)) {
                        records.add(ArticleFts(lang = lang, domainSlug = domain, slug = slug, title = title, content = mdFile.readText(Charsets.UTF_8)))
                    }
                }
            }
            if (records.isNotEmpty()) {
                contentDatabase?.articleFtsDao()?.insertAll(records)
            }

            bootstrapper?.markLanguagesImported(entry.languages.toSet())
            preferences?.markInstalled(
                id = entry.id,
                version = entry.version,
                sha256 = entry.sha256,
                languages = entry.languages,
                domains = entry.domains,
            )
            knowledgeRepository?.invalidate()

            _state.value = QapInstallState.Done(
                id = entry.id,
                domains = importResult.domains,
                questions = importResult.questions,
            )
        } catch (t: Throwable) {
            _state.value = QapInstallState.Failed(entry.id, t.message ?: t::class.java.simpleName)
        } finally {
            incoming.delete()
            extracted.deleteRecursively()
        }
    }

    fun resetState() { _state.value = QapInstallState.Idle }

    fun getBaseDomains(): Set<String> {
        val ctx = context ?: return emptySet()
        val json = try {
            ctx.assets.open("datasets/en/domains.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }
        if (json.isBlank()) return emptySet()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getJSONObject(it).getString("slug") }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    fun getDomainsToDelete(entry: QapPackEntry): List<String> {
        val baseDomains = getBaseDomains()
        return entry.domains.filter { it !in baseDomains }
    }

    /**
     * Uninstalls a QAP pack by deleting associated domains, FTS records, knowledge assets,
     * and preference records.
     *
     * @param entry The [QapPackEntry] to uninstall.
     */
    suspend fun uninstall(entry: QapPackEntry) {
        val ctx = context ?: return
        val db = contentDatabase ?: return
        _state.value = QapInstallState.Deleting(entry.id)
        withContext(Dispatchers.IO) {
            try {
                val domainsToDelete = getDomainsToDelete(entry)

                if (domainsToDelete.isNotEmpty()) {
                    db.domainDao().deleteDomainsBySlugs(domainsToDelete)
                    try {
                        db.articleFtsDao().deleteByDomainSlugs(domainsToDelete)
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                val root = File(ctx.filesDir, "qap-content")
                for (lang in entry.languages) {
                    val indexFile = File(root, "knowledge/$lang/index.json")
                    if (indexFile.isFile) {
                        val current = knowledgeRepository?.getIndex(lang).orEmpty()
                        val filtered = current.filterNot { it.domainSlug in domainsToDelete }
                        indexFile.writeText(serializeIndex(filtered), Charsets.UTF_8)
                    }

                    for (domain in domainsToDelete) {
                        val domainDir = File(root, "knowledge/$lang/$domain")
                        domainDir.deleteRecursively()
                    }
                }

                preferences?.removeInstalledPack(entry.id)
                knowledgeRepository?.invalidate()

                _state.value = QapInstallState.Uninstalled(entry.id)
            } catch (t: Throwable) {
                _state.value = QapInstallState.Failed(entry.id, t.message ?: "Uninstall failed")
            }
        }
    }

    // ---------- internals ----------

    private suspend fun download(serverUrl: String, entry: QapPackEntry, dst: File) =
        withContext(Dispatchers.IO) {
            val url = URL("${serverUrl.trimEnd('/')}/qaps/${entry.file}")
            var existingLength = if (dst.exists()) dst.length() else 0L
            var conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 30_000
                requestMethod = "GET"
                if (existingLength > 0) {
                    setRequestProperty("Range", "bytes=$existingLength-")
                }
            }
            try {
                var responseCode = conn.responseCode
                if (responseCode == 416) {
                    conn.disconnect()
                    dst.delete()
                    existingLength = 0L
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5_000
                        readTimeout = 30_000
                        requestMethod = "GET"
                    }
                    responseCode = conn.responseCode
                }

                val append: Boolean
                val total: Long
                val initialRead: Long

                when (responseCode) {
                    206 -> {
                        append = true
                        initialRead = existingLength
                        val contentLength = conn.contentLengthLong
                        total = if (contentLength > 0) existingLength + contentLength else entry.size
                    }
                    200 -> {
                        append = false
                        initialRead = 0L
                        total = conn.contentLengthLong.takeIf { it > 0 } ?: entry.size
                    }
                    else -> {
                        error("HTTP $responseCode from $url")
                    }
                }

                var read = initialRead
                conn.inputStream.use { input ->
                    FileOutputStream(dst, append).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            _state.value = QapInstallState.Downloading(entry.id, read, total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

    internal fun verifySha256(file: File, expected: String) {
        if (expected.isBlank()) error("sha256 missing for pack")
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        val actualWithPrefix = "sha256:$hex"
        val expectedNormalized = if (expected.startsWith("sha256:", ignoreCase = true)) expected else "sha256:$expected"
        if (!actualWithPrefix.equals(expectedNormalized, ignoreCase = true)) {
            error("sha256 mismatch: expected $expected, got $actualWithPrefix")
        }
    }

    internal fun extract(zip: File, dst: File) {
        dst.mkdirs()
        val canonicalDst = dst.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                val out = File(dst, e.name)
                val canonicalOut = out.canonicalFile
                require(canonicalOut.startsWith(canonicalDst)) { "zip entry escapes extraction dir: ${e.name}" }
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
    }

    private data class ImportResult(val domains: Int, val questions: Int)

    private suspend fun importIntoRoom(extracted: File): ImportResult {
        val db = contentDatabase ?: return ImportResult(0, 0)
        val importer = QapImporter(db) { path ->
            File(extracted, path).takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }
        val report = importer.importFromAssets(bundlePath = "", onProgress = null)
        return ImportResult(
            domains = report.domainsImported,
            questions = report.questionsImported,
        )
    }

    internal suspend fun mergeKnowledge(extracted: File, entry: QapPackEntry) =
        withContext(Dispatchers.IO) {
            val ctx = context ?: return@withContext
            val root = File(ctx.filesDir, "qap-content")
            val packDomains = entry.domains.toSet()

            for (lang in entry.languages) {
                val incomingIndex = File(extracted, "knowledge/$lang/index.json")
                if (!incomingIndex.isFile) continue

                val current = knowledgeRepository?.getIndex(lang).orEmpty()
                val incoming = parseIndex(incomingIndex.readText(Charsets.UTF_8))

                val kept = current.filterNot { it.domainSlug in packDomains }
                val merged = (kept + incoming).sortedWith(
                    compareBy({ it.domainSlug }, { it.order })
                )

                val dstIndex = File(root, "knowledge/$lang/index.json")
                dstIndex.parentFile?.mkdirs()
                dstIndex.writeText(serializeIndex(merged), Charsets.UTF_8)

                for (meta in incoming) {
                    val src = File(extracted, "knowledge/$lang/${meta.path}").canonicalFile
                    if (!src.isFile || !src.startsWith(extracted.canonicalFile)) continue
                    val dst = File(root, "knowledge/$lang/${meta.path}").canonicalFile
                    if (!dst.startsWith(root.canonicalFile)) continue
                    dst.parentFile?.mkdirs()
                    src.copyTo(dst, overwrite = true)
                }
            }
        }

    private fun parseIndex(json: String): List<ArticleMeta> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val slug = o.getString("slug")
            val uuid = o.optString("uuid").takeIf { it.isNotBlank() }
                ?: UUID.nameUUIDFromBytes("knowledge/$slug".toByteArray()).toString()
            ArticleMeta(
                uuid = uuid,
                slug = slug,
                domainSlug = o.getString("domainSlug"),
                title = o.optString("title", ""),
                description = o.optString("description", ""),
                path = o.getString("path"),
                order = o.optInt("order", 0),
            )
        }
    }

    private fun serializeIndex(list: List<ArticleMeta>): String {
        val arr = JSONArray()
        for (m in list) {
            arr.put(JSONObject().apply {
                put("uuid", m.uuid)
                put("slug", m.slug)
                put("domainSlug", m.domainSlug)
                put("title", m.title)
                put("description", m.description)
                put("path", m.path)
                put("order", m.order)
            })
        }
        return arr.toString(2)
    }
}
