package com.example.quizapp.data.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "attempts",
    indices = [Index("playerId"), Index("questionUuid"), Index("domainSlug"), Index("answeredAt"), Index("syncedAt")],
)
data class Attempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val syncedAt: Long? = null,
    val playerId: Long,
    val questionUuid: String,
    val domainSlug: String,
    val selectedText: String,
    val isCorrect: Boolean,
    val hintsUsed: Int = 0,
    val language: String,
    val answeredAt: Long = System.currentTimeMillis(),
)
