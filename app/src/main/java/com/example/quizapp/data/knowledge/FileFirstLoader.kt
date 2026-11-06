package com.example.quizapp.data.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class FileFirstLoader(
    private val filesDir: File,
    private val assetLoader: TextResourceLoader,
) : TextResourceLoader {
    override suspend fun load(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(filesDir, "qap-content/$path")
        if (file.isFile) {
            try {
                return@withContext file.readText(Charsets.UTF_8)
            } catch (_: IOException) {
                // fall through to assets
            }
        }
        assetLoader.load(path)
    }
}
