package com.example.quizapp.ui.screens.quiz

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.QuizApplication
import com.example.quizapp.QuizConfig
import com.example.quizapp.R
import com.example.quizapp.data.entity.Distractor
import com.example.quizapp.engine.QuestionType
import com.example.quizapp.engine.QuizQuestion
import com.example.quizapp.ui.common.LanguageSwitcher
import com.example.quizapp.ui.common.MathText
import com.example.quizapp.ui.common.ProvideLatexMeasurer
import com.example.quizapp.ui.common.languageDisplayName
import com.example.quizapp.ui.common.rememberHapticTap
import com.example.quizapp.ui.theme.QuizAppTheme

/**
 * Top-level Composable screen managing active quiz sessions, exit confirmation handling, and question navigation.
 *
 * @param playerId Unique ID of the player taking the quiz.
 * @param config Configuration parameters for the quiz session.
 * @param onExit Callback invoked when exiting the quiz.
 * @param onOpenArticle Callback invoked to open a related knowledge article.
 * @param viewModel ViewModel managing quiz state and session logic.
 */
@Composable
fun QuizScreen(
    playerId: Long,
    config: QuizConfig,
    onExit: () -> Unit,
    onOpenArticle: (String) -> Unit,
    viewModel: QuizViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as QuizApplication
    val contentLang by app.preferences.contentLanguage.collectAsStateWithLifecycle()
    val hapticEnabled by app.preferences.hapticFeedback.collectAsStateWithLifecycle()
    val haptic = rememberHapticTap(hapticEnabled)
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(playerId, config, contentLang) {
        viewModel.load(playerId, config)
    }
    BackHandler(enabled = !state.finished && !state.isLoading) {
        showExitDialog = true
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val exitDialogAlignment =
        if (isLandscape && !state.isSwapped) Alignment.TopEnd else Alignment.TopStart

    if (showExitDialog) {
        AlignedAlertDialog(
            alignment = exitDialogAlignment,
            onDismissRequest = { showExitDialog = false },
            title = stringResource(R.string.quiz_exit_dialog_title),
            text = stringResource(R.string.quiz_exit_dialog_message),
            confirmText = stringResource(R.string.quiz_exit_confirm),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                haptic(HapticFeedbackType.LongPress)
                showExitDialog = false
                viewModel.reset()
                onExit()
            },
            onDismiss = {
                haptic(HapticFeedbackType.LongPress)
                showExitDialog = false
            }
        )
    }

    if (showFeedbackDialog) {
        var selectedReason by rememberSaveable { mutableStateOf("wrong_answer") }
        var comment by rememberSaveable { mutableStateOf("") }
        val reasons = listOf(
            "wrong_answer" to R.string.feedback_reason_wrong,
            "typo" to R.string.feedback_reason_typo,
            "bad_translation" to R.string.feedback_reason_translation,
            "unclear_hint" to R.string.feedback_reason_hint,
            "other" to R.string.feedback_reason_other,
        )

        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text(stringResource(R.string.feedback_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(R.string.feedback_reason_label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    reasons.forEach { (code, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = code }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == code,
                                onClick = { selectedReason = code }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(labelRes))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { if (it.length <= 500) comment = it },
                        label = { Text(stringResource(R.string.feedback_comment_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        viewModel.submitFeedback(selectedReason, comment)
                        showFeedbackDialog = false
                    }
                ) {
                    Text(stringResource(R.string.feedback_submit))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        haptic(HapticFeedbackType.LongPress)
                        showFeedbackDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val isArticleQuiz = config.articleSlug != null

    ProvideLatexMeasurer {
        QuizContent(
            state = state,
            contentLang = contentLang,
            hapticEnabled = hapticEnabled,
            isArticleQuiz = isArticleQuiz,
            onAnswer = { answer ->
                haptic(HapticFeedbackType.LongPress)
                viewModel.selectAnswer(answer, playerId)
            },
            onSubmitConstruct = {
                haptic(HapticFeedbackType.LongPress)
                viewModel.submitConstruct(playerId)
            },
            onTokenSelect = { tokenId ->
                haptic(HapticFeedbackType.TextHandleMove)
                viewModel.onConstructTokenSelected(tokenId)
            },
            onTokenDeselect = { tokenId ->
                haptic(HapticFeedbackType.TextHandleMove)
                viewModel.onConstructTokenDeselected(tokenId)
            },
            onRevealHint = {
                haptic(HapticFeedbackType.TextHandleMove)
                viewModel.revealNextHint()
            },
            onAdvance = {
                haptic(HapticFeedbackType.LongPress)
                viewModel.advance()
            },
            onRestart = {
                haptic(HapticFeedbackType.LongPress)
                viewModel.reset()
            },
            onLanguageSelected = { lang ->
                haptic(HapticFeedbackType.LongPress)
                app.preferences.setContentLanguage(lang)
            },
            onRequestExit = {
                haptic(HapticFeedbackType.LongPress)
                if (state.finished || state.isLoading) {
                    viewModel.reset()
                    onExit()
                } else {
                    showExitDialog = true
                }
            },
            onOpenArticle = { slug ->
                haptic(HapticFeedbackType.LongPress)
                onOpenArticle(slug)
            },
            onToggleWhy = {
                haptic(HapticFeedbackType.TextHandleMove)
                viewModel.toggleWhy()
            },
            onToggleSwap = {
                haptic(HapticFeedbackType.LongPress)
                viewModel.toggleSwap()
            },
            onReportFeedback = {
                haptic(HapticFeedbackType.LongPress)
                showFeedbackDialog = true
            },
        )
    }
}

@Composable
internal fun QuizContent(
    state: QuizUiState,
    contentLang: String,
    hapticEnabled: Boolean = true,
    isArticleQuiz: Boolean = false,
    onAnswer: (String) -> Unit,
    onSubmitConstruct: () -> Unit = {},
    onTokenSelect: (Int) -> Unit = {},
    onTokenDeselect: (Int) -> Unit = {},
    onRevealHint: () -> Unit,
    onAdvance: () -> Unit,
    onRestart: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onRequestExit: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onToggleWhy: () -> Unit,
    onToggleSwap: () -> Unit = {},
    onReportFeedback: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        val errorMessage = state.errorMessage
        val errorResId = state.errorResId
        when {
            state.isLoading -> LoadingScreen()
            errorMessage != null -> ErrorScreen(message = errorMessage)
            errorResId != null -> ErrorScreen(messageResId = errorResId)
            state.finished -> FinishedScreen(
                score = state.score,
                total = state.questions.size,
                isArticleQuiz = isArticleQuiz,
                onRestart = onRestart,
                onExit = onRequestExit,
            )

            else -> {
                if (isLandscape) {
                    QuestionScreenLandscape(
                        state = state,
                        contentLang = contentLang,
                        hapticEnabled = hapticEnabled,
                        isSwapped = state.isSwapped,
                        onSwap = onToggleSwap,
                        onAnswer = onAnswer,
                        onSubmitConstruct = onSubmitConstruct,
                        onTokenSelect = onTokenSelect,
                        onTokenDeselect = onTokenDeselect,
                        onRevealHint = onRevealHint,
                        onAdvance = onAdvance,
                        onExit = onRequestExit,
                        onLanguageSelected = onLanguageSelected,
                        onOpenArticle = onOpenArticle,
                        onToggleWhy = onToggleWhy,
                        onReportFeedback = onReportFeedback,
                    )
                } else {
                    QuestionScreen(
                        state = state,
                        contentLang = contentLang,
                        hapticEnabled = hapticEnabled,
                        onAnswer = onAnswer,
                        onSubmitConstruct = onSubmitConstruct,
                        onTokenSelect = onTokenSelect,
                        onTokenDeselect = onTokenDeselect,
                        onRevealHint = onRevealHint,
                        onAdvance = onAdvance,
                        onExit = onRequestExit,
                        onLanguageSelected = onLanguageSelected,
                        onOpenArticle = onOpenArticle,
                        onToggleWhy = onToggleWhy,
                        onReportFeedback = onReportFeedback,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorScreen(
    message: String? = null,
    @StringRes messageResId: Int? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        val text = message ?: messageResId?.let {
            stringResource(
                R.string.error_generic,
                stringResource(it)
            )
        } ?: ""
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun FinishedScreen(
    score: Int,
    total: Int,
    isArticleQuiz: Boolean = false,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.quiz_finished_score, score, total),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRestart) {
            Text(stringResource(R.string.quiz_play_again))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onExit,
        ) {
            Text(stringResource(if (isArticleQuiz) R.string.back_to_article else R.string.quiz_return_home))
        }
    }
}

// ===== PORTRAIT =====

@Composable
private fun QuestionScreen(
    state: QuizUiState,
    contentLang: String,
    hapticEnabled: Boolean = true,
    onAnswer: (String) -> Unit,
    onSubmitConstruct: () -> Unit = {},
    onTokenSelect: (Int) -> Unit = {},
    onTokenDeselect: (Int) -> Unit = {},
    onRevealHint: () -> Unit,
    onAdvance: () -> Unit,
    onExit: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onToggleWhy: () -> Unit,
    onReportFeedback: () -> Unit,
) {
    val currentQ = state.questions.getOrNull(state.currentIndex)
    val isConstruct = currentQ?.type == QuestionType.CONSTRUCT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HintsPanelPortrait(
            // Top: 4/14. Controls + hints.
            state = state,
            contentLang = contentLang,
            hapticEnabled = hapticEnabled,
            onRevealHint = onRevealHint,
            onLanguageSelected = onLanguageSelected,
            onExit = onExit,
            onOpenArticle = onOpenArticle,
            modifier = Modifier
                .weight(4f)
                .fillMaxWidth(),
        )
        QuestionBlock(
            // Middle: 5/14. Question.
            state = state,
            onTokenDeselect = onTokenDeselect,
            onReportFeedback = onReportFeedback,
            feedbackAlignment = Alignment.BottomEnd,
            modifier = Modifier
                .weight(5f)
                .fillMaxWidth(),
        )
        Box(
            // Bottom: 5/14.
            modifier = Modifier
                .weight(5f)
                .fillMaxWidth(),
        ) {
            if (isConstruct) {
                ConstructAnswersList(
                    state = state,
                    onTokenSelect = onTokenSelect,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AnswersList(
                    state = state,
                    onAnswer = onAnswer,
                    alignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val showBar = isConstruct ||
                    (state.answerFeedback != null && !state.answerFeedback.isCorrect)
            if (showBar) {
                ActionsBar(
                    state = state,
                    onAdvance = onAdvance,
                    onToggleWhy = onToggleWhy,
                    showingWhy = state.showingWhy,
                    isLandscape = false,
                    nextOnStart = false,
                    onSubmitConstruct = onSubmitConstruct,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HintsPanelPortrait(
    state: QuizUiState,
    contentLang: String,
    hapticEnabled: Boolean = true,
    onRevealHint: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onExit: () -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var unavailableTap by rememberSaveable { mutableStateOf<String?>(null) }
    val currentQuestion = state.questions.getOrNull(state.currentIndex)
    val gradientColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    val brush = Brush.verticalGradient(listOf(gradientColor, Color.Transparent))
    Card(
        modifier = modifier.background(brush, shape = CardDefaults.shape),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                TextButton(
                    onClick = onExit,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.quiz_exit))
                }

                Box(modifier = Modifier.align(Alignment.Center)) {
                    HintOrArticleButton(
                        state = state,
                        onRevealHint = onRevealHint,
                        onOpenArticle = onOpenArticle,
                        relatedArticleSlug = currentQuestion?.relatedArticleSlug,
                    )
                }

                LanguageSwitcher(
                    availableLanguages = state.availableLanguages,
                    currentLanguage = contentLang,
                    onLanguageSelected = onLanguageSelected,
                    unavailableLanguages = state.unavailableLanguages,
                    onUnavailableTapped = { unavailableTap = it },
                    enabled = true,
                    hapticEnabled = hapticEnabled,
                    trailingIcon = true,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            Spacer(Modifier.height(4.dp))
            HintsScroll(
                state = state,
                alignment = Alignment.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }

    if (unavailableTap != null) {
        AlertDialog(
            onDismissRequest = { unavailableTap = null },
            title = { Text(stringResource(R.string.language_unavailable_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.language_unavailable_message,
                        languageDisplayName(unavailableTap!!),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { unavailableTap = null }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

// ===== LANDSCAPE =====

@Composable
private fun QuestionScreenLandscape(
    state: QuizUiState,
    contentLang: String,
    hapticEnabled: Boolean = true,
    isSwapped: Boolean,
    onSwap: () -> Unit,
    onAnswer: (String) -> Unit,
    onSubmitConstruct: () -> Unit = {},
    onTokenSelect: (Int) -> Unit = {},
    onTokenDeselect: (Int) -> Unit = {},
    onRevealHint: () -> Unit,
    onAdvance: () -> Unit,
    onExit: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onToggleWhy: () -> Unit,
    onReportFeedback: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hintsPanel: @Composable (Modifier, Boolean) -> Unit = { modifier, mirrored ->
            LandscapeHintsPanel(
                state = state,
                contentLang = contentLang,
                hapticEnabled = hapticEnabled,
                onRevealHint = onRevealHint,
                onLanguageSelected = onLanguageSelected,
                onSwap = onSwap,
                onOpenArticle = onOpenArticle,
                mirrored = mirrored,
                modifier = modifier,
            )
        }
        val feedbackAlignment = if (isSwapped) Alignment.BottomStart else Alignment.BottomEnd

        val centerPanel: @Composable (Modifier) -> Unit = { modifier ->
            QuestionBlock(
                // Landscape
                state = state,
                onTokenDeselect = onTokenDeselect,
                onReportFeedback = onReportFeedback,
                feedbackAlignment = feedbackAlignment,
                modifier = modifier,
            )
        }
        val answersPanel: @Composable (Modifier, Boolean) -> Unit = { modifier, exitOnStart ->
            LandscapeAnswersPanel(
                state = state,
                onAnswer = onAnswer,
                onSubmitConstruct = onSubmitConstruct,
                onTokenSelect = onTokenSelect,
                onTokenDeselect = onTokenDeselect,
                onAdvance = onAdvance,
                onExit = onExit,
                onToggleWhy = onToggleWhy,
                onSwap = onSwap,
                exitOnStart = exitOnStart,
                modifier = modifier,
            )
        }
        // Width: 3 : 4 : 3
        if (isSwapped) {
            answersPanel(Modifier
                .weight(3f)
                .fillMaxHeight(), true)
            centerPanel(Modifier
                .weight(4f)
                .fillMaxHeight())
            hintsPanel(Modifier
                .weight(3f)
                .fillMaxHeight(), true)
        } else {
            hintsPanel(Modifier
                .weight(3f)
                .fillMaxHeight(), false)
            centerPanel(Modifier
                .weight(4f)
                .fillMaxHeight())
            answersPanel(Modifier
                .weight(3f)
                .fillMaxHeight(), false)
        }
    }
}

@Composable
private fun LandscapeHintsPanel(
    state: QuizUiState,
    contentLang: String,
    hapticEnabled: Boolean = true,
    onRevealHint: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onSwap: () -> Unit,
    onOpenArticle: (String) -> Unit,
    mirrored: Boolean,
    modifier: Modifier = Modifier,
) {
    var unavailableTap by rememberSaveable { mutableStateOf<String?>(null) }
    val currentQuestion = state.questions.getOrNull(state.currentIndex)
    val gradientColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    val brush = Brush.verticalGradient(listOf(Color.Transparent, gradientColor))
    Box(
        modifier = modifier.background(brush, shape = CardDefaults.shape),
    ) {
        HintsScroll(
            state = state,
            alignment = if (mirrored) Alignment.End else Alignment.Start,
            topSpacerHeight = 48.dp,
            bottomSpacerHeight = 16.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        )

        // Top overlay: controls
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            // Left corner
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                if (mirrored) {
                    SwapIconButton(onSwap)
                } else {
                    LanguageSwitcher(
                        availableLanguages = state.availableLanguages,
                        currentLanguage = contentLang,
                        onLanguageSelected = onLanguageSelected,
                        unavailableLanguages = state.unavailableLanguages,
                        onUnavailableTapped = { unavailableTap = it },
                        enabled = true,
                        hapticEnabled = hapticEnabled,
                        trailingIcon = false,
                    )
                }
            }

            // Center — always hint/article, always dead center
            Box(modifier = Modifier.align(Alignment.Center)) {
                HintOrArticleButton(
                    state = state,
                    onRevealHint = onRevealHint,
                    onOpenArticle = onOpenArticle,
                    relatedArticleSlug = currentQuestion?.relatedArticleSlug,
                )
            }

            // Right corner
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                if (mirrored) {
                    LanguageSwitcher(
                        availableLanguages = state.availableLanguages,
                        currentLanguage = contentLang,
                        onLanguageSelected = onLanguageSelected,
                        unavailableLanguages = state.unavailableLanguages,
                        onUnavailableTapped = { unavailableTap = it },
                        enabled = true,
                        hapticEnabled = hapticEnabled,
                        trailingIcon = true,
                    )
                } else {
                    SwapIconButton(onSwap)
                }
            }
        }
    }

    if (unavailableTap != null) {
        AlertDialog(
            onDismissRequest = { unavailableTap = null },
            title = { Text(stringResource(R.string.language_unavailable_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.language_unavailable_message,
                        languageDisplayName(unavailableTap!!),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { unavailableTap = null }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun LandscapeAnswersPanel(
    state: QuizUiState,
    onAnswer: (String) -> Unit,
    onSubmitConstruct: () -> Unit = {},
    onTokenSelect: (Int) -> Unit = {},
    onTokenDeselect: (Int) -> Unit = {},
    onAdvance: () -> Unit,
    onExit: () -> Unit,
    onToggleWhy: () -> Unit,
    onSwap: () -> Unit,
    exitOnStart: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentQ = state.questions.getOrNull(state.currentIndex)
    val isConstruct = currentQ?.type == QuestionType.CONSTRUCT
    val gradientColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    val brush = Brush.verticalGradient(listOf(Color.Transparent, gradientColor))
    Box(
        modifier = modifier.background(brush, shape = CardDefaults.shape),
    ) {
        if (isConstruct) {
            ConstructAnswersList(
                state = state,
                onTokenSelect = onTokenSelect,
                topSpacerHeight = 48.dp,
                bottomSpacerHeight = 48.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            )
        } else {
            AnswersList(
                state = state,
                onAnswer = onAnswer,
                alignment = if (exitOnStart) Alignment.Start else Alignment.End,
                topSpacerHeight = 48.dp,
                bottomSpacerHeight = 48.dp,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            )
        }
        Row(
            // Top overlay: swap + exit
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (exitOnStart) {
                ExitIconButton(onExit)
                SwapIconButton(onSwap)
            } else {
                SwapIconButton(onSwap)
                ExitIconButton(onExit)
            }
        }

        // Bottom overlay: actions
        val showBar = isConstruct ||
                (state.answerFeedback != null && !state.answerFeedback.isCorrect)
        if (showBar) {
            ActionsBar(
                state = state,
                onAdvance = onAdvance,
                onToggleWhy = onToggleWhy,
                showingWhy = state.showingWhy,
                isLandscape = true,
                nextOnStart = !exitOnStart,
                onSubmitConstruct = onSubmitConstruct,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SwapIconButton(onSwap: () -> Unit) {
    IconButton(onClick = onSwap) {
        Icon(
            painter = painterResource(id = R.drawable.ic_swap_horizontal),
            contentDescription = stringResource(R.string.swap_positions),
        )
    }
}

@Composable
private fun ExitIconButton(onExit: () -> Unit) {
    IconButton(onClick = onExit) {
        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.quiz_exit))
    }
}

@Composable
private fun FeedbackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_flag),
            contentDescription = stringResource(R.string.feedback_button_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===== SHARED BLOCKS =====

@Composable
private fun HintsScroll(
    state: QuizUiState,
    alignment: Alignment.Horizontal = Alignment.End,
    topSpacerHeight: Dp = 0.dp,
    bottomSpacerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.currentIndex) {
        scrollState.scrollTo(0)
    }
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (topSpacerHeight > 0.dp) {
                Spacer(Modifier.height(topSpacerHeight))
            }
            val revealedHints = state.currentHints.take(state.revealedHintsCount)
            if (revealedHints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.currentHints.isEmpty()) {
                            stringResource(R.string.quiz_no_hints)
                        } else {
                            stringResource(R.string.quiz_hints_placeholder)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                revealedHints.forEach { hintText ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        MathText(
                            text = "💡 $hintText",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            if (bottomSpacerHeight > 0.dp) {
                Spacer(Modifier.height(bottomSpacerHeight))
            }
        }
    }
}

@Composable
private fun ConstructAnswersList(
    state: QuizUiState,
    onTokenSelect: (Int) -> Unit,
    topSpacerHeight: Dp = 0.dp,
    bottomSpacerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val usedIds = state.constructSelected.toSet()
    val locked = state.answerFeedback != null

    Column(
        modifier = modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (topSpacerHeight > 0.dp) {
            Spacer(Modifier.height(topSpacerHeight))
        }
        // Pool
        val availableTokens = state.constructPool.filter { it.id !in usedIds }
        if (availableTokens.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.construct_all_tokens_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                availableTokens.forEach { token ->
                    val selectDesc = stringResource(R.string.cd_select_token, token.text)
                    OutlinedButton(
                        onClick = { onTokenSelect(token.id) },
                        enabled = !locked,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 0.dp)
                            .semantics { contentDescription = selectDesc },
                    ) {
                        MathText(token.text)
                    }
                }
            }
        }
        if (bottomSpacerHeight > 0.dp) {
            Spacer(Modifier.height(bottomSpacerHeight))
        }
    }
}

@Composable
private fun AnswersList(
    state: QuizUiState,
    onAnswer: (String) -> Unit,
    alignment: Alignment.Horizontal = Alignment.End,
    topSpacerHeight: Dp = 0.dp,
    bottomSpacerHeight: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(
        4.dp,
        Alignment.CenterVertically
    ),
    modifier: Modifier = Modifier,
) {
    val question = state.questions.getOrNull(state.currentIndex)
    val feedback = state.answerFeedback
    val displayedOptions = state.currentOptions
    val scrollState = rememberScrollState()
    LaunchedEffect(state.currentIndex) {
        scrollState.scrollTo(0)
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .heightIn(min = viewportHeight),
            verticalArrangement = verticalArrangement,
        ) {
            if (topSpacerHeight > 0.dp) {
                Spacer(Modifier.height(topSpacerHeight))
            }
            displayedOptions.forEach { option ->
                val isCorrectAnswer = question != null && option == question.correctAnswer
                val isSelectedAnswer = option == feedback?.selectedAnswer
                val (containerColor, contentColor) = when {
                    feedback == null ->
                        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

                    isCorrectAnswer ->
                        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

                    isSelectedAnswer ->
                        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

                    else ->
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) to MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.6f
                        )
                }
                Button(
                    onClick = { if (feedback == null) onAnswer(option) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MathText(
                        text = option,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (bottomSpacerHeight > 0.dp) {
                Spacer(Modifier.height(bottomSpacerHeight))
            }
        }
        // Top scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, Color.Transparent)
                    )
                )
        )
        // Bottom scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
        )
    }
}

@Composable
private fun ActionsBar(
    state: QuizUiState,
    onAdvance: () -> Unit,
    onToggleWhy: () -> Unit,
    showingWhy: Boolean,
    isLandscape: Boolean,
    nextOnStart: Boolean = true,
    onSubmitConstruct: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val feedback = state.answerFeedback
    val currentQ = state.questions.getOrNull(state.currentIndex)
    val isConstruct = currentQ?.type == QuestionType.CONSTRUCT
    val isPreSubmit = isConstruct && feedback == null

    if (!isPreSubmit && (feedback == null || feedback.isCorrect)) {
        return
    }

    val gradientColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    val brush = if (isLandscape) {
        Brush.verticalGradient(listOf(Color.Transparent, gradientColor))
    } else {
        Brush.verticalGradient(listOf(gradientColor, Color.Transparent))
    }

    Row(
        modifier = modifier
            .background(brush, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPreSubmit) {
            Button(
                onClick = onSubmitConstruct,
                enabled = state.constructSelected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.quiz_check))
            }
        } else {
            val whyButton: @Composable () -> Unit = {
                Button(
                    onClick = onToggleWhy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(
                            if (showingWhy) R.string.quiz_why_ok_button else R.string.quiz_why_button
                        )
                    )
                }
            }
            val nextButton: @Composable () -> Unit = {
                Button(
                    onClick = onAdvance,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.quiz_next))
                }
            }
            if (nextOnStart) {
                nextButton()
                whyButton()
            } else {
                whyButton()
                nextButton()
            }
        }
    }
}

// ===== MODULAR BLOCKS =====

@Composable
private fun QuestionBlock(
    state: QuizUiState,
    onTokenDeselect: (Int) -> Unit = {},
    onReportFeedback: () -> Unit,
    feedbackAlignment: Alignment,
    modifier: Modifier = Modifier,
) {
    val question = state.questions.getOrNull(state.currentIndex)
    val explanation = state.answerFeedback?.explanation
    val showingExplanation = state.showingWhy && explanation != null
    val displayText = if (showingExplanation) {
        explanation
    } else {
        question?.text.orEmpty()
    }
    val isConstruct = question?.type == QuestionType.CONSTRUCT
    val selectedIds = state.constructSelected
    val locked = state.answerFeedback != null
    val byId = remember(state.constructPool) { state.constructPool.associateBy { it.id } }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .then(if (isConstruct) Modifier.padding(bottom = 64.dp) else Modifier),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                ProgressBarPlaceholder(
                    currentIndex = state.currentIndex,
                    totalQuestions = state.questions.size
                )

                Spacer(Modifier.height(16.dp))

                if (question != null) {
                    android.util.Log.d("MathDebug", "raw question text: ${question.text}")
                    android.util.Log.d("MathDebug", "chars: ${question.text.map { it.code }}")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (showingExplanation) {
                            ExplanationIndicator(modifier = Modifier.padding(top = 2.dp))
                        } else {
                            QuestionIndicator(modifier = Modifier.padding(top = 2.dp))
                        }
                        MathText(
                            text = displayText,
                            style = if (showingExplanation) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall,
                            color = if (showingExplanation) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isConstruct && selectedIds.isNotEmpty()) {
                // Scrim behind the strip so long question text does not clash with chips
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = 64.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 56.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        selectedIds.forEach { id ->
                            val token = byId[id] ?: return@forEach
                            val deselectDesc =
                                stringResource(R.string.cd_deselect_token, token.text)
                            AssistChip(
                                onClick = { if (!locked) onTokenDeselect(id) },
                                enabled = !locked,
                                label = { MathText(token.text) },
                                modifier = Modifier.semantics { contentDescription = deselectDesc },
                            )
                        }
                    }
                }
            }

            FeedbackIconButton(
                onClick = onReportFeedback,
                modifier = Modifier
                    .align(feedbackAlignment)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun QuestionIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ExplanationIndicator(modifier: Modifier = Modifier) {
    val triangleColor = MaterialTheme.colorScheme.secondaryContainer
    val textColor = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width / 2f, 2.dp.toPx())
                lineTo(size.width - 2.dp.toPx(), size.height - 2.dp.toPx())
                lineTo(2.dp.toPx(), size.height - 2.dp.toPx())
                close()
            }
            drawPath(path, color = triangleColor)
        }
        Text(
            text = "!",
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

@Composable
private fun HintButton(
    remaining: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (remaining <= 0) return
    val desc = stringResource(R.string.cd_hints_available, remaining)
    TextButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = desc },
    ) {
        Text(
            text = "?".repeat(remaining),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HintOrArticleButton(
    state: QuizUiState,
    onRevealHint: () -> Unit,
    onOpenArticle: (String) -> Unit,
    relatedArticleSlug: String?,
    modifier: Modifier = Modifier,
) {
    val remaining = state.currentHints.size - state.revealedHintsCount
    val feedback = state.answerFeedback
    when {
        feedback != null -> {
            if (relatedArticleSlug != null) {
                ArticleButton(onClick = { onOpenArticle(relatedArticleSlug) }, modifier = modifier)
            }
        }

        remaining > 0 -> HintButton(
            remaining = remaining,
            onClick = onRevealHint,
            modifier = modifier
        )

        relatedArticleSlug != null -> ArticleButton(
            onClick = { onOpenArticle(relatedArticleSlug) },
            modifier = modifier
        )

        else -> {}
    }
}

@Composable
private fun ArticleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_knowledge_book),
            contentDescription = stringResource(R.string.quiz_article_button),
        )
    }
}

@Composable
private fun ProgressBarPlaceholder(
    currentIndex: Int,
    totalQuestions: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val progress = if (totalQuestions > 0) {
            (currentIndex + 1).toFloat() / totalQuestions
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

// ===== PREVIEWS =====

private val sampleQuestion = QuizQuestion(
    id = 1L,
    domainId = 1L,
    difficulty = 1,
    text = "Capital of France?",
    correctAnswer = "Paris",
    distractorPool = listOf(
        Distractor("London"),
        Distractor("Berlin"),
        Distractor("Madrid"),
    ),
    hints = listOf("City of light.", "Eiffel Tower."),
)

private val sampleQuestionWithMath = QuizQuestion(
    id = 2L,
    domainId = 1L,
    difficulty = 1,
    text = "What is the value of \$x\$ if \$x^2 + 3x = 0\$ and \$x \\neq 0\$?",
    correctAnswer = "\$x = -3\$",
    distractorPool = listOf(
        Distractor("\$x = 0\$"),
        Distractor("\$x = 3\$"),
        Distractor("\$x = 9\$"),
    ),
    hints = listOf("Factor out \$x\$.", "Consider \$x(x + 3) = 0\$."),
)

private val stateDefault = QuizUiState(
    isLoading = false,
    questions = listOf(sampleQuestionWithMath),
    currentIndex = 0,
    score = 0,
    finished = false,
    currentHints = sampleQuestion.hints,
    revealedHintsCount = 0,
    answerFeedback = null,
    currentOptions = listOf("Paris", "London", "Berlin", "Madrid"),
    availableLanguages = listOf("en", "ru"),
)

@Preview(
    name = "Default Portrait",
    device = "spec:parent=pixel_5,orientation=portrait",
    showBackground = true
)
@Composable
private fun QuestionScreenPreview_Default() {
    QuizAppTheme {
        QuestionScreen(
            state = stateDefault,
            contentLang = "en",
            onAnswer = {},
            onRevealHint = {},
            onAdvance = {},
            onExit = {},
            onLanguageSelected = {},
            onOpenArticle = {},
            onToggleWhy = {},
            onReportFeedback = {},
        )
    }
}

@Preview(
    name = "Dark Theme Portrait",
    device = "spec:parent=pixel_5,orientation=portrait",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun QuestionScreenPreview_DarkTheme() {
    QuizAppTheme(darkTheme = true) {
        QuestionScreen(
            state = stateDefault,
            contentLang = "en",
            onAnswer = {},
            onRevealHint = {},
            onAdvance = {},
            onExit = {},
            onLanguageSelected = {},
            onOpenArticle = {},
            onToggleWhy = {},
            onReportFeedback = {},
        )
    }
}

@Preview(
    name = "Landscape Mode",
    device = "spec:parent=pixel_5,orientation=landscape",
    showBackground = true
)
@Composable
private fun QuestionScreenPreview_Landscape() {
    QuizAppTheme {
        QuestionScreenLandscape(
            state = stateDefault,
            contentLang = "en",
            isSwapped = false,
            onSwap = {},
            onAnswer = {},
            onRevealHint = {},
            onAdvance = {},
            onExit = {},
            onLanguageSelected = {},
            onOpenArticle = {},
            onToggleWhy = {},
            onReportFeedback = {},
        )
    }
}

@Composable
private fun AlignedAlertDialog(
    alignment: Alignment,
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            contentAlignment = alignment
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(dismissText)
                        }
                        TextButton(onClick = onConfirm) {
                            Text(confirmText)
                        }
                    }
                }
            }
        }
    }
}
