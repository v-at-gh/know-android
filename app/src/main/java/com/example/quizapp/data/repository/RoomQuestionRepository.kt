package com.example.quizapp.data.repository

import com.example.quizapp.data.dao.QuestionDao
import com.example.quizapp.engine.QuizQuestion

class RoomQuestionRepository(private val dao: QuestionDao) : QuestionRepository {

    override suspend fun getQuizQuestions(
        language: String,
        domainIds: Set<Long>?,
        difficulties: Set<Int>?,
        articleSlug: String?,
    ): List<QuizQuestion> {
        return dao.getQuizQuestions(language, domainIds?.toList(), difficulties, articleSlug)
    }

    override suspend fun getLanguagesWithContentForDomains(
        playerLanguages: List<String>,
        domainIds: Set<Long>,
    ): Set<String> {
        val availableInDb = dao.getLanguagesWithQuestionsForDomains(domainIds.toList()).toSet()
        return playerLanguages.filterTo(mutableSetOf()) { it in availableInDb }
    }

    override suspend fun countByArticleSlug(language: String, articleSlug: String): Int {
        return dao.countByArticleSlug(language, articleSlug)
    }

    override suspend fun getAllAvailableLanguages(): List<String> {
        return dao.getAllLanguagesWithQuestions()
    }
}
