package com.example.quizapp.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.example.quizapp.data.entity.Hint
import com.example.quizapp.data.entity.HintTranslation

@Dao
interface HintDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(hint: Hint): Long

    @Query("SELECT COUNT(*) FROM hints WHERE questionId = :questionId")
    suspend fun countByQuestion(questionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTranslation(translation: HintTranslation): Long

    @Query("SELECT * FROM hints WHERE questionId = :questionId ORDER BY `order`")
    suspend fun getByQuestionOrdered(questionId: Long): List<Hint>

    @Query("SELECT * FROM hints WHERE questionId = :questionId AND `order` = :order LIMIT 1")
    suspend fun getByQuestionAndOrder(questionId: Long, order: Int): Hint?
}