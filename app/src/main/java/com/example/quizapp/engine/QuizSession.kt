package com.example.quizapp.engine

data class QuizSession(
    val questions: List<QuizQuestion>,
    val currentIndex: Int = 0,
    val score: Int = 0,
    val revealedHints: Map<Long, Int> = emptyMap(),
    val feedback: AnswerFeedback? = null,
    val finished: Boolean = false,
) {
    val currentQuestion: QuizQuestion?
        get() = questions.getOrNull(currentIndex)

    val currentHints: List<String>
        get() = currentQuestion?.hints.orEmpty()

    val revealedHintsForCurrent: Int
        get() = currentQuestion?.let { revealedHints[it.id] ?: 0 } ?: 0
}

data class AnswerFeedback(
    val selectedAnswer: String,
    val isCorrect: Boolean,
    val explanation: String? = null,
)

data class AnswerResult(
    val questionId: Long,
    val selectedAnswer: String,
    val isCorrect: Boolean,
    val hintsUsed: Int,
)
