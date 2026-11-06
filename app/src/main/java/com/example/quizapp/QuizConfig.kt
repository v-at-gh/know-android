package com.example.quizapp

/**
 * Configuration parameters defining the scope and filtering options for a quiz session.
 *
 * @property domainIds Set of domain IDs included in the quiz.
 * @property difficulties Set of difficulty levels included, or null for all.
 * @property questionLimit Maximum number of questions in the session, or null for no limit.
 * @property articleSlug Optional article slug associated with a contextual article quiz, or null.
 */
data class QuizConfig(
    val domainIds: Set<Long> = emptySet(),
    val difficulties: Set<Int>? = null,   // null = all
    val questionLimit: Int? = null,       // null = no limit
    val articleSlug: String? = null,      // null = all
)
