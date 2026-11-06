package com.example.quizapp.data.sync

import android.util.Log
import com.example.quizapp.data.UserDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SyncRepository(private val db: UserDatabase) {

    @Serializable
    private data class FeedbackPayload(
        val client_uuid: String,
        val question_uuid: String?,
        val question_text: String,
        val selected_answer: String,
        val correct_answer: String,
        val language: String,
        val domain_slug: String,
        val article_slug: String?,
        val reason_code: String,
        val comment: String,
        val app_version: String,
        val locale: String,
        val client_created_at: Long
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getPendingAttempts(): List<com.example.quizapp.data.entity.Attempt> = withContext(Dispatchers.IO) {
        db.attemptDao().getUnsyncedAttempts()
    }

    suspend fun markAttemptsAsSynced(ids: List<Long>) = withContext(Dispatchers.IO) {
        db.attemptDao().markSynced(ids)
    }

    suspend fun syncFeedback(baseUrl: String): Int = withContext(Dispatchers.IO) {
        val feedbackDao = db.feedbackDao()
        val unsent = feedbackDao.getUnsent()
        if (unsent.isEmpty()) return@withContext 0

        val payloadList = unsent.map {
            FeedbackPayload(
                client_uuid = it.clientUuid,
                question_uuid = it.questionUuid,
                question_text = it.questionText,
                selected_answer = it.selectedAnswer,
                correct_answer = it.correctAnswer,
                language = it.language,
                domain_slug = it.domainSlug,
                article_slug = it.articleSlug,
                reason_code = it.reasonCode,
                comment = it.comment,
                app_version = it.appVersion,
                locale = it.locale,
                client_created_at = it.createdAt
            )
        }

        val jsonBody = json.encodeToString(ListSerializer(FeedbackPayload.serializer()), payloadList)

        val url = URL("$baseUrl/api/feedback")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connectTimeout = 5000
            readTimeout = 10000
        }

        try {
            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            when (classifyResponse(conn.responseCode)) {
                is SyncOutcome.Success -> {
                    Log.d("SyncRepository", "Successfully uploaded ${unsent.size} feedback items.")
                    feedbackDao.markSent(unsent.map { it.id })
                    unsent.size
                }
                is SyncOutcome.PermanentFailure -> {
                    Log.e("SyncRepository", "Permanent failure uploading feedback: HTTP ${conn.responseCode}, items preserved")
                    0
                }
                is SyncOutcome.TransientFailure -> {
                    Log.e("SyncRepository", "Transient failure uploading feedback: HTTP ${conn.responseCode}")
                    throw IOException("HTTP ${conn.responseCode}")
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}

internal sealed interface SyncOutcome {
    data object Success : SyncOutcome
    data object PermanentFailure : SyncOutcome
    data object TransientFailure : SyncOutcome
}

internal fun classifyResponse(responseCode: Int): SyncOutcome {
    return when (responseCode) {
        200 -> SyncOutcome.Success
        in 400..499 -> SyncOutcome.PermanentFailure
        else -> SyncOutcome.TransientFailure
    }
}
