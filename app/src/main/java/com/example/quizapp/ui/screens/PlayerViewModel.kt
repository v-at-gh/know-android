package com.example.quizapp.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.quizapp.QuizApplication
import com.example.quizapp.data.entity.Player
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel responsible for observing the list of all registered players.
 *
 * @property playerRepository Repository providing reactive access to player data.
 * @property players Reactive flow emitting the list of all players.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val playerRepository = (application as QuizApplication).playerRepository
    val players: Flow<List<Player>> = playerRepository.observeAll()
}