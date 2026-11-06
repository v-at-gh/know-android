package com.example.quizapp.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = Domain::class,
            parentColumns = ["id"],
            childColumns = ["domainId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("domainId"),
        Index(value = ["uuid"], unique = true),
    ],
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val domainId: Long,
    val difficulty: Int = 1,
    val type: String = "select",
)

@Entity(
    tableName = "question_translations",
    foreignKeys = [
        ForeignKey(
            entity = Question::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["questionId", "language"], unique = true)],
)
data class QuestionTranslation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val language: String,
    val text: String,
    val answer: String,
    val distractors: List<Distractor>,
    val answerWhy: String? = null,
    val relatedArticleSlug: String? = null,
    @ColumnInfo(name = "payload_json") val payloadJson: String? = null,
)
