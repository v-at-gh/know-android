package com.example.quizapp.data.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileFirstLoaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeAssetLoader(
        private val assets: Map<String, String>,
    ) : TextResourceLoader {
        override suspend fun load(path: String): String? = assets[path]
    }

    @Test
    fun `loads from file first if file exists`() = runBlocking {
        val root = tempFolder.newFolder()
        val customFile = File(root, "qap-content/knowledge/en/index.json")
        customFile.parentFile?.mkdirs()
        customFile.writeText("custom-file-content", Charsets.UTF_8)

        val assetLoader = FakeAssetLoader(
            mapOf("knowledge/en/index.json" to "asset-content")
        )
        val loader = FileFirstLoader(root, assetLoader)

        val loaded = loader.load("knowledge/en/index.json")
        assertEquals("custom-file-content", loaded)
    }

    @Test
    fun `falls back to asset loader if file does not exist`() = runBlocking {
        val root = tempFolder.newFolder()
        val assetLoader = FakeAssetLoader(
            mapOf("knowledge/en/index.json" to "asset-content")
        )
        val loader = FileFirstLoader(root, assetLoader)

        val loaded = loader.load("knowledge/en/index.json")
        assertEquals("asset-content", loaded)
    }

    @Test
    fun `returns null if file and asset do not exist`() = runBlocking {
        val root = tempFolder.newFolder()
        val loader = FileFirstLoader(root, FakeAssetLoader(emptyMap()))

        val loaded = loader.load("knowledge/en/missing.json")
        assertNull(loaded)
    }
}
