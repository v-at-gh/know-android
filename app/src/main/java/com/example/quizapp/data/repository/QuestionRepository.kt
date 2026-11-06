package com.example.quizapp.data.repository

import com.example.quizapp.engine.QuizQuestion

interface QuestionRepository {
    suspend fun getQuizQuestions(
        language: String,
        domainIds: Set<Long>?,
        difficulties: Set<Int>? = null,
        articleSlug: String? = null,
    ): List<QuizQuestion>

    suspend fun getLanguagesWithContentForDomains(
        playerLanguages: List<String>,
        domainIds: Set<Long>,
    ): Set<String>

    suspend fun countByArticleSlug(language: String, articleSlug: String): Int

    suspend fun getAllAvailableLanguages(): List<String>
}
