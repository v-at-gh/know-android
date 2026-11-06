package com.example.quizapp

import com.example.quizapp.data.dao.DomainWithTranslation
import com.example.quizapp.data.entity.Player
import com.example.quizapp.data.knowledge.Article
import com.example.quizapp.data.knowledge.ArticleMeta
import com.example.quizapp.data.knowledge.KnowledgeRepository
import com.example.quizapp.data.repository.DomainRepository
import com.example.quizapp.data.repository.PlayerRepository
import com.example.quizapp.ui.screens.knowledge.KnowledgeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeKnowledgeRepository : KnowledgeRepository {
    var articlesIndex = listOf(
        ArticleMeta(
            uuid = "1",
            slug = "intro-math",
            domainSlug = "math",
            title = "Math Intro",
            description = "Math desc",
            path = "math/intro.md",
            order = 1,
        ),
        ArticleMeta(
            uuid = "2",
            slug = "intro-physics",
            domainSlug = "physics",
            title = "Physics Intro",
            description = "Physics desc",
            path = "physics/intro.md",
            order = 2,
        ),
        ArticleMeta(
            uuid = "3",
            slug = "intro-biology",
            domainSlug = "biology",
            title = "Biology Intro",
            description = "Biology desc",
            path = "biology/intro.md",
            order = 3,
        ),
    )

    override suspend fun getIndex(language: String): List<ArticleMeta> = articlesIndex

    override suspend fun getArticle(language: String, meta: ArticleMeta): Article {
        return Article(meta, "# ${meta.title}\nContent")
    }

    override suspend fun getLanguagesWithArticle(
        playerLanguages: List<String>,
        slug: String,
    ): Set<String> {
        return playerLanguages.filterTo(mutableSetOf()) { lang ->
            getIndex(lang).any { it.slug == slug }
        }
    }
}

class FakeDomainRepository : DomainRepository {
    val domains = listOf(
        DomainWithTranslation(1L, "math", "Mathematics", ""),
        DomainWithTranslation(2L, "physics", "Physics", ""),
        DomainWithTranslation(3L, "biology", "Biology", ""),
    )

    override suspend fun getAll(language: String): List<DomainWithTranslation> = domains
}

class ReactivePlayerRepository : PlayerRepository {
    val players = mutableMapOf<Long, MutableStateFlow<Player?>>()

    fun setPlayer(player: Player) {
        val flow = players.getOrPut(player.id) { MutableStateFlow(player) }
        flow.value = player
    }

    override fun observeAll(): Flow<List<Player>> = error("Not needed")

    override fun observeById(id: Long): Flow<Player?> {
        return players.getOrPut(id) { MutableStateFlow(null) }
    }

    override suspend fun getById(id: Long): Player? = players[id]?.value

    override suspend fun create(player: Player): Long {
        setPlayer(player)
        return player.id
    }



    override suspend fun updatePreferredDomains(playerId: Long, slugs: List<String>) {
        val current = players[playerId]?.value ?: return
        setPlayer(current.copy(preferredDomainSlugs = slugs))
    }

    override suspend fun updatePrimaryLanguage(playerId: Long, lang: String) {}

    override suspend fun updateContentLanguages(playerId: Long, langs: List<String>) {}

    override suspend fun updateSelectedDomains(playerId: Long, slugs: List<String>?) {
        val current = players[playerId]?.value ?: return
        setPlayer(current.copy(selectedDomainSlugs = slugs))
    }
}

class TestKnowledgeApplication(
    val fakeKnowledgeRepo: FakeKnowledgeRepository,
    val fakeDomainRepo: FakeDomainRepository,
    val fakePlayerRepo: ReactivePlayerRepository,
) : QuizApplication() {
    override val playerRepository: PlayerRepository get() = fakePlayerRepo
    override val domainRepository: DomainRepository get() = fakeDomainRepo
    override val knowledgeRepository: KnowledgeRepository get() = fakeKnowledgeRepo
}

@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: TestKnowledgeApplication
    private lateinit var fakeKnowledgeRepo: FakeKnowledgeRepository
    private lateinit var fakeDomainRepo: FakeDomainRepository
    private lateinit var fakePlayerRepo: ReactivePlayerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeKnowledgeRepo = FakeKnowledgeRepository()
        fakeDomainRepo = FakeDomainRepository()
        fakePlayerRepo = ReactivePlayerRepository()

        app = TestKnowledgeApplication(
            fakeKnowledgeRepo = fakeKnowledgeRepo,
            fakeDomainRepo = fakeDomainRepo,
            fakePlayerRepo = fakePlayerRepo,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `knowledge articles are filtered by player preferred domains`() = runTest(testDispatcher) {
        val player = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = listOf("math", "physics"),
        )
        fakePlayerRepo.setPlayer(player)

        val viewModel = KnowledgeViewModel(app)
        viewModel.load(playerId = 1L, language = "en")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.errorResId)

        val domainSlugs = state.campaignGroups.flatMap { it.domainGroups }.map { it.domainSlug }
        assertEquals(listOf("math", "physics"), domainSlugs.sorted())
        assertTrue(domainSlugs.none { it == "biology" })
    }

    @Test
    fun `dynamic updates to player domains update knowledge base groups`() = runTest(testDispatcher) {
        val player = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = listOf("math"),
        )
        fakePlayerRepo.setPlayer(player)

        val viewModel = KnowledgeViewModel(app)
        viewModel.load(playerId = 1L, language = "en")
        advanceUntilIdle()

        assertEquals(listOf("math"), viewModel.state.value.campaignGroups.flatMap { it.domainGroups }.map { it.domainSlug })

        // Update player domain settings in repository
        fakePlayerRepo.setPlayer(player.copy(preferredDomainSlugs = listOf("math", "biology")))
        advanceUntilIdle()

        val updatedSlugs = viewModel.state.value.campaignGroups.flatMap { it.domainGroups }.map { it.domainSlug }
        assertEquals(listOf("biology", "math"), updatedSlugs.sorted())
    }

    @Test
    fun `empty domain selection results in empty knowledge base groups`() = runTest(testDispatcher) {
        val player = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = emptyList(),
        )
        fakePlayerRepo.setPlayer(player)

        val viewModel = KnowledgeViewModel(app)
        viewModel.load(playerId = 1L, language = "en")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.campaignGroups.isEmpty())
    }

    @Test
    fun `toggling collapse state updates collapsed slugs without modifying campaign groups`() = runTest(testDispatcher) {
        val player = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = listOf("math", "physics"),
        )
        fakePlayerRepo.setPlayer(player)

        val viewModel = KnowledgeViewModel(app)
        viewModel.load(playerId = 1L, language = "en")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.collapsedDomainSlugs.isEmpty())
        assertTrue(viewModel.state.value.collapsedCampaignSlugs.isEmpty())

        // Collapse "math" domain
        viewModel.toggleDomainCollapsed("math")
        assertEquals(setOf("math"), viewModel.state.value.collapsedDomainSlugs)

        // Collapse "other" campaign
        viewModel.toggleCampaignCollapsed("other")
        assertEquals(setOf("other"), viewModel.state.value.collapsedCampaignSlugs)

        // Group list still contains math and physics
        assertEquals(2, viewModel.state.value.campaignGroups.flatMap { it.domainGroups }.size)

        // Expand back
        viewModel.toggleDomainCollapsed("math")
        viewModel.toggleCampaignCollapsed("other")
        assertTrue(viewModel.state.value.collapsedDomainSlugs.isEmpty())
        assertTrue(viewModel.state.value.collapsedCampaignSlugs.isEmpty())
    }

    @Test
    fun `different players have isolated knowledge base filtering`() = runTest(testDispatcher) {
        val player1 = Player(
            id = 1L,
            name = "Player 1",
            preferredDomainSlugs = listOf("math"),
        )
        val player2 = Player(
            id = 2L,
            name = "Player 2",
            preferredDomainSlugs = listOf("biology"),
        )
        fakePlayerRepo.setPlayer(player1)
        fakePlayerRepo.setPlayer(player2)

        val viewModel1 = KnowledgeViewModel(app)
        viewModel1.load(playerId = 1L, language = "en")
        advanceUntilIdle()
        assertEquals(listOf("math"), viewModel1.state.value.campaignGroups.flatMap { it.domainGroups }.map { it.domainSlug })

        val viewModel2 = KnowledgeViewModel(app)
        viewModel2.load(playerId = 2L, language = "en")
        advanceUntilIdle()
        assertEquals(listOf("biology"), viewModel2.state.value.campaignGroups.flatMap { it.domainGroups }.map { it.domainSlug })
    }
}
