package com.example.quizapp.data.repository

import com.example.quizapp.data.DomainStats
import com.example.quizapp.data.PlayerStats
import com.example.quizapp.data.dao.AttemptDao
import com.example.quizapp.data.entity.Attempt
import kotlinx.coroutines.flow.Flow

class RoomAttemptRepository(private val dao: AttemptDao) : AttemptRepository {

    override suspend fun insert(attempt: Attempt): Long = dao.insert(attempt)

    override fun observeByPlayer(playerId: Long): Flow<List<Attempt>> =
        dao.observeByPlayer(playerId)

    override suspend fun getStatsForPlayer(playerId: Long): PlayerStats =
        dao.getStatsForPlayer(playerId)

    override suspend fun getStatsByDomain(playerId: Long): List<DomainStats> =
        dao.getStatsByDomain(playerId)
}
