package com.example.quizapp.data.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "domains",
    indices = [Index(value = ["slug"], unique = true)],
)
data class Domain(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
)

@Entity(
    tableName = "domain_translations",
    foreignKeys = [
        ForeignKey(
            entity = Domain::class,
            parentColumns = ["id"],
            childColumns = ["domainId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["domainId", "language"], unique = true)],
)
data class DomainTranslation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domainId: Long,
    val language: String,
    val name: String,
    val description: String,
)