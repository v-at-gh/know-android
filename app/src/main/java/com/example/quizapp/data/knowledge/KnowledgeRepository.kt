package com.example.quizapp.data.knowledge

interface KnowledgeRepository {
    suspend fun getIndex(language: String): List<ArticleMeta>
    suspend fun getArticle(language: String, meta: ArticleMeta): Article?
    suspend fun getLanguagesWithArticle(
        playerLanguages: List<String>,
        slug: String,
    ): Set<String>
}