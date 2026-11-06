package com.example.quizapp.data.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "hints",
    foreignKeys = [
        ForeignKey(
            entity = Question::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("questionId"),
        Index(value = ["questionId", "order"], unique = true),
    ],
)
data class Hint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val order: Int,
)

@Entity(
    tableName = "hint_translations",
    foreignKeys = [
        ForeignKey(
            entity = Hint::class,
            parentColumns = ["id"],
            childColumns = ["hintId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["hintId", "language"], unique = true)],
)
data class HintTranslation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hintId: Long,
    val language: String,
    val text: String,
)