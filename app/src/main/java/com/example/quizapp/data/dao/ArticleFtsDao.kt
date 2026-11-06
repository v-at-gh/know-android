package com.example.quizapp.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.example.quizapp.data.entity.ArticleFts

@Dao
interface ArticleFtsDao {
    @Insert
    suspend fun insertAll(articles: List<ArticleFts>)

    @Query("DELETE FROM article_fts")
    suspend fun clear()

    @Query("DELETE FROM article_fts WHERE domainSlug IN (:slugs)")
    suspend fun deleteByDomainSlugs(slugs: List<String>)

    @Query("""
        SELECT domainSlug, slug, title, 
               snippet(article_fts, 4, '<b>', '</b>', '...', 10) as snippet
        FROM article_fts 
        WHERE article_fts MATCH :query AND lang = :lang
        LIMIT 50
    """)
    suspend fun search(query: String, lang: String): List<ArticleSearchResult>
}

data class ArticleSearchResult(
    val domainSlug: String,
    val slug: String,
    val title: String,
    val snippet: String
)
