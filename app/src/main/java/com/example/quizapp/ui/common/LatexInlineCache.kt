package com.example.quizapp.ui.common

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.TextUnit
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.model.LatexConfig

/**
 * Synchronized LRU cache for inline LaTeX math measure states and inline text content composables.
 */
object LatexInlineCache {

    private const val MAX_ENTRIES = 256

    private val cache = object : LinkedHashMap<Pair<String, TextUnit>, InlineTextContent>(
        /* initialCapacity = */ 64,
        /* loadFactor      = */ 0.75f,
        /* accessOrder     = */ true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Pair<String, TextUnit>, InlineTextContent>,
        ): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(
        measurer: LatexMeasurerState,
        latex: String,
        size: TextUnit,
    ): InlineTextContent? {
        val key = latex to size
        cache[key]?.let { return it }
        val value = measurer.inlineContent(latex, LatexConfig(size)) ?: return null
        cache[key] = value
        return value
    }
}