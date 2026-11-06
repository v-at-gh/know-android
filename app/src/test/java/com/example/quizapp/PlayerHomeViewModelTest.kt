package com.example.quizapp

import com.example.quizapp.data.dao.DomainWithTranslation
import com.example.quizapp.data.entity.Player
import com.example.quizapp.data.repository.DomainRepository
import com.example.quizapp.data.repository.PlayerRepository
import com.example.quizapp.ui.screens.PlayerHomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeTestDomainRepository : DomainRepository {
    val domains = listOf(
        DomainWithTranslation(1L, "math", "Mathematics", ""),
        DomainWithTranslation(2L, "physics", "Physics", ""),
        DomainWithTranslation(3L, "biology", "Biology", ""),
    )

    override suspend fun getAll(language: String): List<DomainWithTranslation> = domains
}

class HomeTestQuestionRepository : com.example.quizapp.data.repository.QuestionRepository {
    override suspend fun getQuizQuestions(language: String, domainIds: Set<Long>?, difficulties: Set<Int>?, articleSlug: String?) = emptyList<com.example.quizapp.engine.QuizQuestion>()
    override suspend fun getLanguagesWithContentForDomains(playerLanguages: List<String>, domainIds: Set<Long>) = playerLanguages.toSet()
    override suspend fun countByArticleSlug(language: String, articleSlug: String) = 0
    override suspend fun getAllAvailableLanguages(): List<String> = emptyList()
}

class HomeTestApplication(
    val playerRepo: ReactivePlayerRepository,
    val domainRepo: HomeTestDomainRepository,
    val questionRepo: com.example.quizapp.data.repository.QuestionRepository = HomeTestQuestionRepository(),
) : QuizApplication() {
    override val playerRepository: PlayerRepository get() = playerRepo
    override val domainRepository: DomainRepository get() = domainRepo
    override val questionRepository: com.example.quizapp.data.repository.QuestionRepository get() = questionRepo
    override val contentLanguage: String get() = "en"
    override val qapInstaller: com.example.quizapp.data.qap.QapInstaller = com.example.quizapp.data.qap.QapInstaller()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerHomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: HomeTestApplication
    private lateinit var playerRepo: ReactivePlayerRepository
    private lateinit var domainRepo: HomeTestDomainRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playerRepo = ReactivePlayerRepository()
        domainRepo = HomeTestDomainRepository()
        app = HomeTestApplication(playerRepo, domainRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `domains fall under Other when no campaigns match in content database`() = runTest(testDispatcher) {
        val player = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = listOf("math", "physics"),
            selectedDomainSlugs = listOf("math"),
        )
        playerRepo.setPlayer(player)

        val viewModel = PlayerHomeViewModel(app)
        viewModel.load(playerId = 1L, language = "en")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Player 1", state.playerName)
        assertEquals(1, state.domainGroups.size)
        assertEquals("other", state.domainGroups[0].campaignSlug)
        assertEquals(2, state.domainGroups[0].domains.size)
        assertEquals(setOf(1L), state.selectedDomainIds)
    }

    @Test
    fun `toggleCampaignCollapsed updates collapsedCampaignSlugs`() = runTest(testDispatcher) {
        val player = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = listOf("math"),
        )
        playerRepo.setPlayer(player)

        val viewModel = PlayerHomeViewModel(app)
        viewModel.load(playerId = 1L, language = "en")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.collapsedCampaignSlugs.isEmpty())

        viewModel.toggleCampaignCollapsed("other")
        assertEquals(setOf("other"), viewModel.state.value.collapsedCampaignSlugs)

        viewModel.toggleCampaignCollapsed("other")
        assertTrue(viewModel.state.value.collapsedCampaignSlugs.isEmpty())
    }

    @Test
    fun `selectAllDomains and clearAllDomains update selection`() = runTest(testDispatcher) {
        val player = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = listOf("math", "physics"),
            selectedDomainSlugs = listOf("math"),
        )
        playerRepo.setPlayer(player)

        val viewModel = PlayerHomeViewModel(app)
        viewModel.load(playerId = 1L, language = "en")
        advanceUntilIdle()

        assertEquals(setOf(1L), viewModel.state.value.selectedDomainIds)

        viewModel.selectAllDomains()
        assertEquals(setOf(1L, 2L), viewModel.state.value.selectedDomainIds)

        viewModel.clearAllDomains()
        assertTrue(viewModel.state.value.selectedDomainIds.isEmpty())
    }

    @Test
    fun `toggleCampaignSelection toggles group selection correctly`() = runTest(testDispatcher) {
        val player = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = listOf("math", "physics"),
            selectedDomainSlugs = emptyList(),
        )
        playerRepo.setPlayer(player)

        val viewModel = PlayerHomeViewModel(app)
        viewModel.load(playerId = 1L, language = "en")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.selectedDomainIds.isEmpty())

        // Toggle "other" group (which contains math and physics) from 0 -> select all
        viewModel.toggleCampaignSelection("other")
        assertEquals(setOf(1L, 2L), viewModel.state.value.selectedDomainIds)

        // Toggle "other" group from all -> clear all
        viewModel.toggleCampaignSelection("other")
        assertTrue(viewModel.state.value.selectedDomainIds.isEmpty())
    }

    @Test
    fun `collapse state is preserved on domain toggle`() = runTest(testDispatcher) {
        val player = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = listOf("math", "physics"),
            selectedDomainSlugs = listOf("math"),
        )
        playerRepo.setPlayer(player)

        val viewModel = PlayerHomeViewModel(app)
        viewModel.load(playerId = 1L, language = "en")
        advanceUntilIdle()

        viewModel.toggleCampaignCollapsed("other")
        assertEquals(setOf("other"), viewModel.state.value.collapsedCampaignSlugs)

        // Toggle domain (triggers DB save & playerRepo emit)
        viewModel.toggleDomain(2L)
        advanceUntilIdle()

        // Collapse state should still be preserved
        assertEquals(setOf("other"), viewModel.state.value.collapsedCampaignSlugs)
        assertEquals(setOf(1L, 2L), viewModel.state.value.selectedDomainIds)
    }
}
