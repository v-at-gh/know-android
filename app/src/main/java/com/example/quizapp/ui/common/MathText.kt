package com.example.quizapp.ui.common

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.measure.rememberLatexMeasurer

private const val MATH_INLINE_ID_PREFIX = "math_inline_"

private val LocalLatexMeasurer = compositionLocalOf<LatexMeasurerState?> { null }

private val MATH_PATTERN = Regex(
    pattern = """\$\$(.+?)\$\$|\\\[(.+?)\\\]|\$([^\$\n]+?)\$|\\\((.+?)\\\)""",
    option = RegexOption.DOT_MATCHES_ALL,
)

private data class ParsedMath(
    val annotated: AnnotatedString,
    val latexById: Map<String, String>,
)

/**
 * Provider composable supplying a [LatexMeasurerState] via CompositionLocal.
 */
@Composable
fun ProvideLatexMeasurer(content: @Composable () -> Unit) {
    val measurer = rememberLatexMeasurer()
    CompositionLocalProvider(LocalLatexMeasurer provides measurer) {
        content()
    }
}

/**
 * Composable text component capable of parsing and rendering inline LaTeX math formulas.
 */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val measurer = LocalLatexMeasurer.current

    if (measurer == null || !hasMath(text)) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            textAlign = textAlign,
        )
        return
    }

    val parsed = remember(text) { parseMath(text) }
    val inlineContent = remember(parsed, measurer, style.fontSize) {
        buildMap<String, InlineTextContent> {
            for ((id, latex) in parsed.latexById) {
                LatexInlineCache.get(measurer, latex, style.fontSize)?.let { put(id, it) }
            }
        }
    }
    Text(
        text = parsed.annotated,
        inlineContent = inlineContent,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
    )
}

private fun hasMath(text: String): Boolean =
    text.indexOf('$') >= 0 || text.contains("\\(") || text.contains("\\[")

private fun parseMath(text: String): ParsedMath {
    val latexById = mutableMapOf<String, String>()
    val annotated = buildAnnotatedString {
        var lastIndex = 0
        for (match in MATH_PATTERN.findAll(text)) {
            if (match.range.first > lastIndex) {
                append(text.substring(lastIndex, match.range.first))
            }
            val latex = match.groups
                .drop(1)
                .firstNotNullOfOrNull { it?.value }
                ?.trim()
                .orEmpty()

            if (latex.isNotEmpty()) {
                val id = "$MATH_INLINE_ID_PREFIX${latex.hashCode()}"
                latexById[id] = latex
                appendInlineContent(id, latex)
            } else {
                append(match.value)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    return ParsedMath(annotated, latexById)
}
