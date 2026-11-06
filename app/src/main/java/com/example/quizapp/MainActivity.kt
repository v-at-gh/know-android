package com.example.quizapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.ui.common.LanguageSelectionDialog
import com.example.quizapp.ui.common.LanguageSwitcher
import com.example.quizapp.ui.common.languageDisplayName
import com.example.quizapp.ui.common.rememberHapticTap
import com.example.quizapp.ui.navigation.AppState
import com.example.quizapp.ui.navigation.AppStateSaver
import com.example.quizapp.ui.navigation.MainScaffold
import com.example.quizapp.ui.navigation.MainTab
import com.example.quizapp.ui.navigation.Screen
import com.example.quizapp.ui.navigation.asTab
import com.example.quizapp.ui.screens.KnowledgeCatalogScreen
import com.example.quizapp.ui.screens.PlayerHomeScreen
import com.example.quizapp.ui.screens.PlayerScreen
import com.example.quizapp.ui.screens.ProfileScreen
import com.example.quizapp.ui.screens.QuizCatalogScreen
import com.example.quizapp.ui.screens.SavedScreen
import com.example.quizapp.ui.screens.SearchScreen
import com.example.quizapp.ui.screens.SettingsScreen
import com.example.quizapp.ui.screens.create.CreatePlayerContentLanguageScreen
import com.example.quizapp.ui.screens.create.CreatePlayerDomainsScreen
import com.example.quizapp.ui.screens.create.CreatePlayerNameScreen
import com.example.quizapp.ui.screens.create.CreatePlayerUiLanguageScreen
import com.example.quizapp.ui.screens.knowledge.ArticleScreen
import com.example.quizapp.ui.screens.knowledge.ArticleUiState
import com.example.quizapp.ui.screens.knowledge.ArticleViewModel
import com.example.quizapp.ui.screens.quiz.QuizScreen
import com.example.quizapp.ui.screens.settings.DeveloperSettingsScreen
import com.example.quizapp.ui.screens.settings.SettingsDomainsScreen
import com.example.quizapp.ui.screens.settings.SettingsLanguageScreen
import com.example.quizapp.ui.screens.settings.SettingsLookAndFeelScreen
import com.example.quizapp.ui.screens.settings.SettingsUiLanguageScreen
import com.example.quizapp.ui.theme.QuizAppTheme
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig

/**
 * Main entry point Activity of QuizApp.
 * Handles edge-to-edge layout, splash screen initialization, deep link intent handling, and top-level navigation container.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            QuizAppTheme {
                LatexWarmup()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val app = application as QuizApplication
                    val appReady by app.appReady.collectAsStateWithLifecycle()
                    if (!appReady) {
                        SplashScreen()
                    } else {
                        AppNavigation()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "quizapp" && data.host == "install") {
            val server = data.getQueryParameter("server")
            val packId = data.getQueryParameter("pack")
            if (!server.isNullOrBlank() && !packId.isNullOrBlank()) {
                (application as QuizApplication).handleDeepLink(server, packId)
            }
        }
    }
}

/**
 * Composable screen displayed during initial dataset bootstrapping and content importation.
 */
@Composable
private fun SplashScreen() {
    val app = LocalContext.current.applicationContext as QuizApplication
    val showProgressBar by app.contentBootstrapper.showSplashProgressBar.collectAsStateWithLifecycle()
    val progress by app.contentBootstrapper.importProgress.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        if (showProgressBar && progress != null && progress!!.domainsTotal > 0) {
            val p = progress!!
            val fraction = p.domainsDone.toFloat() / p.domainsTotal.toFloat()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Importing content: ${p.domainsDone} / ${p.domainsTotal}",
                    style = MaterialTheme.typography.titleMedium,
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun LatexWarmup() {
    val measurer = rememberLatexMeasurer()
    LaunchedEffect(Unit) {
        runCatching {
            measurer.inlineContent("x", LatexConfig(16.sp))
            measurer.inlineContent("x^2 + y^2", LatexConfig(16.sp))
            measurer.inlineContent("\\frac{a}{b}", LatexConfig(16.sp))
        }
    }
}

@Composable
private fun AppNavigation() {
    val app = LocalContext.current.applicationContext as QuizApplication
    val lastPlayerId by app.preferences.lastPlayerId.collectAsStateWithLifecycle()
    var state by rememberSaveable(stateSaver = AppStateSaver) {
        mutableStateOf(
            AppState(
                playerId = lastPlayerId.takeIf { it != 0L },
                screen = Screen.Home,
            )
        )
    }

    var deepLinkInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        app.deepLinkEvents.collect { info ->
            deepLinkInfo = info
        }
    }

    if (deepLinkInfo != null) {
        AlertDialog(
            onDismissRequest = { deepLinkInfo = null },
            title = { Text("Install Course?") },
            text = { Text("Install pack '${deepLinkInfo!!.second}' from ${deepLinkInfo!!.first}?") },
            confirmButton = {
                TextButton(onClick = {
                    val (server, packId) = deepLinkInfo!!
                    app.preferences.setDevServer(server)
                    state = state.navigateToTab(MainTab.Settings)
                    deepLinkInfo = null
                }) { Text("Go to Settings") }
            },
            dismissButton = { TextButton(onClick = { deepLinkInfo = null }) { Text("Cancel") } }
        )
    }

    val canGoBack = state.backStack.isNotEmpty() ||
            (state.playerId != null && state.screen != Screen.Home)
    BackHandler(enabled = canGoBack) {
        state = state.goBack()
    }
    val playerId = state.playerId
    var emptyAutoNavigated by rememberSaveable { mutableStateOf(false) }
    var lastAppliedPlayerId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(playerId) {
        if (playerId != null && playerId != lastAppliedPlayerId) {
            lastAppliedPlayerId = playerId
            val player = app.playerRepository.getById(playerId)
            if (player != null) {
                app.preferences.setContentLanguage(player.primaryContentLanguage)
            }
        } else if (playerId == null) {
            lastAppliedPlayerId = null
        }
    }

    // ---------------------------------------------------------------------
    // Branch «player not selected» — three possible screens:
    // PlayerSelect (default), CreatePlayerName, CreatePlayerLanguages.
    // ---------------------------------------------------------------------
    if (playerId == null) {
        val prevId = state.previousPlayerId
        val atRoot = state.backStack.isEmpty()
        BackHandler(enabled = prevId != null && atRoot) {
            app.preferences.setLastPlayerId(prevId!!)
            state = AppState(
                playerId = prevId,
                screen = state.switchingFrom ?: Screen.Home,
            )
        }
        when (state.screen) {
            Screen.CreatePlayerName -> CreatePlayerNameScreen(
                onNext = {
                    state = state.navigate(Screen.CreatePlayerUiLanguage)
                },
                onBack = { state = state.goBack() },
            )
            Screen.CreatePlayerUiLanguage -> CreatePlayerUiLanguageScreen(
                onNext = { state = state.navigate(Screen.CreatePlayerContentLanguage) },
                onDone = { id ->
                    app.preferences.setLastPlayerId(id)
                    emptyAutoNavigated = true
                    state = AppState(playerId = id, screen = Screen.Home)
                },
                onBack = { state = state.goBack() },
            )
            Screen.CreatePlayerContentLanguage -> CreatePlayerContentLanguageScreen(
                onDone = { id ->
                    app.preferences.setLastPlayerId(id)
                    emptyAutoNavigated = true
                    state = AppState(playerId = id, screen = Screen.Home)
                },
                onBack = { state = state.goBack() },
            )
            Screen.CreatePlayerDomains -> CreatePlayerDomainsScreen(
                onDone = { id ->
                    app.preferences.setLastPlayerId(id)
                    emptyAutoNavigated = true
                    state = AppState(playerId = id, screen = Screen.Home)
                },
                onBack = { state = state.goBack() },
            )
            else -> PlayerScreen(
                onPlayerSelected = { id ->
                    app.preferences.setLastPlayerId(id)
                    state = AppState(
                        playerId = id,
                        screen = state.destinationAfterSelection(),
                    )
                },
                onCreateRequested = {
                    state = state.navigate(Screen.CreatePlayerName)
                },
            )
        }
        return
    }

    // ---------------------------------------------------------------------
    // Branch «player selected» — full app.
    // ---------------------------------------------------------------------
    LaunchedEffect(playerId) {
        if (state.screen == Screen.CreatePlayerName || state.screen == Screen.CreatePlayerUiLanguage || state.screen == Screen.CreatePlayerContentLanguage) {
            state = AppState(playerId, Screen.Home)
        }
    }
    val currentTab = state.screen.asTab()
    if (currentTab != null || state.screen is Screen.Article) {
        val tabToPass = currentTab ?: MainTab.KnowledgeCatalog
        val fromQuiz = (state.screen as? Screen.Article)?.fromQuiz ?: false
        val hapticEnabled by app.preferences.hapticFeedback.collectAsStateWithLifecycle()
        val haptic = rememberHapticTap(hapticEnabled)
        val contentLang by app.preferences.contentLanguage.collectAsStateWithLifecycle()

        val articleViewModel: ArticleViewModel? = if (state.screen is Screen.Article) viewModel() else null
        val articleState by articleViewModel?.state?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(ArticleUiState()) }

        val articleSlug = (state.screen as? Screen.Article)?.slug
        LaunchedEffect(articleSlug, contentLang, playerId) {
            if (articleSlug != null) {
                articleViewModel?.load(articleSlug, contentLang, playerId)
            }
        }

        var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
        var unavailableDialogLang by rememberSaveable { mutableStateOf<String?>(null) }

        val portraitTopBar: @Composable () -> Unit = if (state.screen is Screen.Article) {
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        state = state.goBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(if (fromQuiz) R.string.back_to_quiz else R.string.profile_back)
                        )
                    }
                    IconButton(
                        onClick = {
                            articleState.previousSlug?.let { slug ->
                                haptic(HapticFeedbackType.TextHandleMove)
                                articleViewModel?.selectArticle(slug)
                            }
                        },
                        enabled = articleState.previousSlug != null,
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.article_nav_prev))
                    }
                    IconButton(
                        onClick = {
                            articleState.nextSlug?.let { slug ->
                                haptic(HapticFeedbackType.TextHandleMove)
                                articleViewModel?.selectArticle(slug)
                            }
                        },
                        enabled = articleState.nextSlug != null,
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.article_nav_next))
                    }
                    Spacer(Modifier.weight(1f))
                    LanguageSwitcher(
                        availableLanguages = articleState.availableLanguages,
                        currentLanguage = contentLang,
                        onLanguageSelected = { app.preferences.setContentLanguage(it) },
                        unavailableLanguages = articleState.unavailableLanguages,
                        onUnavailableTapped = { unavailableDialogLang = it },
                        hapticEnabled = hapticEnabled,
                        trailingIcon = true,
                    )
                }
            }
        } else if (state.screen == Screen.Home || state.screen == Screen.KnowledgeCatalog) {
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            state = state.navigate(Screen.Search)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_articles)
                        )
                    }
                }
            }
        } else {
            {}
        }

        val railExtrasGroup1: @Composable ColumnScope.() -> Unit = if (state.screen is Screen.Article) {
            {
                NavigationRailItem(
                    selected = false,
                    enabled = articleState.previousSlug != null,
                    onClick = {
                        articleState.previousSlug?.let { slug ->
                            haptic(HapticFeedbackType.LongPress)
                            articleViewModel?.selectArticle(slug)
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.article_nav_prev)) },
                    label = { Text(stringResource(R.string.article_nav_prev)) },
                )
                NavigationRailItem(
                    selected = false,
                    enabled = articleState.nextSlug != null,
                    onClick = {
                        articleState.nextSlug?.let { slug ->
                            haptic(HapticFeedbackType.LongPress)
                            articleViewModel?.selectArticle(slug)
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.article_nav_next)) },
                    label = { Text(stringResource(R.string.article_nav_next)) },
                )
            }
        } else {
            {}
        }

        val railExtrasGroup2: @Composable ColumnScope.() -> Unit = if (state.screen is Screen.Article) {
            {
                if (fromQuiz) {
                    NavigationRailItem(
                        selected = false,
                        onClick = {
                            haptic(HapticFeedbackType.LongPress)
                            state = state.goBack()
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.back_to_quiz)) },
                        label = { Text(stringResource(R.string.back_to_quiz)) },
                    )
                }
                NavigationRailItem(
                    selected = false,
                    enabled = articleState.availableLanguages.isNotEmpty(),
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        showLanguageDialog = true
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_language_globe),
                            contentDescription = stringResource(R.string.settings_language_title),
                        )
                    },
                    label = { Text(contentLang.uppercase()) },
                )
            }
        } else {
            {}
        }

        MainScaffold(
            currentTab = tabToPass,
            isRailOnLeft = state.isRailOnLeft,
            hapticEnabled = hapticEnabled,
            hideTabs = fromQuiz,
            portraitTopBar = portraitTopBar,
            railExtrasGroup1 = railExtrasGroup1,
            railExtrasGroup2 = railExtrasGroup2,
            onTabSelected = { tab -> state = state.navigateToTab(tab) },
            onToggleRailSide = { state = state.toggleRailSide() },
        ) {
            when (state.screen) {
                Screen.Home -> PlayerHomeScreen(
                    playerId = playerId,
                    onStartQuiz = { config -> state = state.navigate(Screen.Quiz(config)) },
                    onOpenProfile = { state = state.navigate(Screen.Profile) },
                    onOpenDomainsSettings = { state = state.navigate(Screen.SettingsDomains) },
                )
                Screen.QuizCatalog -> QuizCatalogScreen()
                Screen.KnowledgeCatalog -> KnowledgeCatalogScreen(
                    playerId = playerId,
                    onOpenArticle = { slug ->
                        state = state.navigate(Screen.Article(slug, fromQuiz = false))
                    },
                )
                Screen.Profile -> ProfileScreen(
                    playerId = playerId,
                    onBack = { state = state.goBack() },
                )
                Screen.Saved -> SavedScreen()
                Screen.Settings -> SettingsScreen(
                    playerId = playerId,
                    onOpenUiLanguage = { state = state.navigate(Screen.SettingsUiLanguage) },
                    onOpenLanguage = { state = state.navigate(Screen.SettingsLanguage) },
                    onOpenDomains = { state = state.navigate(Screen.SettingsDomains) },
                    onOpenDeveloper = { state = state.navigate(Screen.DeveloperSettings) },
                    onOpenLookAndFeel = { state = state.navigate(Screen.SettingsLookAndFeel) },
                    onSwitchPlayer = { state = state.startSwitchPlayer() },
                )
                is Screen.Article -> {
                    val originQuizArticleSlug = (state.backStack.lastOrNull() as? Screen.Quiz)
                        ?.config?.articleSlug
                    ArticleScreen(
                        slug = (state.screen as Screen.Article).slug,
                        playerId = playerId,
                        fromQuiz = fromQuiz,
                        originQuizArticleSlug = originQuizArticleSlug,
                        isRailOnLeft = state.isRailOnLeft,
                        onBack = { state = state.goBack() },
                        onStartQuiz = { slugKey ->
                            state = state.navigateToQuiz(
                                QuizConfig(
                                    articleSlug = slugKey,
                                    questionLimit = 5,
                                )
                            )
                        },
                        viewModel = articleViewModel ?: viewModel(),
                    )
                }
                else -> Unit
            }
        }

        if (showLanguageDialog) {
            LanguageSelectionDialog(
                availableLanguages = articleState.availableLanguages,
                currentLanguage = contentLang,
                unavailableLanguages = articleState.unavailableLanguages,
                onLanguageSelected = { app.preferences.setContentLanguage(it) },
                onUnavailableTapped = { unavailableDialogLang = it },
                onDismissRequest = { showLanguageDialog = false },
            )
        }

        if (unavailableDialogLang != null) {
            AlertDialog(
                onDismissRequest = { unavailableDialogLang = null },
                title = { Text(stringResource(R.string.language_unavailable_title)) },
                text = {
                    Text(stringResource(
                        R.string.language_unavailable_message,
                        languageDisplayName(unavailableDialogLang!!),
                    ))
                },
                confirmButton = {
                    TextButton(onClick = { unavailableDialogLang = null }) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
            )
        }
    } else {
        when (val s = state.screen) {
            is Screen.Quiz -> QuizScreen(
                playerId = playerId,
                config = s.config,
                onExit = {
                    if (s.config.articleSlug != null) {
                        state = state.goBack()
                    } else {
                        state = state.popToRoot()
                    }
                },
                onOpenArticle = { slug ->
                    state = state.navigateToArticle(slug, fromQuiz = true)
                },
            )
            Screen.Search -> SearchScreen(
                onOpenArticle = { slug -> state = state.navigate(Screen.Article(slug, fromQuiz = false)) },
                onBack = { state = state.goBack() },
            )
            Screen.SettingsUiLanguage -> SettingsUiLanguageScreen(
                onBack = { state = state.goBack() },
            )
            Screen.SettingsLanguage -> SettingsLanguageScreen(
                playerId = playerId,
                onBack = { state = state.goBack() },
            )
            Screen.SettingsDomains -> SettingsDomainsScreen(
                playerId = playerId,
                onBack = { state = state.goBack() },
            )
            Screen.DeveloperSettings -> DeveloperSettingsScreen(
                onBack = { state = state.goBack() },
            )
            Screen.SettingsLookAndFeel -> SettingsLookAndFeelScreen(
                onBack = { state = state.goBack() },
            )
            else -> Unit
        }
    }
}
