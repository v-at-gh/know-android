package com.example.quizapp.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.example.quizapp.data.entity.Domain
import com.example.quizapp.data.entity.DomainTranslation

@Dao
interface DomainDao {

    // --- Domain ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(domain: Domain): Long

    @Query("SELECT * FROM domains WHERE slug = :slug")
    suspend fun getBySlug(slug: String): Domain?

    @Query("SELECT * FROM domains ORDER BY slug")
    suspend fun getAll(): List<Domain>

    @Query("DELETE FROM domains WHERE slug IN (:slugs)")
    suspend fun deleteDomainsBySlugs(slugs: List<String>)

    // --- DomainTranslation ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTranslation(translation: DomainTranslation): Long

    @androidx.room3.Update
    suspend fun updateTranslation(translation: DomainTranslation)

    @Query("""
        SELECT * FROM domain_translations 
        WHERE domainId = :domainId AND language = :language
    """)
    suspend fun getTranslation(domainId: Long, language: String): DomainTranslation?

    @Query("""
        SELECT
            d.id           AS id,
            d.slug         AS slug,
            COALESCE(dt.name, d.slug)        AS name,
            COALESCE(dt.description, '')     AS description,
            CASE WHEN dt.id IS NOT NULL THEN 1 ELSE 0 END AS hasTranslation
        FROM domains d
        LEFT JOIN domain_translations dt
            ON dt.domainId = d.id AND dt.language = :language
        ORDER BY COALESCE(dt.name, d.slug)
    """)
    suspend fun getAllWithTranslation(language: String): List<DomainWithTranslation>

    @Query("""
        SELECT DISTINCT language FROM domain_translations
        WHERE domainId = :domainId
        ORDER BY language
    """)
    suspend fun getLanguagesForDomain(domainId: Long): List<String>
}

data class DomainWithTranslation(
    val id: Long,
    val slug: String,
    val name: String,
    val description: String,
    val hasTranslation: Boolean = true,
    val availableLanguages: List<String> = emptyList(),
)