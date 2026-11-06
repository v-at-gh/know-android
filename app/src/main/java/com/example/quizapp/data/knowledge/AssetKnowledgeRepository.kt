package com.example.quizapp.data.knowledge

import android.util.Log
import org.json.JSONArray
import java.util.UUID

class AssetKnowledgeRepository(
    private val loader: TextResourceLoader,
) : KnowledgeRepository {

    private val articleCache = mutableMapOf<Pair<String, String>, Article>()
    private val indexCache = mutableMapOf<String, List<ArticleMeta>>()

    override suspend fun getIndex(language: String): List<ArticleMeta> {
        indexCache[language]?.let { return it }

        val raw = loader.load("knowledge/$language/index.json") ?: run {
            Log.w("KnowledgeRepository", "Index not found for language=$language")
            return emptyList()
        }

        val array = JSONArray(raw)
        val metas = List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val slug = obj.getString("slug")
            val uuid: String = obj.optString("uuid").takeIf { it.isNotBlank() }
                ?: UUID.nameUUIDFromBytes("knowledge/$slug".toByteArray()).toString()
            ArticleMeta(
                uuid = uuid,
                slug = slug,
                domainSlug = obj.getString("domainSlug"),
                title = obj.getString("title"),
                description = obj.optString("description", ""),
                path = obj.getString("path"),
                order = obj.optInt("order", 0),
            )
        }.sortedBy { it.order }

        indexCache[language] = metas
        return metas
    }

    override suspend fun getArticle(language: String, meta: ArticleMeta): Article? {
        val key = language to meta.slug
        articleCache[key]?.let { return it }

        val markdown = loader.load("knowledge/$language/${meta.path}") ?: return null
        val article = Article(meta, markdown)

        articleCache[key] = article
        return article
    }

    override suspend fun getLanguagesWithArticle(
        playerLanguages: List<String>,
        slug: String,
    ): Set<String> {
        return playerLanguages.filterTo(mutableSetOf()) { lang ->
            getIndex(lang).any { it.slug == slug }
        }
    }

    @Synchronized
    fun invalidate() {
        articleCache.clear()
        indexCache.clear()
    }
}
