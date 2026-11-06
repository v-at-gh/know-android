package com.example.quizapp.data.qap

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class QapInstallerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val installer = QapInstaller()

    @Test
    fun `extract zip slip entry throws exception`() {
        val zipFile = tempFolder.newFile("malicious.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("evil contents".toByteArray())
            zos.closeEntry()
        }

        val dstDir = tempFolder.newFolder("extract_dst")
        try {
            installer.extract(zipFile, dstDir)
            fail("Expected IllegalArgumentException for zip slip entry")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("zip entry escapes extraction dir") == true)
        }
    }

    @Test
    fun `verifySha256 with blank hash throws exception`() {
        val dummyFile = tempFolder.newFile("dummy.bin")
        dummyFile.writeText("content")

        try {
            installer.verifySha256(dummyFile, "")
            fail("Expected Exception when expected sha256 is blank")
        } catch (e: IllegalStateException) {
            assertEquals("sha256 missing for pack", e.message)
        }
    }

    @Test
    fun `mergeKnowledge skips path traversal entries`() = runBlocking {
        val extractedDir = tempFolder.newFolder("extracted")
        val langDir = File(extractedDir, "knowledge/en").apply { mkdirs() }
        val indexFile = File(langDir, "index.json")
        indexFile.writeText(
            """
            [
              {
                "slug": "legit",
                "domainSlug": "test",
                "title": "Legit",
                "path": "legit.md"
              },
              {
                "slug": "evil",
                "domainSlug": "test",
                "title": "Evil",
                "path": "../../evil.md"
              }
            ]
            """.trimIndent()
        )

        File(langDir, "legit.md").writeText("# Legit")

        val entry = QapPackEntry(
            id = "test-pack",
            file = "test.qap",
            size = 100L,
            sha256 = "sha256:1234",
            version = "1.0",
            kind = "campaign",
            createdAt = "",
            languages = listOf("en"),
            campaigns = emptyList(),
            domains = listOf("test"),
            totals = emptyMap(),
        )

        val targetRoot = tempFolder.newFolder("files_root")
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = targetRoot
        }

        val testInstaller = QapInstaller(context = mockContext)
        testInstaller.mergeKnowledge(extractedDir, entry)

        val legitDst = File(targetRoot, "qap-content/knowledge/en/legit.md")
        assertTrue(legitDst.exists())

        val evilEscaped = File(tempFolder.root, "evil.md")
        assertFalse(evilEscaped.exists())
    }
}
