package com.example.quizapp.data.repository

import com.example.quizapp.data.dao.PlayerDao
import com.example.quizapp.data.entity.Player
import kotlinx.coroutines.flow.Flow

class RoomPlayerRepository(private val dao: PlayerDao) : PlayerRepository {
    override fun observeAll(): Flow<List<Player>> = dao.observeAll()
    override fun observeById(id: Long): Flow<Player?> = dao.observeById(id)
    override suspend fun getById(id: Long): Player? = dao.getById(id)
    override suspend fun create(player: Player): Long = dao.insert(player)
    override suspend fun updatePreferredDomains(playerId: Long, slugs: List<String>) =
        dao.updatePreferredDomains(playerId, slugs)
    override suspend fun updatePrimaryLanguage(playerId: Long, lang: String) =
        dao.updatePrimaryLanguage(playerId, lang)
    override suspend fun updateContentLanguages(playerId: Long, langs: List<String>) =
        dao.updateContentLanguages(playerId, langs)
    override suspend fun updateSelectedDomains(playerId: Long, slugs: List<String>?) =
        dao.updateSelectedDomains(playerId, slugs)
}
