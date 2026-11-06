package com.example.quizapp.ui.navigation

import com.example.quizapp.QuizConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStateTest {

    private val dummyPlayerId = 1L

    private fun state(screen: Screen, backStack: List<Screen> = emptyList()): AppState =
        AppState(playerId = dummyPlayerId, screen = screen, backStack = backStack)

    private val quizConfigA = QuizConfig(domainIds = setOf(1L))
    private val quizConfigB = QuizConfig(domainIds = setOf(2L))

    private val articleBrowseA = Screen.Article(slug = "a", fromQuiz = false)
    private val articleDetourA = Screen.Article(slug = "a", fromQuiz = true)
    private val quizA = Screen.Quiz(quizConfigA)
    private val quizB = Screen.Quiz(quizConfigB)

    @Test
    fun navigateToArticle_rollsBackToExactMatch() {
        // Stack bottom -> top: Home -> quizA -> articleDetourA. Current screen: articleBrowseA.
        // Wait, we want to navigate TO articleDetourA, which is in the stack.
        // Let's set up: Home -> articleBrowseA -> quizA. Current screen is Home (simulating weird state).
        // Actually, let's keep it realistic: Home -> quizA -> articleDetourA.
        // Current screen = Home (happens if we manually set state).
        // Let's just set the stack list directly to what it should be.
        val initial = state(
            screen = Screen.Home,
            backStack = listOf(articleBrowseA, quizA, articleDetourA)
        )
        val result = initial.navigateToArticle(slug = "a", fromQuiz = true)
        // matchIdx of articleDetourA is 2. take(2) = [articleBrowseA, quizA].
        assertEquals(articleDetourA, result.screen)
        assertEquals(listOf(articleBrowseA, quizA), result.backStack)
    }

    @Test
    fun navigateToArticle_doesNotCollapseBrowseIntoDetour() {
        val initial = state(
            screen = Screen.Home,
            backStack = listOf(articleBrowseA)
        )
        val result = initial.navigateToArticle(slug = "a", fromQuiz = true)
        assertEquals(articleDetourA, result.screen)
        assertEquals(listOf(articleBrowseA, Screen.Home), result.backStack)
    }

    @Test
    fun navigateToQuiz_rollsBackToExactMatch() {
        // Realistic setup: Home -> quizA -> articleBrowseA. Current screen is articleBrowseA.
        // We navigate to quizA.
        val initial = state(
            screen = articleBrowseA,
            backStack = listOf(Screen.Home, quizA)
        )
        val result = initial.navigateToQuiz(quizConfigA)
        // matchIdx of quizA is 1. take(1) = [Home].
        assertEquals(quizA, result.screen)
        assertEquals(listOf(Screen.Home), result.backStack)
    }

    @Test
    fun navigateToQuiz_pushesNewConfig() {
        val initial = state(
            screen = Screen.Home,
            backStack = listOf(quizA)
        )
        val result = initial.navigateToQuiz(quizConfigB)
        assertEquals(quizB, result.screen)
        assertEquals(listOf(quizA, Screen.Home), result.backStack)
    }

    @Test
    fun goBack_popsScreenCorrectly() {
        // Realistic setup: Home -> articleBrowseA -> quizA. Current screen is quizA.
        val initial = state(
            screen = quizA,
            backStack = listOf(Screen.Home, articleBrowseA)
        )
        val result = initial.goBack()
        // Last element is articleBrowseA. dropLast(1) is [Home].
        assertEquals(articleBrowseA, result.screen)
        assertEquals(listOf(Screen.Home), result.backStack)
    }

    @Test
    fun goBack_usesFallbackWhenEmpty() {
        val initial = state(
            screen = articleDetourA,
            backStack = emptyList()
        )
        val result = initial.goBack()
        assertEquals(Screen.Home, result.screen)
    }
}
