package com.example.quizapp.engine

/**
 * Core state machine managing quiz session flow, question navigation, scoring, hints, and answer evaluation.
 *
 * @param questions The initial list of questions for the quiz session.
 */
class QuizEngine(questions: List<QuizQuestion>) {

    private var initialSession = QuizSession(questions = questions)

    var session: QuizSession = initialSession
        private set

    fun replaceQuestions(newQuestions: List<QuizQuestion>): Boolean {
        if (newQuestions.isEmpty()) return false
        val newIndex = session.currentIndex.coerceAtMost(newQuestions.size - 1)
        initialSession = initialSession.copy(questions = newQuestions)
        session = session.copy(
            questions = newQuestions,
            currentIndex = newIndex,
            feedback = null,
            finished = false,
        )
        return true
    }

    fun seekTo(uuid: String): Boolean {
        val index = session.questions.indexOfFirst { it.uuid == uuid }
        if (index < 0) return false
        session = session.copy(currentIndex = index, finished = false)
        return true
    }

    /**
     * Processes a multiple-choice answer selection for the current question.
     *
     * @param selectedAnswer The answer text selected by the user.
     * @return An [AnswerResult] containing evaluation details, or `null` if the session is finished,
     *         feedback is already active, or the selected answer is invalid.
     */
    fun selectAnswer(selectedAnswer: String): AnswerResult? {
        val s = session

        if (s.finished) return null
        if (s.feedback != null) return null

        val question = s.currentQuestion ?: return null
        val distractorTexts = question.distractorPool.map { it.text }
        if (selectedAnswer !in distractorTexts && selectedAnswer != question.correctAnswer) {
            return null
        }

        val isCorrect = selectedAnswer == question.correctAnswer
        val hintsUsed = s.revealedHintsForCurrent

        val explanation = if (isCorrect) {
            question.answerWhy
        } else {
            question.distractorPool.firstOrNull { it.text == selectedAnswer }?.why
        }

        session = s.copy(
            score = s.score + if (isCorrect) 1 else 0,
            feedback = AnswerFeedback(selectedAnswer, isCorrect, explanation),
        )

        return AnswerResult(
            questionId = question.id,
            selectedAnswer = selectedAnswer,
            isCorrect = isCorrect,
            hintsUsed = hintsUsed,
        )
    }

    /**
     * Validates and processes submitted token IDs for a construction/ordering question.
     *
     * @param selectedIds The ordered list of token IDs selected by the user.
     * @return An [AnswerResult] containing evaluation details, or `null` if finished, feedback is active,
     *         or the current question is not of type [QuestionType.CONSTRUCT] or contains unknown token IDs.
     */
    fun submitConstruct(selectedIds: List<Int>): AnswerResult? {
        val s = session
        if (s.finished) return null
        if (s.feedback != null) return null

        val question = s.currentQuestion ?: return null
        if (question.type != QuestionType.CONSTRUCT) return null

        // Validate that every id refers to a known token (canonical or distractor).
        val knownIds = question.tokens.map { it.id }.toSet() +
                       question.distractorTokens.map { it.id }.toSet()
        if (!selectedIds.all { it in knownIds }) return null

        val isCorrect = selectedIds == question.solution
        val displayText = selectedIds.joinToString(" ") { id ->
            (question.tokens + question.distractorTokens).firstOrNull { it.id == id }?.text ?: ""
        }
        val explanation = if (isCorrect) question.answerWhy else null

        session = s.copy(
            score = s.score + if (isCorrect) 1 else 0,
            feedback = AnswerFeedback(displayText, isCorrect, explanation),
        )

        return AnswerResult(
            questionId = question.id,
            selectedAnswer = displayText,
            isCorrect = isCorrect,
            hintsUsed = s.revealedHintsForCurrent,
        )
    }

    fun revealHint(): String? {
        val s = session
        if (s.finished) return null
        if (s.feedback != null) return null

        val question = s.currentQuestion ?: return null
        val hints = s.currentHints
        val current = s.revealedHintsForCurrent
        if (current >= hints.size) return null

        session = s.copy(
            revealedHints = s.revealedHints + (question.id to (current + 1)),
        )
        return hints[current]
    }

    /**
     * Advances the session to the next question if feedback has been given.
     * Sets `finished = true` if the last question in the session has been reached.
     *
     * @return `true` if advanced successfully, `false` if feedback is missing.
     */
    fun advance(): Boolean {
        val s = session
        if (s.feedback == null) return false

        val nextIndex = s.currentIndex + 1
        val willFinish = nextIndex >= s.questions.size

        session = s.copy(
            currentIndex = if (willFinish) s.currentIndex else nextIndex,
            feedback = null,
            finished = willFinish,
        )
        return true
    }

    fun clearFeedback() {
        session = session.copy(feedback = null)
    }

    fun setFeedback(feedback: AnswerFeedback) {
        session = session.copy(feedback = feedback)
    }

    fun reset() {
        session = initialSession
    }
}
