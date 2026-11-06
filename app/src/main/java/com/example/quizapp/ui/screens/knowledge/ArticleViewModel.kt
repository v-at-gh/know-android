package com.example.quizapp.ui.screens.knowledge

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizapp.BuildConfig
import com.example.quizapp.QuizApplication
import com.example.quizapp.R
import com.example.quizapp.data.entity.Feedback
import com.example.quizapp.data.knowledge.Article
import com.example.quizapp.data.knowledge.ArticleMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the UI state for the article view screen.
 */
data class ArticleUiState(
    val isLoading: Boolean = true,
    val article: Article? = null,
    val domainArticles: List<ArticleMeta> = emptyList(),
    val currentIndex: Int = 0,
    val domainName: String = "",
    val cachedArticles: Map<String, Article> = emptyMap(),
    @StringRes val errorResId: Int? = null,
    val availableLanguages: List<String> = emptyList(),
    val unavailableLanguages: Set<String> = emptySet(),
    val previousSlug: String? = null,
    val nextSlug: String? = null,
    val hasArticleQuiz: Boolean = false,
    val quizAvailabilityMap: Map<String, Boolean> = emptyMap(),
)

/**
 * ViewModel managing article content loading, domain article index caching,
 * language support checks, and associated quiz availability for articles.
 */
class ArticleViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuizApplication
    private val repository = app.knowledgeRepository
    private val domainRepository = app.domainRepository
    private val playerRepository = app.playerRepository
    private val questionRepository = app.questionRepository

    private val _state = MutableStateFlow(ArticleUiState())
    val state: StateFlow<ArticleUiState> = _state.asStateFlow()
    private var loadedFor: Triple<String, String, Long>? = null
    private var loadedLanguage: String? = null

    fun load(slug: String, language: String, playerId: Long) {
        val isLanguageChange = loadedLanguage != null && language != loadedLanguage
        val currentSlug = _state.value.domainArticles.getOrNull(_state.value.currentIndex)?.slug

        val targetSlug = if (isLanguageChange && currentSlug != null) currentSlug else slug

        val key = Triple(targetSlug, language, playerId)
        if (loadedFor == key) return
        loadedFor = key
        loadedLanguage = language

        _state.value = ArticleUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val player = playerRepository.getById(playerId)
                val langs = player?.contentLanguages.orEmpty()
                val withArticle = repository.getLanguagesWithArticle(langs, targetSlug)
                val unavailable = langs.toSet() - withArticle

                val metas = repository.getIndex(language)
                val meta = metas.find { it.slug == targetSlug }
                if (meta == null) {
                    _state.value = ArticleUiState(
                        isLoading = false,
                        errorResId = R.string.error_article_not_found,
                        availableLanguages = langs,
                        unavailableLanguages = unavailable,
                    )
                    return@launch
                }

                val domains = domainRepository.getAll(language)
                val domainName = domains.find { it.slug == meta.domainSlug }?.name ?: meta.domainSlug

                val domainArticles = metas.filter { it.domainSlug == meta.domainSlug }.sortedBy { it.order }
                val currentIndex = domainArticles.indexOfFirst { it.slug == targetSlug }.takeIf { it >= 0 } ?: 0
                val previousSlug = domainArticles.getOrNull(currentIndex - 1)?.slug
                val nextSlug = domainArticles.getOrNull(currentIndex + 1)?.slug

                val cachedArticles = mutableMapOf<String, Article>()
                val quizAvailabilityMap = mutableMapOf<String, Boolean>()

                for (m in domainArticles) {
                    repository.getArticle(language, m)?.let { cachedArticles[m.slug] = it }
                    val quizCount = questionRepository.countByArticleSlug(language, m.slug)
                    quizAvailabilityMap[m.slug] = quizCount >= 2
                }

                val hasArticleQuiz = quizAvailabilityMap[targetSlug] ?: false
                val article = cachedArticles[targetSlug]

                _state.value = ArticleUiState(
                    isLoading = false,
                    article = article,
                    domainArticles = domainArticles,
                    currentIndex = currentIndex,
                    domainName = domainName,
                    cachedArticles = cachedArticles,
                    availableLanguages = langs,
                    unavailableLanguages = unavailable,
                    previousSlug = previousSlug,
                    nextSlug = nextSlug,
                    hasArticleQuiz = hasArticleQuiz,
                    quizAvailabilityMap = quizAvailabilityMap,
                )
            } catch (t: Throwable) {
                Log.e("ArticleViewModel", "Failed to load article=$targetSlug lang=$language", t)
                _state.value = ArticleUiState(
                    isLoading = false,
                    errorResId = R.string.error_article_load_failed,
                )
            }
        }
    }

    fun selectArticle(slug: String) {
        val current = _state.value
        val index = current.domainArticles.indexOfFirst { it.slug == slug }
        if (index >= 0) {
            val art = current.cachedArticles[slug]
            val previousSlug = current.domainArticles.getOrNull(index - 1)?.slug
            val nextSlug = current.domainArticles.getOrNull(index + 1)?.slug
            val hasArticleQuiz = current.quizAvailabilityMap[slug] ?: false

            _state.value = current.copy(
                currentIndex = index,
                article = art,
                previousSlug = previousSlug,
                nextSlug = nextSlug,
                hasArticleQuiz = hasArticleQuiz,
            )
        }
    }

    fun submitArticleFeedback(articleSlug: String, reasonCode: String, comment: String) {
        val current = _state.value
        val article = current.article
        val articleTitle = article?.meta?.title ?: current.domainArticles.find { it.slug == articleSlug }?.title.orEmpty()
        val domainSlug = article?.meta?.domainSlug ?: current.domainArticles.find { it.slug == articleSlug }?.domainSlug.orEmpty()
        val feedback = Feedback(
            questionUuid = null,
            questionText = articleTitle,
            selectedAnswer = "",
            correctAnswer = "",
            language = loadedLanguage ?: app.contentLanguage,
            domainSlug = domainSlug,
            articleSlug = articleSlug,
            reasonCode = reasonCode,
            comment = comment,
            appVersion = BuildConfig.VERSION_NAME,
            locale = java.util.Locale.getDefault().toString(),
        )
        viewModelScope.launch {
            try {
                app.userDatabase.feedbackDao().insert(feedback)
            } catch (t: Throwable) {
                Log.e("ArticleViewModel", "Failed to save article feedback", t)
            }
        }
    }
}
