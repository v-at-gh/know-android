package com.example.quizapp.data.knowledge

data class ArticleMeta(
    val uuid: String,
    val slug: String,
    val domainSlug: String,
    val title: String,
    val description: String,
    val path: String,
    val order: Int,
)

data class Article(
    val meta: ArticleMeta,
    val content: String,
)
