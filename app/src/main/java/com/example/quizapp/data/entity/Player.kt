package com.example.quizapp.data.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "players",
    indices = [Index(value = ["name"], unique = true)],
)
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val remoteUuid: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val contentLanguages: List<String> = listOf("en"),
    val primaryContentLanguage: String = "en",
    val preferredDomainSlugs: List<String> = emptyList(),
    val selectedDomainSlugs: List<String>? = null,
)
