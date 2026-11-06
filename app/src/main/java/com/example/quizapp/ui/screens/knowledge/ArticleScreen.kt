package com.example.quizapp.ui.screens.knowledge

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quizapp.R
import com.example.quizapp.data.knowledge.ArticleMeta
import com.example.quizapp.data.knowledge.LatexPatterns
import com.example.quizapp.data.knowledge.MarkdownPreprocessor
import com.example.quizapp.ui.common.LatexInlineCache
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownInlineContent

/**
 * Composable screen for displaying a knowledge base article with horizontal swipe navigation
 * between domain articles, inline LaTeX math rendering, and quiz launching.
 *
 * @param slug The article slug identifier.
 * @param playerId The active player ID.
 * @param fromQuiz Whether the article was opened as a detour from an active quiz.
 * @param originQuizArticleSlug Origin article slug if opened from a quiz.
 * @param onBack Callback to navigate back.
 * @param onStartQuiz Callback to start or resume a quiz for the article.
 * @param viewModel ViewModel backing the article screen.
 */
@Composable
fun ArticleScreen(
    slug: String,
    playerId: Long,
    fromQuiz: Boolean = false,
    originQuizArticleSlug: String? = null,
    isRailOnLeft: Boolean = true,
    onBack: () -> Unit,
    onStartQuiz: (slugKey: String) -> Unit = {},
    viewModel: ArticleViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val measurer = rememberLatexMeasurer()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isSwapped = isLandscape && isRailOnLeft

    val quizFabAlignment = if (isSwapped) Alignment.BottomStart else Alignment.BottomEnd
    val feedbackFabAlignment = if (isSwapped) Alignment.BottomEnd else Alignment.BottomStart

    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.errorResId != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(state.errorResId!!),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.domainArticles.isNotEmpty() -> {
                val pagerState = rememberPagerState(
                    initialPage = state.currentIndex,
                    pageCount = { state.domainArticles.size },
                )

                LaunchedEffect(state.currentIndex) {
                    if (pagerState.currentPage != state.currentIndex) {
                        pagerState.animateScrollToPage(state.currentIndex)
                    }
                }

                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage in state.domainArticles.indices) {
                        val targetSlug = state.domainArticles[pagerState.currentPage].slug
                        viewModel.selectArticle(targetSlug)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val meta = state.domainArticles[page]
                    val article = state.cachedArticles[meta.slug]
                    if (article != null) {
                        ArticlePageView(
                            meta = meta,
                            articleContent = article.content,
                            measurer = measurer,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                val currentMeta = state.domainArticles.getOrNull(pagerState.currentPage)
                val hasQuiz = currentMeta?.let { state.quizAvailabilityMap[it.slug] } ?: false

                val canResumeQuiz = fromQuiz &&
                    currentMeta != null &&
                    currentMeta.slug == originQuizArticleSlug
                val canStartQuiz = !fromQuiz
                if (currentMeta != null && (canResumeQuiz || canStartQuiz) && hasQuiz) {
                    FloatingActionButton(
                        onClick = { onStartQuiz(currentMeta.slug) },
                        modifier = Modifier
                            .align(quizFabAlignment)
                            .padding(24.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            imageVector = if (canResumeQuiz) Icons.AutoMirrored.Filled.ArrowForward
                                          else Icons.Default.PlayArrow,
                            contentDescription = if (canResumeQuiz) stringResource(R.string.quiz_continue_button) else stringResource(R.string.quiz_check_knowledge_button),
                        )
                    }
                }

                if (currentMeta != null) {
                    FloatingActionButton(
                        onClick = { showFeedbackDialog = true },
                        modifier = Modifier
                            .align(feedbackFabAlignment)
                            .padding(24.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_flag),
                            contentDescription = stringResource(R.string.feedback_button_cd),
                        )
                    }
                }

                if (showFeedbackDialog && currentMeta != null) {
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
                                    viewModel.submitArticleFeedback(currentMeta.slug, selectedReason, comment)
                                    showFeedbackDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.feedback_submit))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showFeedbackDialog = false }
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticlePageView(
    meta: ArticleMeta,
    articleContent: String,
    measurer: LatexMeasurerState,
) {
    val processedContent = remember(articleContent) {
        MarkdownPreprocessor.preprocess(articleContent)
    }
    val formulaSize = MaterialTheme.typography.bodyLarge.fontSize
    var inlineContent by remember(processedContent) {
        mutableStateOf<Map<String, InlineTextContent>>(emptyMap())
    }
    LaunchedEffect(processedContent, measurer, formulaSize) {
        val formulas = LatexPatterns.extractInlineFormulas(processedContent).toSet()
        val built = buildMap<String, InlineTextContent> {
            formulas.forEach { latex ->
                val id = "$LATEX_INLINE_ID_PREFIX${latex.hashCode()}"
                LatexInlineCache.get(measurer, latex, formulaSize)?.let { put(id, it) }
            }
        }
        inlineContent = built
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 80.dp),
    ) {
        Text(
            text = meta.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
        Markdown(
            content = processedContent,
            components = latexMarkdownComponents,
            annotator = latexAnnotator(),
            inlineContent = markdownInlineContent(content = inlineContent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
