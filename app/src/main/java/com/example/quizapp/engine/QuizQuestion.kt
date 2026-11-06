package com.example.quizapp.engine

import com.example.quizapp.data.entity.Distractor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Defines the type of quiz question.
 */
enum class QuestionType { 
    /** Standard multiple-choice question with text options. */
    SELECT, 
    /** Word/token ordering or construction question. */
    CONSTRUCT 
}

data class ConstructToken(val id: Int, val text: String)

/**
 * Represents a quiz question data model.
 *
 * @property id Unique numeric identifier.
 * @property uuid Unique string identifier for tracking attempts and sync.
 * @property domainId ID of the knowledge domain this question belongs to.
 * @property domainSlug Slug of the knowledge domain.
 * @property difficulty Difficulty rating of the question.
 * @property text The question prompt text.
 * @property correctAnswer The correct answer text (for SELECT questions) or joined token solution representation.
 * @property distractorPool Pool of incorrect distractor answers.
 * @property hints List of progressive hints available for this question.
 * @property answerWhy Explanation text explaining why the answer is correct.
 * @property relatedArticleSlug Slug of a related knowledge base article.
 * @property type The [QuestionType] of this question ([QuestionType.SELECT] or [QuestionType.CONSTRUCT]).
 * @property tokens The canonical list of tokens for construct questions.
 * @property solution Ordered list of token IDs representing the correct solution sequence.
 * @property distractorTokens Additional distractor tokens included for construct questions.
 */
data class QuizQuestion(
    val id: Long,
    val uuid: String = UUID.randomUUID().toString(),
    val domainId: Long,
    val domainSlug: String = "",
    val difficulty: Int,
    val text: String,
    val correctAnswer: String,
    val distractorPool: List<Distractor>,
    val hints: List<String>,
    val answerWhy: String? = null,
    val relatedArticleSlug: String? = null,
    val type: QuestionType = QuestionType.SELECT,
    val tokens: List<ConstructToken> = emptyList(),
    val solution: List<Int> = emptyList(),
    val distractorTokens: List<ConstructToken> = emptyList(),
)

/**
 * A question is playable iff the engine can accept an answer from the user
 * and score it. Malformed payloads (missing tokens, out-of-range solution
 * indices, empty select questions) must fail this check so QuizViewModel
 * can drop them before the engine ever sees them.
 */
val QuizQuestion.isPlayable: Boolean
    get() = when (type) {
        QuestionType.SELECT ->
            correctAnswer.isNotBlank() && distractorPool.isNotEmpty()
        QuestionType.CONSTRUCT ->
            tokens.size >= 2 &&
                solution.isNotEmpty() &&
                solution.size == tokens.size &&
                solution.all { it in tokens.indices } &&
                solution.toSet().size == solution.size
    }

@Serializable
private data class TokenPayload(val text: String)

@Serializable
private data class ConstructPayload(
    val tokens: List<TokenPayload> = emptyList(),
    val solution: List<Int> = emptyList(),
    val distractorTokens: List<TokenPayload> = emptyList()
)

private val jsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

internal fun parseTokens(json: String?): List<ConstructToken> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val payload = jsonParser.decodeFromString<ConstructPayload>(json)
        payload.tokens.mapIndexed { index, token -> ConstructToken(id = index, text = token.text) }
    } catch (t: Throwable) {
        emptyList()
    }
}

internal fun parseSolution(json: String?): List<Int> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<ConstructPayload>(json).solution
    } catch (t: Throwable) {
        emptyList()
    }
}

internal fun parseDistractorTokens(json: String?): List<ConstructToken> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val payload = jsonParser.decodeFromString<ConstructPayload>(json)
        val baseId = payload.tokens.size
        payload.distractorTokens.mapIndexed { index, token -> ConstructToken(id = baseId + index, text = token.text) }
    } catch (t: Throwable) {
        emptyList()
    }
}
