package com.example.quizapp.ui.screens.quiz

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizapp.BuildConfig
import com.example.quizapp.QuizApplication
import com.example.quizapp.QuizConfig
import com.example.quizapp.R
import com.example.quizapp.data.entity.Attempt
import com.example.quizapp.data.entity.Feedback
import com.example.quizapp.engine.AnswerFeedback
import com.example.quizapp.engine.ConstructToken
import com.example.quizapp.engine.QuestionOrdering
import com.example.quizapp.engine.QuestionType
import com.example.quizapp.engine.QuizEngine
import com.example.quizapp.engine.QuizQuestion
import com.example.quizapp.engine.isPlayable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Represents the UI state of an active quiz session.
 *
 * Includes construct-specific fields: [constructPool] (shuffled tokens available to select),
 * [constructSolution] (correct ordered token IDs), and [constructSelected] (tokens currently selected by the user).
 */
data class QuizUiState(
    val isLoading: Boolean = true,
    val questions: List<QuizQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val score: Int = 0,
    val finished: Boolean = false,
    @StringRes val errorResId: Int? = null,
    val errorMessage: String? = null,
    val currentHints: List<String> = emptyList(),
    val revealedHintsCount: Int = 0,
    val answerFeedback: AnswerFeedback? = null,
    val currentOptions: List<String> = emptyList(),
    val availableLanguages: List<String> = emptyList(),
    val unavailableLanguages: Set<String> = emptySet(),
    val showingWhy: Boolean = false,
    val isSwapped: Boolean = false,
    val constructPool: List<ConstructToken> = emptyList(),
    val constructSolution: List<Int> = emptyList(),
    val constructSelected: List<Int> = emptyList(),
) {
    val constructCanSubmit: Boolean
        get() = constructSelected.isNotEmpty()
}

private data class QuizKey(
    val playerId: Long,
    val config: QuizConfig,
    val language: String,
)

/**
 * ViewModel managing quiz execution, question repositories, attempt persistence,
 * hint revelation, and state synchronization between the [QuizEngine] and [QuizUiState].
 *
 * Supports language hot-swapping, multiple choice answer evaluation, and token construction validation.
 */
class QuizViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuizApplication
    private val questionRepository = app.questionRepository
    private val attemptRepository = app.attemptRepository
    private val playerRepository = app.playerRepository
    private val contentLanguage: String
        get() = app.contentLanguage
    private var engine: QuizEngine? = null
    private var advanceJob: Job? = null
    private var loadJob: Job? = null
    private var loadedKey: QuizKey? = null
    private var loadedPlayerId: Long? = null
    private var loadedConfig: QuizConfig? = null
    private var loadedQuestionOrder: List<String>? = null
    private var loadedLanguage: String? = null
    private var playerContentLanguages: List<String> = emptyList()
    private var unavailableLanguages: Set<String> = emptySet()
    private val optionsPatternByQuestionId = mutableMapOf<Long, List<Int>>()

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    fun load(playerId: Long, domainIds: Set<Long>) {
        load(playerId, QuizConfig(domainIds = domainIds))
    }

    /**
     * Loads questions for the specified player and configuration.
     *
     * Implements a "hot-swap" mechanism when switching content languages: preserves the current active
     * question and answer feedback across language transitions if question UUIDs overlap.
     *
     * @param playerId The ID of the active player.
     * @param config The [QuizConfig] parameters.
     */
    fun load(playerId: Long, config: QuizConfig) {
        val language = contentLanguage
        val key = QuizKey(playerId, config, language)
        if (loadedKey == key) return
        loadedKey = key

        loadJob?.cancel()
        advanceJob?.cancel()
        advanceJob = null

        val canHotSwap = engine != null &&
                loadedPlayerId == playerId &&
                loadedConfig == config &&
                loadedQuestionOrder != null &&
                loadedLanguage != null &&
                loadedLanguage != language

        if (!canHotSwap) {
            _state.update {
                it.copy(
                    isLoading = true,
                    errorResId = null,
                    errorMessage = null,
                    isSwapped = false,
                )
            }
        } else {
            _state.update {
                it.copy(
                    errorResId = null,
                    errorMessage = null,
                )
            }
        }

        loadJob = viewModelScope.launch {
            try {
                val player = playerRepository.getById(playerId)
                playerContentLanguages = player?.contentLanguages.orEmpty()
                val withContent = questionRepository.getLanguagesWithContentForDomains(
                    playerLanguages = playerContentLanguages,
                    domainIds = config.domainIds,
                )
                unavailableLanguages = playerContentLanguages.toSet() - withContent

                var newQuestions = questionRepository.getQuizQuestions(
                    language = language,
                    domainIds = config.domainIds,
                    difficulties = config.difficulties,
                    articleSlug = config.articleSlug,
                )

                val preFilterCount = newQuestions.size
                newQuestions = newQuestions.filter { it.isPlayable }
                if (newQuestions.size < preFilterCount) {
                    val dropped = preFilterCount - newQuestions.size
                    Log.w(
                        "QuizViewModel",
                        "Dropped $dropped unplayable question(s) in lang=$language " +
                            "(domainIds=${config.domainIds}, articleSlug=${config.articleSlug})"
                    )
                }

                if (newQuestions.isEmpty()) {
                    val bootstrapper = (getApplication() as? QuizApplication)?.contentBootstrapper
                    val currentImporting = bootstrapper?.importingDomainIds?.value.orEmpty()
                    val isIdle = bootstrapper?.isIdle?.value ?: true
                    val isWaitingForDomains = if (config.domainIds.isEmpty()) {
                        !isIdle || currentImporting.isNotEmpty()
                    } else {
                        config.domainIds.any { it in currentImporting }
                    }

                    if (isWaitingForDomains && bootstrapper != null) {
                        _state.update { it.copy(isLoading = true) }
                        bootstrapper.importingDomainIds.first { importingSet ->
                            if (config.domainIds.isEmpty()) {
                                importingSet.isEmpty()
                            } else {
                                config.domainIds.none { it in importingSet }
                            }
                        }
                        newQuestions = questionRepository.getQuizQuestions(
                            language = language,
                            domainIds = config.domainIds,
                            difficulties = config.difficulties,
                        )
                        val preFilterCount2 = newQuestions.size
                        newQuestions = newQuestions.filter { it.isPlayable }
                        if (newQuestions.size < preFilterCount2) {
                            val dropped = preFilterCount2 - newQuestions.size
                            Log.w(
                                "QuizViewModel",
                                "Dropped $dropped unplayable question(s) in lang=$language " +
                                    "(domainIds=${config.domainIds})"
                            )
                        }
                    }
                }

                if (newQuestions.isEmpty()) {
                    clearEngineState()
                    val availableLangs = withContent.sorted().joinToString(", ")
                    val availableStr = if (availableLangs.isNotBlank()) availableLangs else "none"
                    val msg = "No questions in $language for the selected domains. Available: $availableStr."
                    _state.update {
                        it.copy(
                            isLoading = false,
                            questions = emptyList(),
                            currentIndex = 0,
                            score = 0,
                            finished = false,
                            currentHints = emptyList(),
                            revealedHintsCount = 0,
                            answerFeedback = null,
                            currentOptions = emptyList(),
                            availableLanguages = playerContentLanguages,
                            unavailableLanguages = unavailableLanguages,
                            isSwapped = false,
                            errorResId = R.string.error_no_questions_for_domains,
                            errorMessage = msg,
                        )
                    }
                    return@launch
                }

                if (canHotSwap) {
                    val prevShowingWhy = _state.value.showingWhy
                    val order = loadedQuestionOrder!!
                    val newQuestionsByUuid = newQuestions.associateBy { it.uuid }
                    val intersectionUuids = order.filter { it in newQuestionsByUuid }

                    if (intersectionUuids.isNotEmpty()) {
                        val filteredNewQuestions = intersectionUuids.mapNotNull { newQuestionsByUuid[it] }
                        filteredNewQuestions.forEach { q ->
                            optionsPatternByQuestionId.getOrPut(q.id) { buildPattern(q) }
                        }

                        val currentUuid = engine!!.session.currentQuestion?.uuid
                        val targetUuid = if (currentUuid != null && currentUuid in intersectionUuids) {
                            currentUuid
                        } else {
                            val currentIndexInOld = currentUuid?.let { order.indexOf(it) } ?: -1
                            val followingUuids = if (currentIndexInOld >= 0 && currentIndexInOld < order.size - 1) {
                                order.subList(currentIndexInOld + 1, order.size)
                            } else {
                                emptyList()
                            }
                            followingUuids.firstOrNull { it in intersectionUuids } ?: intersectionUuids.last()
                        }

                        val prevFeedback = engine!!.session.feedback
                        val prevQuestion = engine!!.session.currentQuestion
                        val prevSelectedIndex = if (prevFeedback != null && prevQuestion != null) {
                            displayedOptionsFor(prevQuestion).indexOf(prevFeedback.selectedAnswer)
                        } else -1

                        advanceJob?.cancel()
                        advanceJob = null

                        engine!!.clearFeedback()
                        if (engine!!.replaceQuestions(filteredNewQuestions)) {
                            engine!!.seekTo(targetUuid)
                            if (prevFeedback != null && prevSelectedIndex >= 0) {
                                val newQ = engine!!.session.currentQuestion
                                if (newQ != null) {
                                    val newOpts = displayedOptionsFor(newQ)
                                    val newSelectedText = newOpts.getOrNull(prevSelectedIndex)
                                        ?: prevFeedback.selectedAnswer
                                    val newExplanation = if (prevFeedback.isCorrect) {
                                        newQ.answerWhy
                                    } else {
                                        newQ.distractorPool.firstOrNull { it.text == newSelectedText }?.why
                                    }
                                    engine!!.setFeedback(
                                        AnswerFeedback(newSelectedText, prevFeedback.isCorrect, newExplanation)
                                    )

                                    if (prevFeedback.isCorrect) {
                                        advanceJob = viewModelScope.launch {
                                            delay(FEEDBACK_DELAY_MS.milliseconds)
                                            engine?.advance()
                                            _state.update { it.copy(showingWhy = false) }
                                            publishEngineState()
                                        }
                                    }
                                }
                            }
                            loadedQuestionOrder = filteredNewQuestions.map { it.uuid }
                            loadedLanguage = language
                            _state.update { it.copy(showingWhy = prevShowingWhy, errorMessage = null) }
                            publishEngineState()
                            return@launch
                        }
                    }
                }

                val seed = configSeed(playerId, config)
                val shuffled = QuestionOrdering.shuffledByDifficulty(newQuestions, seed = seed)
                val ordered = config.questionLimit?.takeIf { it > 0 }
                    ?.let { shuffled.take(it) }
                    ?: shuffled
                optionsPatternByQuestionId.clear()
                ordered.forEach { q ->
                    optionsPatternByQuestionId[q.id] = buildPattern(q)
                }
                loadedPlayerId = playerId
                loadedConfig = config
                loadedQuestionOrder = ordered.map { it.uuid }
                loadedLanguage = language
                engine = QuizEngine(ordered)
                _state.update { it.copy(showingWhy = false, errorMessage = null) }
                publishEngineState()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("QuizViewModel", "Failed to load questions", t)
                clearEngineState()
                _state.update {
                    it.copy(
                        isLoading = false,
                        questions = emptyList(),
                        currentIndex = 0,
                        score = 0,
                        finished = false,
                        currentHints = emptyList(),
                        revealedHintsCount = 0,
                        answerFeedback = null,
                        currentOptions = emptyList(),
                        availableLanguages = playerContentLanguages,
                        unavailableLanguages = unavailableLanguages,
                        isSwapped = false,
                        errorResId = R.string.error_questions_load_failed,
                    )
                }
            }
        }
    }

    private fun clearEngineState() {
        engine = null
        loadedKey = null
        loadedPlayerId = null
        loadedConfig = null
        loadedQuestionOrder = null
        loadedLanguage = null
        optionsPatternByQuestionId.clear()
    }

    private fun buildPattern(q: QuizQuestion): List<Int> {
        val n = q.distractorPool.size
        val distractorIndices = (0 until n).shuffled().take(3)
        return (distractorIndices + listOf(-1)).shuffled()
    }

    private fun displayedOptionsFor(q: QuizQuestion): List<String> {
        val pattern = optionsPatternByQuestionId[q.id] ?: return emptyList()
        return pattern.map { idx ->
            if (idx == -1) q.correctAnswer else q.distractorPool.getOrNull(idx)?.text.orEmpty()
        }
    }

    /**
     * Processes a user's multiple-choice answer selection, performing an optimistic UI update
     * via the engine while asynchronously persisting the attempt in the background database.
     *
     * @param selectedAnswer The text of the selected option.
     * @param playerId The ID of the active player.
     */
    fun selectAnswer(selectedAnswer: String, playerId: Long) {
        val eng = engine ?: return
        val currentQ = eng.session.currentQuestion
        if (currentQ?.type == QuestionType.CONSTRUCT) return
        val result = eng.selectAnswer(selectedAnswer) ?: return
        _state.update { it.copy(showingWhy = false) }
        viewModelScope.launch {
            try {
                attemptRepository.insert(
                    Attempt(
                        playerId = playerId,
                        questionUuid = currentQ?.uuid.orEmpty(),
                        domainSlug = currentQ?.domainSlug.orEmpty(),
                        selectedText = result.selectedAnswer,
                        isCorrect = result.isCorrect,
                        hintsUsed = result.hintsUsed,
                        language = contentLanguage,
                    )
                )
            } catch (t: Throwable) {
                Log.e("QuizViewModel", "Failed to save attempt", t)
            }
        }
        publishEngineState()
        advanceJob?.cancel()
        advanceJob = null
        if (result.isCorrect) {
            advanceJob = viewModelScope.launch {
                delay(FEEDBACK_DELAY_MS.milliseconds)
                eng.advance()
                _state.update { it.copy(showingWhy = false) }
                publishEngineState()
            }
        }
    }

    /**
     * Selects a construction token by ID for construct-type questions.
     *
     * @param tokenId The ID of the token to select.
     */
    fun onConstructTokenSelected(tokenId: Int) {
        val s = _state.value
        if (s.answerFeedback != null) return
        if (tokenId in s.constructSelected) return
        _state.update { it.copy(constructSelected = it.constructSelected + tokenId) }
    }

    /**
     * Deselects a construction token by ID for construct-type questions.
     *
     * @param tokenId The ID of the token to deselect.
     */
    fun onConstructTokenDeselected(tokenId: Int) {
        val s = _state.value
        if (s.answerFeedback != null) return
        _state.update { it.copy(constructSelected = it.constructSelected - tokenId) }
    }

    /**
     * Submits the user's selected construction tokens, performing an optimistic UI update
     * via the engine while asynchronously persisting the attempt in the background database.
     *
     * @param playerId The ID of the active player.
     */
    fun submitConstruct(playerId: Long) {
        val eng = engine ?: return
        val s = _state.value
        if (!s.constructCanSubmit) return
        val result = eng.submitConstruct(s.constructSelected) ?: return
        val currentQ = eng.session.currentQuestion

        viewModelScope.launch {
            try {
                attemptRepository.insert(
                    Attempt(
                        playerId = playerId,
                        questionUuid = currentQ?.uuid.orEmpty(),
                        domainSlug = currentQ?.domainSlug.orEmpty(),
                        selectedText = result.selectedAnswer,
                        isCorrect = result.isCorrect,
                        hintsUsed = result.hintsUsed,
                        language = contentLanguage,
                    )
                )
            } catch (t: Throwable) {
                Log.e("QuizViewModel", "Failed to save construct attempt", t)
            }
        }

        publishEngineState()
        advanceJob?.cancel()
        advanceJob = null
        if (result.isCorrect) {
            advanceJob = viewModelScope.launch {
                delay(FEEDBACK_DELAY_MS.milliseconds)
                eng.advance()
                _state.update { it.copy(showingWhy = false) }
                publishEngineState()
            }
        }
    }

    /**
     * Submits user feedback for the current question.
     */
    fun submitFeedback(reasonCode: String, comment: String) {
        val eng = engine ?: return
        val q = eng.session.currentQuestion ?: return
        val feedback = Feedback(
            questionUuid = q.uuid,
            questionText = q.text,
            selectedAnswer = eng.session.feedback?.selectedAnswer ?: "",
            correctAnswer = q.correctAnswer,
            language = contentLanguage,
            domainSlug = q.domainSlug,
            articleSlug = q.relatedArticleSlug,
            reasonCode = reasonCode,
            comment = comment,
            appVersion = BuildConfig.VERSION_NAME,
            locale = java.util.Locale.getDefault().toString()
        )
        viewModelScope.launch {
            try {
                app.userDatabase.feedbackDao().insert(feedback)
            } catch (t: Throwable) {
                Log.e("QuizViewModel", "Failed to save feedback", t)
            }
        }
    }

    /**
     * Toggles visibility of the answer explanation ("Why?") view.
     */
    fun toggleWhy() {
        advanceJob?.cancel()
        advanceJob = null
        _state.update { it.copy(showingWhy = !it.showingWhy) }
    }

    /**
     * Toggles UI swapping modes.
     */
    fun toggleSwap() {
        advanceJob?.cancel()
        advanceJob = null
        _state.update { it.copy(isSwapped = !it.isSwapped) }
    }

    /**
     * Advances to the next question in the quiz session.
     */
    fun advance() {
        val eng = engine ?: return
        advanceJob?.cancel()
        advanceJob = null
        _state.update { it.copy(showingWhy = false) }
        if (eng.advance()) {
            publishEngineState()
        }
    }

    /**
     * Reveals the next progressive hint for the current question.
     */
    fun revealNextHint() {
        val eng = engine ?: return
        eng.revealHint() ?: return
        publishEngineState()
    }

    /**
     * Resets the quiz session back to its initial state.
     */
    fun reset() {
        loadJob?.cancel()
        loadJob = null
        advanceJob?.cancel()
        advanceJob = null
        engine?.reset()
        loadedKey = null
        _state.update { it.copy(showingWhy = false, isSwapped = false, errorResId = null) }
        publishEngineState()
    }

    /**
     * Synchronizes the internal [QuizEngine] state to the UI state flow ([_state]).
     *
     * Performs an [isSameQuestion] check against the previous question to preserve user token
     * selections in construction questions when navigating or refreshing.
     */
    private fun publishEngineState() {
        val eng = engine ?: return
        val s = eng.session
        val current = s.currentQuestion

        val prevQuestionUuid = _state.value.questions.getOrNull(_state.value.currentIndex)?.uuid
        val currentQuestionUuid = current?.uuid
        val isSameQuestion = prevQuestionUuid != null && prevQuestionUuid == currentQuestionUuid

        val construct = current?.takeIf { it.type == QuestionType.CONSTRUCT }
        val pool = construct?.let {
            val all = it.tokens + it.distractorTokens
            val seed = it.uuid.hashCode().toLong()
            all.shuffled(kotlin.random.Random(seed))
        }.orEmpty()
        val solution = construct?.solution.orEmpty()
        val selected = if (construct != null && isSameQuestion) {
            _state.value.constructSelected
        } else {
            emptyList()
        }

        _state.update {
            it.copy(
                isLoading = false,
                errorResId = null,
                errorMessage = null,
                questions = s.questions,
                currentIndex = s.currentIndex,
                score = s.score,
                finished = s.finished,
                currentHints = s.currentHints,
                revealedHintsCount = s.revealedHintsForCurrent,
                answerFeedback = s.feedback,
                currentOptions = current?.let { q -> displayedOptionsFor(q) }.orEmpty(),
                availableLanguages = playerContentLanguages,
                unavailableLanguages = unavailableLanguages,
                constructPool = pool,
                constructSolution = solution,
                constructSelected = selected,
            )
        }
    }

    private fun configSeed(playerId: Long, config: QuizConfig): Long {
        var h = 1125899906842597L
        h = h * 31 + playerId
        for (id in config.domainIds.sorted()) h = h * 31 + id
        for (d in (config.difficulties ?: emptySet()).sorted()) h = h * 31 + d
        h = h * 31 + (config.questionLimit?.toLong() ?: -1L)
        h = h * 31 + (config.articleSlug?.hashCode()?.toLong() ?: 0L)
        return h
    }

    private companion object {
        const val FEEDBACK_DELAY_MS = 800L
    }
}
