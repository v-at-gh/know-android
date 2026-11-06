package com.example.quizapp.data

data class PlayerStats(
    val total: Int,
    val correct: Int,
    val hintsUsed: Int,
)

data class DomainStats(
    val domainSlug: String,
    val domainName: String,
    val total: Int,
    val correct: Int,
)
