package com.example.quizapp.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizapp.QuizApplication
import com.example.quizapp.data.dao.ArticleSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing full-text search (FTS) operations over knowledge base articles with debouncing.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuizApplication
    private val _state = MutableStateFlow<List<ArticleSearchResult>>(emptyList())
    val state: StateFlow<List<ArticleSearchResult>> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        if (query.isBlank()) {
            _state.value = emptyList()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            try {
                val lang = app.preferences.contentLanguage.value
                val safeQuery = query.trim()
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { "\"$it\"*" }
                val results = app.contentDatabase.articleFtsDao().search(safeQuery, lang)
                _state.value = results
            } catch (e: Exception) {
                _state.value = emptyList()
            }
        }
    }
}
