package com.example.quizapp.ui.navigation

import com.example.quizapp.QuizConfig
import com.example.quizapp.R

/**
 * Sealed interface representing all navigation screen destinations in the application.
 */
sealed interface Screen {
    // Tabs
    data object Home : Screen
    data object QuizCatalog : Screen
    data object KnowledgeCatalog : Screen
    data object Saved : Screen
    data object Settings : Screen
    data object Search : Screen

    // Focus
    data class Quiz(val config: QuizConfig) : Screen {
        constructor(domainIds: Set<Long>) : this(QuizConfig(domainIds = domainIds))
        val domainIds: Set<Long> get() = config.domainIds
    }
    data object Profile : Screen
    data class Article(val slug: String, val fromQuiz: Boolean = false) : Screen

    // Onboarding
    data object CreatePlayerName : Screen
    data object CreatePlayerUiLanguage : Screen
    data object CreatePlayerContentLanguage : Screen
    data object CreatePlayerDomains : Screen
    // Settings: subscreens
    data object SettingsUiLanguage : Screen
    data object SettingsLanguage : Screen
    data object SettingsDomains : Screen
    data object DeveloperSettings : Screen
    data object SettingsLookAndFeel : Screen
}

/**
 * Main navigation tabs shown in the bottom bar or navigation rail.
 */
enum class MainTab(val titleResId: Int) {
    Home(R.string.tab_home),
    QuizCatalog(R.string.tab_quizzes),
    KnowledgeCatalog(R.string.tab_knowledge),
    Progress(R.string.tab_progress),
    Saved(R.string.tab_saved),
    Settings(R.string.tab_settings),
}

fun Screen.asTab(): MainTab? = when (this) {
    Screen.Home -> MainTab.Home
    Screen.QuizCatalog -> MainTab.QuizCatalog
    Screen.KnowledgeCatalog -> MainTab.KnowledgeCatalog
    Screen.Profile -> MainTab.Progress
    Screen.Saved -> MainTab.Saved
    Screen.Settings -> MainTab.Settings
    else -> null
}

fun MainTab.toScreen(): Screen = when (this) {
    MainTab.Home -> Screen.Home
    MainTab.QuizCatalog -> Screen.QuizCatalog
    MainTab.KnowledgeCatalog -> Screen.KnowledgeCatalog
    MainTab.Progress -> Screen.Profile
    MainTab.Saved -> Screen.Saved
    MainTab.Settings -> Screen.Settings
}