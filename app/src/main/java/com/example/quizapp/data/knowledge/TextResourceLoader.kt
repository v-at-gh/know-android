package com.example.quizapp.data.knowledge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface TextResourceLoader {
    suspend fun load(path: String): String?
}

class AssetTextResourceLoader(private val context: Context) : TextResourceLoader {
    override suspend fun load(path: String): String? = withContext(Dispatchers.IO) {
        try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            null
        }
    }
}