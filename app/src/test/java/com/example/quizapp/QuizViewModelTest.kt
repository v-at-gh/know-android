package com.example.quizapp

import com.example.quizapp.data.ContentDatabase
import com.example.quizapp.data.DomainStats
import com.example.quizapp.data.PlayerStats
import com.example.quizapp.data.entity.Attempt
import com.example.quizapp.data.entity.Distractor
import com.example.quizapp.data.entity.Player
import com.example.quizapp.data.importer.ContentBootstrapper
import com.example.quizapp.data.repository.AttemptRepository
import com.example.quizapp.data.repository.PlayerRepository
import com.example.quizapp.data.repository.QuestionRepository
import com.example.quizapp.engine.QuizQuestion
import com.example.quizapp.ui.screens.quiz.QuizViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeQuestionRepository : QuestionRepository {
    val questionsMap = mutableMapOf<Pair<String, Set<Long>>, List<QuizQuestion>>()

    override suspend fun getQuizQuestions(
        language: String,
        domainIds: Set<Long>?,
        difficulties: Set<Int>?,
        articleSlug: String?,
    ): List<QuizQuestion> {
        val base = questionsMap[language to (domainIds ?: emptySet())].orEmpty()
        val filteredByArticle = if (articleSlug != null) {
            base.filter { it.relatedArticleSlug == articleSlug }
        } else {
            base
        }
        return if (difficulties.isNullOrEmpty()) {
            filteredByArticle
        } else {
            filteredByArticle.filter { it.difficulty in difficulties }
        }
    }

    override suspend fun countByArticleSlug(language: String, articleSlug: String): Int {
        return questionsMap.values.flatten().count { it.relatedArticleSlug == articleSlug }
    }

    override suspend fun getLanguagesWithContentForDomains(
        playerLanguages: List<String>,
        domainIds: Set<Long>,
    ): Set<String> {
        val langsWithQuestions = questionsMap.entries
            .filter { (key, questions) ->
                val (_, domains) = key
                questions.isNotEmpty() && (domainIds.isEmpty() || domains.any { it in domainIds })
            }
            .map { it.key.first }
            .toSet()
        return playerLanguages.filterTo(mutableSetOf()) { it in langsWithQuestions }
    }

    override suspend fun getAllAvailableLanguages(): List<String> {
        return questionsMap.keys.map { it.first }.distinct()
    }
}

class FakePlayerRepository : PlayerRepository {
    var player: Player? = Player(
        id = 1L,
        name = "TestPlayer",
        contentLanguages = listOf("en", "fr"),
    )

    override fun observeAll(): Flow<List<Player>> = flowOf(listOfNotNull(player))
    override fun observeById(id: Long): Flow<Player?> = flowOf(player)
    override suspend fun getById(id: Long): Player? = player
    override suspend fun create(player: Player): Long = player.id

    override suspend fun updatePreferredDomains(playerId: Long, slugs: List<String>) {}
    override suspend fun updatePrimaryLanguage(playerId: Long, lang: String) {}
    override suspend fun updateContentLanguages(playerId: Long, langs: List<String>) {}
    override suspend fun updateSelectedDomains(playerId: Long, slugs: List<String>?) {}
}

class FakeAttemptRepository : AttemptRepository {
    val attempts = mutableListOf<Attempt>()

    override suspend fun insert(attempt: Attempt): Long {
        attempts.add(attempt)
        return attempts.size.toLong()
    }

    override fun observeByPlayer(playerId: Long): Flow<List<Attempt>> = flowOf(attempts)
    override suspend fun getStatsForPlayer(playerId: Long): PlayerStats = PlayerStats(0, 0, 0)
    override suspend fun getStatsByDomain(playerId: Long): List<DomainStats> = emptyList()
}

class TestQuizApplication(
    val fakeQuestionRepo: FakeQuestionRepository,
    val fakePlayerRepo: FakePlayerRepository,
    val fakeAttemptRepo: FakeAttemptRepository,
    var currentLang: String = "en",
) : QuizApplication() {
    override val questionRepository: QuestionRepository get() = fakeQuestionRepo
    override val playerRepository: PlayerRepository get() = fakePlayerRepo
    override val attemptRepository: AttemptRepository get() = fakeAttemptRepo
    override val contentLanguage: String get() = currentLang
    override val contentBootstrapper: ContentBootstrapper by lazy {
        val dummyDb = object : ContentDatabase() {
            override fun domainDao() = throw UnsupportedOperationException()
            override fun questionDao() = throw UnsupportedOperationException()
            override fun hintDao() = throw UnsupportedOperationException()
            override fun campaignDao() = throw UnsupportedOperationException()
            override fun articleFtsDao() = throw UnsupportedOperationException()
            override fun createInvalidationTracker() = throw UnsupportedOperationException()
            override suspend fun clearAllTables() {}
        }
        object : ContentBootstrapper(this, dummyDb) {
            override val isIdle: StateFlow<Boolean> = MutableStateFlow(true)
            override val importingDomainIds: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var questionRepo: FakeQuestionRepository
    private lateinit var playerRepo: FakePlayerRepository
    private lateinit var attemptRepo: FakeAttemptRepository
    private lateinit var app: TestQuizApplication
    private lateinit var viewModel: QuizViewModel

    private fun createQuestion(id: Long, domainId: Long, lang: String = "en"): QuizQuestion {
        return QuizQuestion(
            id = id,
            uuid = "uuid-$id",
            domainId = domainId,
            difficulty = 1,
            text = "Question $id ($lang)",
            correctAnswer = "Correct $id",
            distractorPool = listOf(
                Distractor("Wrong A $id"),
                Distractor("Wrong B $id"),
                Distractor("Wrong C $id"),
            ),
            hints = listOf("Hint $id"),
        )
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        questionRepo = FakeQuestionRepository()
        playerRepo = FakePlayerRepository()
        attemptRepo = FakeAttemptRepository()

        // Set up questions for Domain 1 in EN and FR
        questionRepo.questionsMap["en" to setOf(1L)] = listOf(
            createQuestion(101L, 1L, "en"),
            createQuestion(102L, 1L, "en"),
        )
        questionRepo.questionsMap["fr" to setOf(1L)] = listOf(
            createQuestion(101L, 1L, "fr"),
            createQuestion(102L, 1L, "fr"),
        )

        // Set up questions for Domain 2 in EN
        questionRepo.questionsMap["en" to setOf(2L)] = listOf(
            createQuestion(201L, 2L, "en"),
            createQuestion(202L, 2L, "en"),
        )

        // Domain 99 has 0 questions
        questionRepo.questionsMap["en" to setOf(99L)] = emptyList()

        app = TestQuizApplication(questionRepo, playerRepo, attemptRepo, "en")
        viewModel = QuizViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful load populates state and clears errors`() {
        viewModel.load(1L, setOf(1L))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.errorResId)
        assertEquals(2, state.questions.size)
        assertEquals(listOf("en", "fr"), state.availableLanguages)
    }

    @Test
    fun `no questions error preserves availableLanguages and clears questions`() {
        viewModel.load(1L, setOf(99L))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(R.string.error_no_questions_for_domains, state.errorResId)
        assertTrue(state.questions.isEmpty())
        assertEquals(listOf("en", "fr"), state.availableLanguages)
    }

    @Test
    fun `error recovery clears error screen on subsequent valid load`() {
        // First load empty domain
        viewModel.load(1L, setOf(99L))
        assertEquals(R.string.error_no_questions_for_domains, viewModel.state.value.errorResId)

        // User resets/exits and picks valid domain
        viewModel.reset()
        viewModel.load(1L, setOf(1L))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.errorResId)
        assertEquals(2, state.questions.size)
    }

    @Test
    fun `play then reset then play again with same domains reloads cleanly`() {
        viewModel.load(1L, setOf(1L))
        assertEquals(2, viewModel.state.value.questions.size)

        viewModel.reset()
        viewModel.load(1L, setOf(1L))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.errorResId)
        assertEquals(2, state.questions.size)
        assertEquals(0, state.currentIndex)
        assertFalse(state.finished)
    }

    @Test
    fun `switching domain sets after playing quiz loads new domain cleanly`() {
        viewModel.load(1L, setOf(1L))
        assertEquals(setOf(101L, 102L), viewModel.state.value.questions.map { it.id }.toSet())

        viewModel.reset()
        viewModel.load(1L, setOf(2L))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.errorResId)
        assertEquals(2, state.questions.size)
        assertEquals(setOf(201L, 202L), state.questions.map { it.id }.toSet())
    }

    @Test
    fun `language switch during active quiz hot-swaps questions`() {
        viewModel.load(1L, setOf(1L))
        val initialQuestionId = viewModel.state.value.questions[0].id
        val expectedInitialText = "Question $initialQuestionId (en)"
        assertEquals(expectedInitialText, viewModel.state.value.questions[0].text)

        app.currentLang = "fr"
        viewModel.load(1L, setOf(1L))

        val state = viewModel.state.value
        assertNull(state.errorResId)
        assertEquals(2, state.questions.size)
        assertEquals("Question $initialQuestionId (fr)", state.questions[0].text)
    }

    @Test
    fun `domain switch with language switch does fresh load instead of invalid hot swap`() {
        // Load Domain 1 in English
        viewModel.load(1L, setOf(1L))

        viewModel.reset()
        // Switch to French and select Domain 2
        app.currentLang = "fr"
        questionRepo.questionsMap["fr" to setOf(2L)] = listOf(
            createQuestion(201L, 2L, "fr"),
            createQuestion(202L, 2L, "fr"),
        )
        viewModel.load(1L, setOf(2L))

        val state = viewModel.state.value
        assertNull(state.errorResId)
        assertEquals(2, state.questions.size)
        assertEquals(setOf(201L, 202L), state.questions.map { it.id }.toSet())
        assertTrue(state.questions.all { it.text.endsWith("(fr)") })
    }

    @Test
    fun `rapid domain switching keeps state consistent with last requested domain`() {
        viewModel.load(1L, setOf(1L))
        viewModel.load(1L, setOf(2L))

        val state = viewModel.state.value
        assertNull(state.errorResId)
        assertEquals(setOf(201L, 202L), state.questions.map { it.id }.toSet())
    }

    private fun createQuestionWithDifficulty(id: Long, domainId: Long, difficulty: Int, lang: String = "en"): QuizQuestion {
        return QuizQuestion(
            id = id,
            domainId = domainId,
            difficulty = difficulty,
            text = "Question $id ($lang)",
            correctAnswer = "Correct $id",
            distractorPool = listOf(
                Distractor("Wrong A $id"),
                Distractor("Wrong B $id"),
                Distractor("Wrong C $id"),
            ),
            hints = listOf("Hint $id"),
        )
    }

    @Test
    fun `quick quiz load with difficulty filter filters correctly`() {
        questionRepo.questionsMap["en" to setOf(1L)] = listOf(
            createQuestionWithDifficulty(1L, 1L, 1),
            createQuestionWithDifficulty(2L, 1L, 2),
            createQuestionWithDifficulty(3L, 1L, 3),
        )

        viewModel.load(1L, QuizConfig(domainIds = setOf(1L), difficulties = setOf(2)))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.questions.size)
        assertEquals(2L, state.questions[0].id)
        assertEquals(2, state.questions[0].difficulty)
    }

    @Test
    fun `quick quiz load with question limit limits size`() {
        questionRepo.questionsMap["en" to setOf(1L)] = listOf(
            createQuestionWithDifficulty(1L, 1L, 1),
            createQuestionWithDifficulty(2L, 1L, 1),
            createQuestionWithDifficulty(3L, 1L, 1),
        )

        viewModel.load(1L, QuizConfig(domainIds = setOf(1L), questionLimit = 2))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.questions.size)
    }

    @Test
    fun `quick quiz load with filter yielding zero questions shows error`() {
        questionRepo.questionsMap["en" to setOf(1L)] = listOf(
            createQuestionWithDifficulty(1L, 1L, 1),
        )

        viewModel.load(1L, QuizConfig(domainIds = setOf(1L), difficulties = setOf(3)))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(R.string.error_no_questions_for_domains, state.errorResId)
        assertTrue(state.questions.isEmpty())
    }

    @Test
    fun `language switch during feedback preserves feedback and current question`() {
        viewModel.load(1L, setOf(1L))
        assertEquals(0, viewModel.state.value.currentIndex)

        val question = viewModel.state.value.questions[0]
        val wrongAnswer = question.distractorPool[0].text
        viewModel.selectAnswer(wrongAnswer, 1L)

        val stateBefore = viewModel.state.value
        assertEquals(wrongAnswer, stateBefore.answerFeedback?.selectedAnswer)
        assertFalse(stateBefore.answerFeedback!!.isCorrect)

        app.currentLang = "fr"
        viewModel.load(1L, setOf(1L))

        val stateAfter = viewModel.state.value
        assertEquals(0, stateAfter.currentIndex)
        assertNotNull(stateAfter.answerFeedback)
        assertFalse(stateAfter.answerFeedback!!.isCorrect)
    }

    @Test
    fun `language switch during correct answer feedback preserves feedback and schedules advance`() {
        viewModel.load(1L, setOf(1L))
        assertEquals(0, viewModel.state.value.currentIndex)

        val question = viewModel.state.value.questions[0]
        val correctAnswer = question.correctAnswer
        viewModel.selectAnswer(correctAnswer, 1L)

        val stateBefore = viewModel.state.value
        assertEquals(correctAnswer, stateBefore.answerFeedback?.selectedAnswer)
        assertTrue(stateBefore.answerFeedback!!.isCorrect)

        app.currentLang = "fr"
        viewModel.load(1L, setOf(1L))

        val stateAfter = viewModel.state.value
        assertEquals(0, stateAfter.currentIndex)
        assertNotNull(stateAfter.answerFeedback)
        assertTrue(stateAfter.answerFeedback!!.isCorrect)
    }

    @Test
    fun `language switch during active quiz does not set isLoading to true`() {
        viewModel.load(1L, setOf(1L))
        assertFalse(viewModel.state.value.isLoading)
        val initialQuestionId = viewModel.state.value.questions[0].id

        app.currentLang = "fr"
        viewModel.load(1L, setOf(1L))

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Question $initialQuestionId (fr)", viewModel.state.value.questions[0].text)
    }

    @Test
    fun `isSwapped is preserved across reload with same key and reset clears it`() {
        viewModel.load(1L, setOf(1L))
        assertFalse(viewModel.state.value.isSwapped)

        viewModel.toggleSwap()
        assertTrue(viewModel.state.value.isSwapped)

        // Reload with same key (simulating returning from article)
        viewModel.load(1L, setOf(1L))
        assertTrue(viewModel.state.value.isSwapped)

        // Reset clears isSwapped
        viewModel.reset()
        assertFalse(viewModel.state.value.isSwapped)
    }

    @Test
    fun `loading new domain resets isSwapped`() {
        viewModel.load(1L, setOf(1L))
        viewModel.toggleSwap()
        assertTrue(viewModel.state.value.isSwapped)

        // Load different domain
        viewModel.load(1L, setOf(2L))
        assertFalse(viewModel.state.value.isSwapped)
    }

    @Test
    fun `unavailableLanguages contains languages lacking questions for selected domains`() {
        playerRepo.player = Player(
            id = 1L,
            name = "TestPlayer",
            contentLanguages = listOf("en", "fr", "de"),
        )
        viewModel.load(1L, setOf(1L))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf("en", "fr", "de"), state.availableLanguages)
        assertEquals(setOf("de"), state.unavailableLanguages)
    }

    @Test
    fun `quiz config with articleSlug filters questions by article`() {
        val q1 = QuizQuestion(1, "uuid1", 1, "domain", 1, "Q1", "A1", listOf(Distractor("Wrong1")), emptyList(), null, "geography/foo")
        val q2 = QuizQuestion(2, "uuid2", 1, "domain", 1, "Q2", "A2", listOf(Distractor("Wrong2")), emptyList(), null, "geography/bar")
        questionRepo.questionsMap["en" to emptySet()] = listOf(q1, q2)

        val config = QuizConfig(articleSlug = "geography/foo")
        viewModel.load(1L, config)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.questions.size)
        assertEquals("geography/foo", state.questions[0].relatedArticleSlug)
    }
}
