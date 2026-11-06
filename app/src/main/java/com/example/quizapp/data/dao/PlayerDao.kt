package com.example.quizapp.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.example.quizapp.data.entity.Player
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(player: Player): Long

    @Query("SELECT * FROM players ORDER BY createdAt")
    fun observeAll(): Flow<List<Player>>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getById(id: Long): Player?

    @Query("UPDATE players SET preferredDomainSlugs = :slugs WHERE id = :playerId")
    suspend fun updatePreferredDomains(playerId: Long, slugs: List<String>)

    @Query("SELECT * FROM players WHERE id = :playerId")
    fun observeById(playerId: Long): Flow<Player?>

    @Query("UPDATE players SET primaryContentLanguage = :lang WHERE id = :playerId")
    suspend fun updatePrimaryLanguage(playerId: Long, lang: String)

    @Query("UPDATE players SET contentLanguages = :langs WHERE id = :playerId")
    suspend fun updateContentLanguages(playerId: Long, langs: List<String>)

    @Query("UPDATE players SET selectedDomainSlugs = :slugs WHERE id = :playerId")
    suspend fun updateSelectedDomains(playerId: Long, slugs: List<String>?)
}
