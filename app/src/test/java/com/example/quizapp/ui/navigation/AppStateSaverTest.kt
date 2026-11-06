package com.example.quizapp.ui.navigation

import com.example.quizapp.QuizConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppStateSaverTest {

    @Test
    fun `quiz config with difficulty filter and limit round-trips through bundle`() {
        val config = QuizConfig(
            domainIds = setOf(1L, 2L),
            difficulties = setOf(2, 3),
            questionLimit = 10,
        )
        val screen = Screen.Quiz(config)
        val bundle = screenToBundle(screen)
        val restored = bundleToScreen(bundle)

        assertEquals(screen, restored)
    }

    @Test
    fun `quiz config with articleSlug round-trips through bundle`() {
        val config = QuizConfig(
            domainIds = setOf(1L),
            articleSlug = "domain/article-slug",
        )
        val screen = Screen.Quiz(config)
        val bundle = screenToBundle(screen)
        val restored = bundleToScreen(bundle)

        assertEquals(screen, restored)
    }

    @Test
    fun `replaceCurrent replaces screen without pushing to backstack`() {
        val state = AppState(playerId = 1L, screen = Screen.Home)
        val nextState = state.navigate(Screen.KnowledgeCatalog).replaceCurrent(Screen.Settings)
        assertEquals(Screen.Settings, nextState.screen)
        assertEquals(listOf(Screen.Home), nextState.backStack)
    }

    @Test
    fun `isRailOnLeft preference defaults to true and can be toggled`() {
        val state = AppState(playerId = 1L, screen = Screen.Home)
        assertEquals(true, state.isRailOnLeft)

        val toggled = state.toggleRailSide()
        assertEquals(false, toggled.isRailOnLeft)
    }

    @Test
    fun `six main tabs exist and map correctly to screens and back`() {
        val tabs = MainTab.entries
        assertEquals(6, tabs.size)

        val expectedMapping = listOf(
            MainTab.Home to Screen.Home,
            MainTab.QuizCatalog to Screen.QuizCatalog,
            MainTab.KnowledgeCatalog to Screen.KnowledgeCatalog,
            MainTab.Progress to Screen.Profile,
            MainTab.Saved to Screen.Saved,
            MainTab.Settings to Screen.Settings,
        )

        for ((tab, screen) in expectedMapping) {
            assertEquals(screen, tab.toScreen())
            assertEquals(tab, screen.asTab())
        }
    }

    @Test
    fun `saved screen round-trips through bundle`() {
        val screen = Screen.Saved
        val bundle = screenToBundle(screen)
        val restored = bundleToScreen(bundle)

        assertEquals(screen, restored)
    }

    @Test
    fun `navigating to tab updates screen and preserves rail side preference`() {
        val state = AppState(playerId = 1L, screen = Screen.Home, isRailOnLeft = false)
        val progressState = state.navigateToTab(MainTab.Progress)

        assertEquals(Screen.Profile, progressState.screen)
        assertEquals(false, progressState.isRailOnLeft)

        val savedState = progressState.navigateToTab(MainTab.Saved)
        assertEquals(Screen.Saved, savedState.screen)
        assertEquals(false, savedState.isRailOnLeft)
    }
}
