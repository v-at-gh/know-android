package com.example.quizapp

import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.quizapp.data.entity.Distractor
import com.example.quizapp.engine.QuizQuestion
import com.example.quizapp.ui.theme.QuizAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuizLandscapePanelSwapTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleQuestionEn = QuizQuestion(
        id = 1L,
        domainId = 1L,
        difficulty = 1,
        text = "What is 2 + 2?",
        correctAnswer = "4",
        distractorPool = listOf(
            Distractor("3"),
            Distractor("5"),
        ),
        hints = listOf("Think addition."),
    )

    private val sampleQuestionRu = QuizQuestion(
        id = 1L,
        domainId = 1L,
        difficulty = 1,
        text = "Сколько будет 2 + 2?",
        correctAnswer = "4",
        distractorPool = listOf(
            Distractor("3"),
            Distractor("5"),
        ),
        hints = listOf("Сложение."),
    )

    @Test
    fun landscapeSidePanelPositionPreservedWhenLanguageSwitched() {
        var state by mutableStateOf(
            QuizUiState(
                isLoading = false,
                questions = listOf(sampleQuestionEn),
                currentIndex = 0,
                currentHints = sampleQuestionEn.hints,
                revealedHintsCount = 1,
                currentOptions = listOf("4", "3", "5"),
                availableLanguages = listOf("en", "ru"),
            )
        )
        var contentLang by mutableStateOf("en")

        composeTestRule.setContent {
            val currentConfig = androidx.compose.ui.platform.LocalConfiguration.current
            val config = Configuration(currentConfig).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides config
            ) {
                QuizAppTheme {
                    QuizContent(
                        state = state,
                        contentLang = contentLang,
                        onAnswer = {},
                        onRevealHint = {},
                        onAdvance = {},
                        onRestart = {},
                        onLanguageSelected = { newLang -> contentLang = newLang },
                        onRequestExit = {},
                        onOpenArticle = {},
                        onToggleWhy = {},
                        onToggleSwap = { state = state.copy(isSwapped = !state.isSwapped) },
                        onReportFeedback = {},
                    )
                }
            }
        }

        // Verify "Swap positions" button is visible
        composeTestRule.onAllNodesWithContentDescription("Swap positions").onFirst().assertIsDisplayed()

        // Swap the side panels
        composeTestRule.onAllNodesWithContentDescription("Swap positions").onFirst().performClick()

        // Switch content language to "ru"
        contentLang = "ru"
        state = state.copy(
            questions = listOf(sampleQuestionRu),
            currentHints = sampleQuestionRu.hints,
        )
        composeTestRule.waitForIdle()

        // Verify translated text is displayed
        composeTestRule.onNodeWithText("Сколько будет 2 + 2?").assertIsDisplayed()

        // Verify "Swap positions" button is still present and functional
        composeTestRule.onAllNodesWithContentDescription("Swap positions").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Swap positions").onFirst().performClick()
    }
}
