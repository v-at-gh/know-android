package com.example.quizapp.data.repository

import com.example.quizapp.data.DomainStats
import com.example.quizapp.data.PlayerStats
import com.example.quizapp.data.entity.Attempt
import kotlinx.coroutines.flow.Flow

interface AttemptRepository {
    suspend fun insert(attempt: Attempt): Long
    fun observeByPlayer(playerId: Long): Flow<List<Attempt>>
    suspend fun getStatsForPlayer(playerId: Long): PlayerStats
    suspend fun getStatsByDomain(playerId: Long): List<DomainStats>
}
