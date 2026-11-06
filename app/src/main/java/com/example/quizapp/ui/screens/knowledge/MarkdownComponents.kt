package com.example.quizapp.ui.screens.knowledge

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrm.latex.renderer.Latex
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.findChildOfType

/**
 * Custom [MarkdownComponents] configuring code fence blocks to render `math` or `latex` language blocks
 * as formatted block LaTeX formulas using the [Latex] renderer.
 */
val latexMarkdownComponents: MarkdownComponents = markdownComponents(
    codeFence = { state ->

        val fenceLangNode = state.node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)
        val language = fenceLangNode
            ?.let { state.content.substring(it.startOffset, it.endOffset) }
            ?.trim()
            ?.lowercase()

        android.util.Log.d("LatexFence", "language=$language, content=${state.content.take(40)}")
        val codeContent = state.node
            .findChildOfType(MarkdownTokenTypes.CODE_FENCE_CONTENT)
            ?.let { state.content.substring(it.startOffset, it.endOffset) }
            ?: ""

        if (language == "math" || language == "latex") {
            Latex(
                latex = codeContent.trim(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        } else {
            MarkdownCodeFence(
                content = state.content,
                node = state.node,
            )
        }
    },
)