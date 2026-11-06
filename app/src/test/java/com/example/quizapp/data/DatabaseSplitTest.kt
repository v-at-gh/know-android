package com.example.quizapp.data

import androidx.room3.Room
import com.example.quizapp.data.entity.Attempt
import com.example.quizapp.data.entity.Domain
import com.example.quizapp.data.entity.DomainTranslation
import com.example.quizapp.data.entity.Player
import com.example.quizapp.data.entity.Question
import com.example.quizapp.data.entity.QuestionTranslation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DatabaseSplitTest {

    private lateinit var contentDb: ContentDatabase
    private lateinit var userDb: UserDatabase

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        contentDb = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDb = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        contentDb.close()
        userDb.close()
    }

    @Test
    fun userDatabaseSurvivesContentDatabaseRecreation() = runBlocking {
        // 1. Insert player and attempts into user database
        val playerId = userDb.playerDao().insert(Player(name = "TestPlayer"))
        userDb.attemptDao().insert(
            Attempt(
                playerId = playerId,
                questionUuid = "uuid-123",
                domainSlug = "docker",
                selectedText = "A container platform",
                isCorrect = true,
                hintsUsed = 1,
                language = "en",
            )
        )

        // 2. Insert content into content database
        val domainId = contentDb.domainDao().insert(Domain(slug = "docker"))
        contentDb.domainDao().insertTranslation(
            DomainTranslation(domainId = domainId, language = "en", name = "Docker", description = "")
        )
        val qId = contentDb.questionDao().insert(Question(uuid = "uuid-123", domainId = domainId, difficulty = 1))
        contentDb.questionDao().insertTranslation(
            QuestionTranslation(questionId = qId, language = "en", text = "What is Docker?", answer = "A container platform", distractors = emptyList())
        )

        // 3. Verify user attempt exists
        val initialStats = userDb.attemptDao().getStatsForPlayer(playerId)
        assertEquals(1, initialStats.total)
        assertEquals(1, initialStats.correct)

        // 4. Simulate dropping/recreating content database
        contentDb.close()
        val context = RuntimeEnvironment.getApplication()
        contentDb = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // 5. User database should be unaffected
        val player = userDb.playerDao().getById(playerId)
        assertNotNull(player)
        val statsAfterWipe = userDb.attemptDao().getStatsForPlayer(playerId)
        assertEquals(1, statsAfterWipe.total)
        assertEquals(1, statsAfterWipe.correct)

        val byDomainStats = userDb.attemptDao().getStatsByDomain(playerId)
        assertEquals(1, byDomainStats.size)
        assertEquals("docker", byDomainStats[0].domainSlug)
        assertEquals(1, byDomainStats[0].total)
        assertEquals(1, byDomainStats[0].correct)
    }

    @Test
    fun attemptsPreservedWhenQuestionIdsShift() = runBlocking {
        val playerId = userDb.playerDao().insert(Player(name = "TestPlayer"))

        // Add attempt for UUID "uuid-abc" in domain "math"
        userDb.attemptDao().insert(
            Attempt(
                playerId = playerId,
                questionUuid = "uuid-abc",
                domainSlug = "math",
                selectedText = "4",
                isCorrect = true,
                hintsUsed = 0,
                language = "en",
            )
        )

        // Re-import content with new auto-generated IDs
        val domainId = contentDb.domainDao().insert(Domain(slug = "math"))
        contentDb.domainDao().insertTranslation(
            DomainTranslation(domainId = domainId, language = "en", name = "Mathematics", description = "")
        )
        contentDb.questionDao().insert(Question(uuid = "uuid-abc", domainId = domainId, difficulty = 1))

        val stats = userDb.attemptDao().getStatsByDomain(playerId)
        assertEquals(1, stats.size)
        assertEquals("math", stats[0].domainSlug)
        assertEquals(1, stats[0].total)
        assertEquals(1, stats[0].correct)
    }
}
