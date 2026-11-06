package com.example.quizapp.data.entity

import androidx.room3.Entity
import androidx.room3.Fts4
import androidx.room3.PrimaryKey

@Fts4
@Entity(tableName = "article_fts")
data class ArticleFts(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val lang: String,
    val domainSlug: String,
    val slug: String,
    val title: String,
    val content: String
)
