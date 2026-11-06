package com.example.quizapp

import com.example.quizapp.data.dao.DomainWithTranslation
import com.example.quizapp.data.entity.Player
import com.example.quizapp.data.knowledge.Article
import com.example.quizapp.data.knowledge.ArticleMeta
import com.example.quizapp.data.knowledge.KnowledgeRepository
import com.example.quizapp.data.repository.AttemptRepository
import com.example.quizapp.data.repository.DomainRepository
import com.example.quizapp.data.repository.PlayerRepository
import com.example.quizapp.data.repository.QuestionRepository
import com.example.quizapp.ui.screens.knowledge.ArticleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ArticleTestKnowledgeRepository : KnowledgeRepository {
    val indexMap = mutableMapOf<String, List<ArticleMeta>>()

    override suspend fun getIndex(language: String): List<ArticleMeta> {
        return indexMap[language] ?: emptyList()
    }

    override suspend fun getArticle(language: String, meta: ArticleMeta): Article {
        return Article(meta, "Content")
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

class ArticleTestPlayerRepository : PlayerRepository {
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

class ArticleTestDomainRepository : DomainRepository {
    override suspend fun getAll(language: String): List<DomainWithTranslation> = emptyList()
}

class ArticleTestQuestionRepository : QuestionRepository {
    var quizCount = 2
    override suspend fun getQuizQuestions(
        language: String,
        domainIds: Set<Long>?,
        difficulties: Set<Int>?,
        articleSlug: String?,
    ): List<com.example.quizapp.engine.QuizQuestion> = emptyList()

    override suspend fun getLanguagesWithContentForDomains(
        playerLanguages: List<String>,
        domainIds: Set<Long>,
    ): Set<String> = emptySet()

    override suspend fun countByArticleSlug(language: String, articleSlug: String): Int = quizCount

    override suspend fun getAllAvailableLanguages(): List<String> = emptyList()
}

class ArticleTestApplication(
    val knowledgeRepo: ArticleTestKnowledgeRepository,
    val playerRepo: ArticleTestPlayerRepository,
    val questionRepo: ArticleTestQuestionRepository = ArticleTestQuestionRepository(),
) : QuizApplication() {
    override val knowledgeRepository: KnowledgeRepository get() = knowledgeRepo
    override val playerRepository: PlayerRepository get() = playerRepo
    override val domainRepository: DomainRepository get() = ArticleTestDomainRepository()
    override val attemptRepository: AttemptRepository get() = error("Not needed")
    override val questionRepository: QuestionRepository get() = questionRepo
    override val contentLanguage: String get() = "en"
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var knowledgeRepo: ArticleTestKnowledgeRepository
    private lateinit var playerRepo: ArticleTestPlayerRepository
    private lateinit var app: ArticleTestApplication
    private lateinit var viewModel: ArticleViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        knowledgeRepo = ArticleTestKnowledgeRepository()
        playerRepo = ArticleTestPlayerRepository()
        app = ArticleTestApplication(knowledgeRepo, playerRepo)
        viewModel = ArticleViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unavailableLanguages contains languages lacking the article`() {
        val meta = ArticleMeta(
            uuid = "1",
            slug = "test-slug",
            domainSlug = "domain",
            title = "Test Article",
            description = "Desc",
            path = "test.md",
            order = 1,
        )
        knowledgeRepo.indexMap["en"] = listOf(meta)
        knowledgeRepo.indexMap["fr"] = emptyList()

        playerRepo.player = Player(
            id = 1L,
            name = "Test",
            contentLanguages = listOf("en", "fr"),
        )

        viewModel.load("test-slug", "en", 1L)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.errorResId)
        assertEquals(listOf("en", "fr"), state.availableLanguages)
        assertEquals(setOf("fr"), state.unavailableLanguages)
    }

    @Test
    fun `neighbor calculation and hasArticleQuiz are computed correctly`() {
        val meta1 = ArticleMeta("1", "slug-1", "domain", "Title 1", "Desc", "path1.md", 1)
        val meta2 = ArticleMeta("2", "slug-2", "domain", "Title 2", "Desc", "path2.md", 2)
        val meta3 = ArticleMeta("3", "slug-3", "domain", "Title 3", "Desc", "path3.md", 3)
        knowledgeRepo.indexMap["en"] = listOf(meta1, meta2, meta3)

        viewModel.load("slug-2", "en", 1L)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("slug-1", state.previousSlug)
        assertEquals("slug-3", state.nextSlug)
        assertEquals(true, state.hasArticleQuiz)
    }

    @Test
    fun `language change preserves swiped article slug over initial load slug`() {
        val meta1 = ArticleMeta("1", "slug-1", "domain", "Title 1", "Desc", "path1.md", 1)
        val meta2 = ArticleMeta("2", "slug-2", "domain", "Title 2", "Desc", "path2.md", 2)
        val meta3 = ArticleMeta("3", "slug-3", "domain", "Title 3", "Desc", "path3.md", 3)
        knowledgeRepo.indexMap["en"] = listOf(meta1, meta2, meta3)
        knowledgeRepo.indexMap["fr"] = listOf(meta1, meta2, meta3)

        viewModel.load("slug-1", "en", 1L)
        viewModel.selectArticle("slug-3")

        viewModel.load("slug-1", "fr", 1L)

        val state = viewModel.state.value
        assertEquals(2, state.currentIndex)
        assertEquals("slug-3", state.article?.meta?.slug)
    }
}
