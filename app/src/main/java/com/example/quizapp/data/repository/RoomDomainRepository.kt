package com.example.quizapp.data.repository

import com.example.quizapp.data.dao.DomainDao
import com.example.quizapp.data.dao.DomainWithTranslation

class RoomDomainRepository(private val dao: DomainDao) : DomainRepository {

    override suspend fun getAll(language: String): List<DomainWithTranslation> {
        val domains = dao.getAllWithTranslation(language)
        return domains.map { domain ->
            val langs = dao.getLanguagesForDomain(domain.id)
            domain.copy(availableLanguages = langs)
        }
    }
}
