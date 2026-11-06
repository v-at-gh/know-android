package com.example.quizapp.data.repository

import com.example.quizapp.data.dao.DomainWithTranslation

interface DomainRepository {
    suspend fun getAll(language: String): List<DomainWithTranslation>
}