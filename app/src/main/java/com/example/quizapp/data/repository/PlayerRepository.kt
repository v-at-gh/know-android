package com.example.quizapp.data.repository

import com.example.quizapp.data.entity.Player
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    fun observeAll(): Flow<List<Player>>
    fun observeById(id: Long): Flow<Player?>
    suspend fun getById(id: Long): Player?
    suspend fun create(player: Player): Long
    suspend fun updatePreferredDomains(playerId: Long, slugs: List<String>)
    suspend fun updatePrimaryLanguage(playerId: Long, lang: String)
    suspend fun updateContentLanguages(playerId: Long, langs: List<String>)
    suspend fun updateSelectedDomains(playerId: Long, slugs: List<String>?)
}
