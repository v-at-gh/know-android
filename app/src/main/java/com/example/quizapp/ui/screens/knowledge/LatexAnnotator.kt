package com.example.quizapp.ui.screens.knowledge

import androidx.compose.foundation.text.appendInlineContent
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import org.intellij.markdown.flavours.gfm.GFMElementTypes.INLINE_MATH

const val LATEX_INLINE_ID_PREFIX = "latex_inline_"

/**
 * Creates a [MarkdownAnnotator] that intercepts inline math elements (`INLINE_MATH`)
 * and replaces them with inline text content IDs for LaTeX formula rendering.
 *
 * @return A configured [MarkdownAnnotator].
 */
fun latexAnnotator(): MarkdownAnnotator = markdownAnnotator { content, child ->
    if (child.type == INLINE_MATH) {
        val raw = content.substring(child.startOffset, child.endOffset)
        val latex = raw.removeSurrounding("$").trim()
        val id = "$LATEX_INLINE_ID_PREFIX${latex.hashCode()}"
        appendInlineContent(id, latex)
        return@markdownAnnotator true
    }
    false
}
