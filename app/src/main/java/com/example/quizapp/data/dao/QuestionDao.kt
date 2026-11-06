package com.example.quizapp.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.example.quizapp.data.entity.Distractor
import com.example.quizapp.data.entity.Question
import com.example.quizapp.data.entity.QuestionTranslation
import com.example.quizapp.engine.QuestionType
import com.example.quizapp.engine.QuizQuestion
import com.example.quizapp.engine.parseDistractorTokens
import com.example.quizapp.engine.parseSolution
import com.example.quizapp.engine.parseTokens

@Dao
interface QuestionDao {

    // --- Question ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(question: Question): Long

    @Query("SELECT * FROM questions WHERE domainId = :domainId")
    suspend fun getByDomain(domainId: Long): List<Question>

    @Query("SELECT COUNT(*) FROM questions WHERE domainId = :domainId")
    suspend fun countByDomain(domainId: Long): Int

    // --- QuestionTranslation ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTranslation(translation: QuestionTranslation): Long

    @Query("""
        SELECT
            q.id            AS questionId,
            q.uuid          AS uuid,
            q.domainId      AS domainId,
            d.slug          AS domainSlug,
            q.difficulty    AS difficulty,
            q.type          AS type,
            qt.text         AS text,
            qt.answer       AS answer,
            qt.distractors  AS distractors,
            qt.answerWhy    AS answerWhy,
            qt.relatedArticleSlug AS relatedArticleSlug,
            qt.payload_json AS payloadJson
        FROM questions q
        INNER JOIN domains d ON d.id = q.domainId
        INNER JOIN question_translations qt
            ON qt.questionId = q.id AND qt.language = :language
        ORDER BY q.id
    """)
    suspend fun getQuizQuestionRows(language: String): List<QuizQuestionRow>

    @Query("""
        SELECT ht.text FROM hints h
        INNER JOIN hint_translations ht
            ON ht.hintId = h.id AND ht.language = :language
        WHERE h.questionId = :questionId
        ORDER BY h.`order`
    """)
    suspend fun getHintTexts(questionId: Long, language: String): List<String>

    @Query("""
        SELECT COUNT(*) FROM question_translations qt
        WHERE qt.language = :language AND qt.relatedArticleSlug = :articleSlug
    """)
    suspend fun countByArticleSlug(language: String, articleSlug: String): Int

    @Transaction
    suspend fun getQuizQuestions(language: String): List<QuizQuestion> {
        return getQuizQuestions(language, null, null, null)
    }

    @Transaction
    suspend fun getQuizQuestions(
        language: String,
        domainIds: List<Long>?,
        difficulties: Set<Int>?,
        articleSlug: String? = null,
    ): List<QuizQuestion> {
        val rows = when {
            articleSlug != null -> {
                when {
                    domainIds.isNullOrEmpty() && difficulties.isNullOrEmpty() -> getQuizQuestionRowsByArticleSlug(language, articleSlug)
                    domainIds.isNullOrEmpty() -> getQuizQuestionRowsByDifficultiesAndArticleSlug(language, difficulties!!.toList(), articleSlug)
                    difficulties.isNullOrEmpty() -> getQuizQuestionRowsByDomainsAndArticleSlug(language, domainIds, articleSlug)
                    else -> getQuizQuestionRowsByDomainsAndDifficultiesAndArticleSlug(language, domainIds, difficulties.toList(), articleSlug)
                }
            }
            domainIds.isNullOrEmpty() && difficulties.isNullOrEmpty() -> getQuizQuestionRows(language)
            domainIds.isNullOrEmpty() -> getQuizQuestionRowsByDifficulties(language, difficulties!!.toList())
            difficulties.isNullOrEmpty() -> getQuizQuestionRowsByDomains(language, domainIds)
            else -> getQuizQuestionRowsByDomainsAndDifficulties(language, domainIds, difficulties.toList())
        }
        return rows.map { row ->
            row.toQuizQuestion(getHintTexts(row.questionId, language))
        }
    }

    @Query("""
        SELECT
            q.id            AS questionId,
            q.uuid          AS uuid,
            q.domainId      AS domainId,
            d.slug          AS domainSlug,
            q.difficulty    AS difficulty,
            q.type          AS type,
            qt.text         AS text,
            qt.answer       AS answer,
            qt.distractors  AS distractors,
            qt.answerWhy    AS answerWhy,
            qt.relatedArticleSlug AS relatedArticleSlug,
            qt.payload_json AS payloadJson
        FROM questions q
        INNER JOIN domains d ON d.id = q.domainId
        INNER JOIN question_translations qt
            ON qt.questionId = q.id AND qt.language = :language
        WHERE q.domainId IN (:domainIds)
        ORDER BY q.id
    """)
    suspend fun getQuizQuestionRowsByDomains(
        language: String,
        domainIds: List<Long>,
    ): List<QuizQuestionRow>

    @Query("""
        SELECT
            q.id            AS questionId,
            q.uuid          AS uuid,
            q.domainId      AS domainId,
            d.slug          AS domainSlug,
            q.difficulty    AS difficulty,
            q.type          AS type,
            qt.text         AS text,
            qt.answer       AS answer,
            qt.distractors  AS distractors,
            qt.answerWhy    AS answerWhy,
            qt.relatedArticleSlug AS relatedArticleSlug,
            qt.payload_json AS payloadJson
        FROM questions q
        INNER JOIN domains d ON d.id = q.domainId
        INNER JOIN question_translations qt
            ON qt.questionId = q.id AND qt.language = :language
        WHERE q.difficulty IN (:difficulties)
        ORDER BY q.id
    """)
    suspend fun getQuizQuestionRowsByDifficulties(
        language: String,
        difficulties: List<Int>,
    ): List<QuizQuestionRow>

    @Query("""
        SELECT
            q.id            AS questionId,
            q.uuid          AS uuid,
            q.domainId      AS domainId,
            d.slug          AS domainSlug,
            q.difficulty    AS difficulty,
            q.type          AS type,
            qt.text         AS text,
            qt.answer       AS answer,
            qt.distractors  AS distractors,
            qt.answerWhy    AS answerWhy,
            qt.relatedArticleSlug AS relatedArticleSlug,
            qt.payload_json AS payloadJson
        FROM questions q
        INNER JOIN domains d ON d.id = q.domainId
        INNER JOIN question_translations qt
            ON qt.questionId = q.id AND qt.language = :language
        WHERE q.domainId IN (:domainIds) AND q.difficulty IN (:difficulties)
        ORDER BY q.id
    """)
    suspend fun getQuizQuestionRowsByDomainsAndDifficulties(
        language: String,
        domainIds: List<Long>,
        difficulties: List<Int>,
    ): List<QuizQuestionRow>

    @Query("""
        SELECT
            q.id            AS questionId,
            q.uuid          AS uuid,
            q.domainId      AS domainId,
            d.slug          AS domainSlug,
            q.difficulty    AS difficulty,
            q.type          AS type,
            qt.text         AS text,
            qt.answer       AS answer,
            qt.distractors  AS distractors,
            qt.answerWhy    AS answerWhy,
            qt.relatedArticleSlug AS relatedArticleSlug,
            qt.payload_json AS payloadJson
        FROM questions q
        INNER JOIN domains d ON d.id = q.domainId
        INNER JOIN question_translations qt
            ON qt.questionId = q.id AND qt.language = :language
        WHERE qt.relatedArticleSlug = :articleSlug
        ORDER BY q.id
    """)
    suspend fun getQuizQuestionRowsByArticleSlug(
        language: String,
        articleSlug: String,
    ): List<QuizQuestionRow>

    @Query("""
        SELECT
            q.id            AS questionId,
            q.uuid          AS uuid,
            q.domainId      AS domainId,
            d.slug          AS domainSlug,
            q.difficulty    AS difficulty,
            q.type          AS type,
            qt.text         AS text,
            qt.answer       AS answer,
            qt.distractors  AS distractors,
            qt.answerWhy    AS answerWhy,
            qt.relatedArticleSlug AS relatedArticleSlug,
            qt.payload_json AS payloadJson
        FROM questions q
        INNER JOIN domains d ON d.id = q.domainId
        INNER JOIN question_translations qt
            ON qt.questionId = q.id AND qt.language = :language
        WHERE q.domainId IN (:domainIds) AND qt.relatedArticleSlug = :articleSlug
        ORDER BY q.id
    """)
    suspend fun getQuizQuestionRowsByDomainsAndArticleSlug(
        language: String,
        domainIds: List<Long>,
        articleSlug: String,
    ): List<QuizQuestionRow>

    @Query("""
        SELECT
            q.id            AS questionId,
            q.uuid          AS uuid,
            q.domainId      AS domainId,
            d.slug          AS domainSlug,
            q.difficulty    AS difficulty,
            q.type          AS type,
            qt.text         AS text,
            qt.answer       AS answer,
            qt.distractors  AS distractors,
            qt.answerWhy    AS answerWhy,
            qt.relatedArticleSlug AS relatedArticleSlug,
            qt.payload_json AS payloadJson
        FROM questions q
        INNER JOIN domains d ON d.id = q.domainId
        INNER JOIN question_translations qt
            ON qt.questionId = q.id AND qt.language = :language
        WHERE q.difficulty IN (:difficulties) AND qt.relatedArticleSlug = :articleSlug
        ORDER BY q.id
    """)
    suspend fun getQuizQuestionRowsByDifficultiesAndArticleSlug(
        language: String,
        difficulties: List<Int>,
        articleSlug: String,
    ): List<QuizQuestionRow>

    @Query("""
        SELECT
            q.id            AS questionId,
            q.uuid          AS uuid,
            q.domainId      AS domainId,
            d.slug          AS domainSlug,
            q.difficulty    AS difficulty,
            q.type          AS type,
            qt.text         AS text,
            qt.answer       AS answer,
            qt.distractors  AS distractors,
            qt.answerWhy    AS answerWhy,
            qt.relatedArticleSlug AS relatedArticleSlug,
            qt.payload_json AS payloadJson
        FROM questions q
        INNER JOIN domains d ON d.id = q.domainId
        INNER JOIN question_translations qt
            ON qt.questionId = q.id AND qt.language = :language
        WHERE q.domainId IN (:domainIds) AND q.difficulty IN (:difficulties) AND qt.relatedArticleSlug = :articleSlug
        ORDER BY q.id
    """)
    suspend fun getQuizQuestionRowsByDomainsAndDifficultiesAndArticleSlug(
        language: String,
        domainIds: List<Long>,
        difficulties: List<Int>,
        articleSlug: String,
    ): List<QuizQuestionRow>

    @Query("""
        SELECT COUNT(*) FROM question_translations
        WHERE language = :language
    """)
    suspend fun countTranslationsByLanguage(language: String): Int

    @Query("""
        SELECT COUNT(*) FROM question_translations qt
        INNER JOIN questions q ON q.id = qt.questionId
        WHERE q.domainId = :domainId AND qt.language = :language
    """)
    suspend fun countTranslationsByDomain(domainId: Long, language: String): Int

    @Query("DELETE FROM question_translations")
    suspend fun deleteAllTranslations()

    @Query("SELECT * FROM questions WHERE domainId = :domainId ORDER BY id")
    suspend fun getByDomainOrdered(domainId: Long): List<Question>

    @Query("SELECT * FROM questions WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Question?

    @Query("""
        SELECT DISTINCT qt.language
        FROM question_translations qt
        INNER JOIN questions q ON q.id = qt.questionId
    """)
    suspend fun getAllLanguagesWithQuestions(): List<String>

    @Query("""
        SELECT DISTINCT qt.language
        FROM question_translations qt
        INNER JOIN questions q ON q.id = qt.questionId
        WHERE q.domainId IN (:domainIds)
    """)
    suspend fun getLanguagesWithQuestionsForDomainsQuery(domainIds: List<Long>): List<String>

    suspend fun getLanguagesWithQuestionsForDomains(domainIds: List<Long>): List<String> {
        return if (domainIds.isEmpty()) {
            getAllLanguagesWithQuestions()
        } else {
            getLanguagesWithQuestionsForDomainsQuery(domainIds)
        }
    }

    @Transaction
    suspend fun getQuizQuestionsByDomains(
        language: String,
        domainIds: List<Long>,
    ): List<QuizQuestion> {
        val rows = getQuizQuestionRowsByDomains(language, domainIds)
        return rows.map { row ->
            row.toQuizQuestion(getHintTexts(row.questionId, language))
        }
    }
}

private fun QuizQuestionRow.toQuizQuestion(hints: List<String>): QuizQuestion = QuizQuestion(
    id = questionId,
    uuid = uuid,
    domainId = domainId,
    domainSlug = domainSlug,
    difficulty = difficulty,
    text = text,
    correctAnswer = answer,
    distractorPool = distractors,
    hints = hints,
    answerWhy = answerWhy,
    relatedArticleSlug = relatedArticleSlug,
    type = if (type == "construct") QuestionType.CONSTRUCT else QuestionType.SELECT,
    tokens = parseTokens(payloadJson),
    solution = parseSolution(payloadJson),
    distractorTokens = parseDistractorTokens(payloadJson),
)

data class QuizQuestionRow(
    val questionId: Long,
    val uuid: String,
    val domainId: Long,
    val domainSlug: String,
    val difficulty: Int,
    val type: String,
    val text: String,
    val answer: String,
    val distractors: List<Distractor>,
    val answerWhy: String?,
    val relatedArticleSlug: String?,
    val payloadJson: String?,
)
