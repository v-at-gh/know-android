package com.example.quizapp.data.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

/**
 * A user-submitted report about a bad question. Persisted locally until sent.
 *
 * The snapshot fields (questionText, correctAnswer, selectedAnswer) are
 * duplicated rather than joined via questionUuid because the underlying
 * question may be edited or regenerated before this report is processed.
 */
@Entity(
    tableName = "feedback",
    indices = [Index("sentAt"), Index("questionUuid"), Index("clientUuid")],
)
data class Feedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientUuid: String = UUID.randomUUID().toString(),
    val questionUuid: String?,
    val questionText: String,
    val selectedAnswer: String,
    val correctAnswer: String,
    val language: String,
    val domainSlug: String,
    val articleSlug: String?,
    val reasonCode: String,          // "wrong_answer" | "typo" | "bad_translation" | "unclear_hint" | "other"
    val comment: String,
    val appVersion: String,
    val locale: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,        // null = pending
)
