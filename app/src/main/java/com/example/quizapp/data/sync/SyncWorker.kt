package com.example.quizapp.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.quizapp.QuizApplication

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as QuizApplication
        val devServer = app.preferences.devServer.value.trim()
        if (devServer.isBlank()) {
            Log.d("SyncWorker", "No dev server configured, skipping.")
            return Result.success()
        }

        val baseUrl = try {
            buildFeedbackBaseUrl(devServer)
        } catch (e: IllegalArgumentException) {
            Log.e("SyncWorker", "Invalid devServer '$devServer', skipping sync", e)
            return Result.success()
        }
        return try {
            val count = app.syncRepository.syncFeedback(baseUrl)
            Log.d("SyncWorker", "Synced $count items")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}

internal fun buildFeedbackBaseUrl(rawDevServer: String): String {
    val host = rawDevServer.trim()
        .removePrefix("http://").removePrefix("https://")
        .removeSuffix("/")
        .substringBefore(":")
    require(host.isNotBlank() && !host.contains("://")) {
        "invalid devServer: '$rawDevServer'"
    }
    return "http://$host:8001"
}
