package com.example.quizapp.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.example.quizapp.data.entity.Feedback
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    @Insert
    suspend fun insert(feedback: Feedback): Long

    @Query("SELECT * FROM feedback WHERE sentAt IS NULL ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getUnsent(limit: Int = 50): List<Feedback>

    @Query("UPDATE feedback SET sentAt = :ts WHERE id IN (:ids)")
    suspend fun markSent(ids: List<Long>, ts: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM feedback WHERE sentAt IS NULL")
    fun observeUnsentCount(): Flow<Int>
}
