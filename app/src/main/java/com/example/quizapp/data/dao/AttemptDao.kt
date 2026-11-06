package com.example.quizapp.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.example.quizapp.data.DomainStats
import com.example.quizapp.data.PlayerStats
import com.example.quizapp.data.entity.Attempt
import kotlinx.coroutines.flow.Flow

@Dao
interface AttemptDao {

    @Insert
    suspend fun insert(attempt: Attempt): Long

    @Query("SELECT * FROM attempts WHERE syncedAt IS NULL")
    suspend fun getUnsyncedAttempts(): List<Attempt>

    @Query("UPDATE attempts SET syncedAt = :ts WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM attempts WHERE playerId = :playerId ORDER BY answeredAt DESC")
    fun observeByPlayer(playerId: Long): Flow<List<Attempt>>

    @Query("SELECT COUNT(*) FROM attempts WHERE playerId = :playerId AND isCorrect = 1")
    fun observeCorrectCount(playerId: Long): Flow<Int>

    @Query("""
        SELECT
            COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END), 0) AS correct,
            COALESCE(SUM(hintsUsed), 0) AS hintsUsed
        FROM attempts
        WHERE playerId = :playerId
    """)
    suspend fun getStatsForPlayer(playerId: Long): PlayerStats

    @Query("""
        SELECT
            domainSlug AS domainSlug,
            domainSlug AS domainName,
            COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END), 0) AS correct
        FROM attempts
        WHERE playerId = :playerId
        GROUP BY domainSlug
        ORDER BY domainSlug
    """)
    suspend fun getStatsByDomain(playerId: Long): List<DomainStats>
}
