package com.example.quizapp.ui.navigation

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import com.example.quizapp.QuizConfig

/**
 * Represents the complete navigation and UI state of the application.
 *
 * @property playerId The ID of the currently active player, or null if no player is selected.
 * @property screen The current active [Screen] being displayed.
 * @property backStack The list of previous screens in the navigation history.
 * @property switchingFrom The screen we were on when player switching was initiated, if applicable.
 * @property previousPlayerId The previous player's ID prior to switching players.
 * @property isRailOnLeft Whether the navigation rail is positioned on the left side of the screen.
 */
data class AppState(
    val playerId: Long?,
    val screen: Screen,
    val backStack: List<Screen> = emptyList(),
    val switchingFrom: Screen? = null,
    val previousPlayerId: Long? = null,
    val isRailOnLeft: Boolean = true,
) {

    /**
     * Pushes the current screen onto the back stack and transitions to the specified [target] screen.
     *
     * @param target The destination [Screen] to navigate to.
     */
    fun navigate(target: Screen): AppState {
        val newStack = backStack + screen
        val cappedStack = if (newStack.size > 20) newStack.drop(newStack.size - 20) else newStack
        return copy(
            backStack = cappedStack,
            screen = target,
        )
    }

    /**
     * Open an article. De-dupes against the back stack by exact Screen.Article
     * equality — slug AND fromQuiz — mirroring navigateToQuiz. Browse-mode and
     * detour-mode visits to the same slug are distinct screens with different
     * chrome and affordances; they must not collapse into each other.
     *
     * If an exact match is found, rolls back to it (screen = match,
     * backStack = everything below). Otherwise pushes.
     */
    fun navigateToArticle(slug: String, fromQuiz: Boolean): AppState {
        val target = Screen.Article(slug, fromQuiz = fromQuiz)
        val matchIdx = backStack.indexOfLast { it == target }
        return if (matchIdx >= 0) {
            val newStack = backStack.take(matchIdx)
            val cappedStack = if (newStack.size > 20) newStack.drop(newStack.size - 20) else newStack
            copy(
                screen = backStack[matchIdx],
                backStack = cappedStack,
            )
        } else {
            navigate(target)
        }
    }

    /**
     * Navigates to a quiz screen with the specified [config], de-duplicating against the back stack
     * to prevent redundant quiz screen instances. If an identical quiz screen exists in the back stack,
     * rolls back to it; otherwise pushes normally.
     *
     * @param config The [QuizConfig] defining the quiz parameters.
     */
    fun navigateToQuiz(config: QuizConfig): AppState {
        val target = Screen.Quiz(config)
        val matchIdx = backStack.indexOfLast { it == target }
        return if (matchIdx >= 0) {
            copy(
                screen = target,
                backStack = backStack.take(matchIdx),
            )
        } else {
            navigate(target)
        }
    }

    /**
     * Replaces the current active screen with the [target] screen without modifying the back stack.
     *
     * @param target The replacement [Screen].
     */
    fun replaceCurrent(target: Screen): AppState = copy(screen = target)

    /**
     * Toggles the position of the navigation rail between left and right sides.
     */
    fun toggleRailSide(): AppState = copy(isRailOnLeft = !isRailOnLeft)

    /**
     * Navigates to a top-level tab, clearing the back stack.
     *
     * @param tab The target [MainTab].
     */
    fun navigateToTab(tab: MainTab): AppState = copy(
        screen = tab.toScreen(),
        backStack = emptyList(),
    )

    /**
     * Pops the top screen from the back stack. If the back stack is empty, falls back to a parent
     * screen determined by [fallbackParent].
     */
    fun goBack(): AppState {
        return if (backStack.isNotEmpty()) {
            copy(
                screen = backStack.last(),
                backStack = backStack.dropLast(1),
            )
        } else {
            copy(screen = fallbackParent(screen))
        }
    }

    fun popToRoot(): AppState = copy(
        screen = Screen.Home,
        backStack = emptyList(),
    )

    fun startSwitchPlayer(): AppState = copy(
        playerId = null,
        screen = Screen.Home,
        backStack = emptyList(),
        switchingFrom = screen,
        previousPlayerId = playerId,
    )

    fun destinationAfterSelection(): Screen = switchingFrom ?: Screen.Home
}

/**
 * Determines the logical parent fallback screen when navigating back from a screen with an empty back stack.
 *
 * @param screen The current [Screen].
 * @return The parent [Screen] to fall back to.
 */
private fun fallbackParent(screen: Screen): Screen = when (screen) {
    is Screen.Article -> if (screen.fromQuiz) Screen.Home else Screen.KnowledgeCatalog
    is Screen.Quiz -> Screen.Home
    Screen.Profile -> Screen.Home
    Screen.QuizCatalog, Screen.KnowledgeCatalog, Screen.Saved, Screen.Settings, Screen.Search -> Screen.Home
    Screen.Home -> Screen.Home
    Screen.CreatePlayerName,
    Screen.CreatePlayerUiLanguage,
    Screen.CreatePlayerContentLanguage,
    Screen.CreatePlayerDomains -> Screen.Home
    Screen.SettingsUiLanguage, Screen.SettingsLanguage, Screen.SettingsDomains, Screen.DeveloperSettings, Screen.SettingsLookAndFeel -> Screen.Settings
}

internal fun screenToBundle(screen: Screen): Bundle = Bundle().apply {
    when (screen) {
        Screen.Home -> putString("kind", "home")
        Screen.QuizCatalog -> putString("kind", "quizCatalog")
        Screen.KnowledgeCatalog -> putString("kind", "knowledgeCatalog")
        Screen.Saved -> putString("kind", "saved")
        Screen.Settings -> putString("kind", "settings")
        Screen.Search -> putString("kind", "search")
        Screen.Profile -> putString("kind", "profile")
        Screen.CreatePlayerName -> putString("kind", "createPlayerName")
        Screen.CreatePlayerUiLanguage -> putString("kind", "createPlayerUiLanguage")
        Screen.CreatePlayerContentLanguage -> putString("kind", "createPlayerContentLanguage")
        Screen.CreatePlayerDomains -> putString("kind", "createPlayerDomains")
        Screen.SettingsUiLanguage -> putString("kind", "settingsUiLanguage")
        Screen.SettingsLanguage -> putString("kind", "settingsLanguage")
        Screen.SettingsDomains -> putString("kind", "settingsDomains")
        Screen.DeveloperSettings -> putString("kind", "developerSettings")
        Screen.SettingsLookAndFeel -> putString("kind", "settingsLookAndFeel")

        is Screen.Quiz -> {
            putString("kind", "quiz")
            putLongArray("domainIds", screen.config.domainIds.toLongArray())
            screen.config.difficulties?.let { putIntArray("difficulties", it.toIntArray()) }
            if (screen.config.questionLimit != null) {
                putInt("questionLimit", screen.config.questionLimit)
            }
            screen.config.articleSlug?.let { putString("articleSlug", it) }
        }

        is Screen.Article -> {
            putString("kind", "article")
            putString("slug", screen.slug)
            putBoolean("fromQuiz", screen.fromQuiz)
        }
    }
}

internal fun bundleToScreen(bundle: Bundle): Screen = when (bundle.getString("kind")) {
    "quizCatalog" -> Screen.QuizCatalog
    "knowledgeCatalog" -> Screen.KnowledgeCatalog
    "saved" -> Screen.Saved
    "settings" -> Screen.Settings
    "search" -> Screen.Search
    "profile" -> Screen.Profile
    "quiz" -> {
        val domainIds = bundle.getLongArray("domainIds")?.toSet() ?: emptySet()
        val difficulties = bundle.getIntArray("difficulties")?.toSet()
        val questionLimit = if (bundle.containsKey("questionLimit")) bundle.getInt("questionLimit") else null
        val articleSlug = bundle.getString("articleSlug")
        Screen.Quiz(QuizConfig(domainIds = domainIds, difficulties = difficulties, questionLimit = questionLimit, articleSlug = articleSlug))
    }
    "settingsUiLanguage" -> Screen.SettingsUiLanguage
    "settingsLanguage" -> Screen.SettingsLanguage
    "settingsDomains" -> Screen.SettingsDomains
    "developerSettings" -> Screen.DeveloperSettings
    "settingsLookAndFeel" -> Screen.SettingsLookAndFeel
    "article" -> Screen.Article(bundle.getString("slug").orEmpty(), bundle.getBoolean("fromQuiz", false))
    "createPlayerName" -> Screen.CreatePlayerName
    "createPlayerUiLanguage" -> Screen.CreatePlayerUiLanguage
    "createPlayerContentLanguage", "createPlayerLanguage" -> Screen.CreatePlayerContentLanguage
    "createPlayerDomains" -> Screen.CreatePlayerDomains
    else -> Screen.Home
}

val AppStateSaver: Saver<AppState, Bundle> = Saver(
    save = { state ->
        Bundle().apply {
            state.playerId?.let { putLong("playerId", it) }
            putBundle("screen", screenToBundle(state.screen))
            putParcelableArray(
                "backStack",
                state.backStack.map { screenToBundle(it) }.toTypedArray(),
            )
            state.switchingFrom?.let { putBundle("switchingFrom", screenToBundle(it)) }
            state.previousPlayerId?.let { putLong("previousPlayerId", it) }
            putBoolean("isRailOnLeft", state.isRailOnLeft)
        }
    },
    restore = { bundle ->
        val playerId = if (bundle.containsKey("playerId")) bundle.getLong("playerId") else null
        val screen = bundle.getBundle("screen")?.let { bundleToScreen(it) } ?: Screen.Home

        @Suppress("DEPRECATION")
        val backStack = bundle.getParcelableArray("backStack")
            ?.mapNotNull { it as? Bundle }
            ?.map { bundleToScreen(it) }
            .orEmpty()
        val switchingFrom = bundle.getBundle("switchingFrom")?.let { bundleToScreen(it) }
        val previousPlayerId =
            if (bundle.containsKey("previousPlayerId"))
                bundle.getLong("previousPlayerId") else null
        val isRailOnLeft = if (bundle.containsKey("isRailOnLeft")) bundle.getBoolean("isRailOnLeft") else true
        AppState(playerId, screen, backStack, switchingFrom, previousPlayerId, isRailOnLeft)
    },
)
