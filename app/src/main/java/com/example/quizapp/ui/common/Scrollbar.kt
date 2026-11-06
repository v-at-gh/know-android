package com.example.quizapp.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modifier drawing a vertical scrollbar thumb on a scrollable container.
 */
fun Modifier.drawVerticalScrollbar(
    scrollState: ScrollState,
    color: Color = Color.Gray,
    alpha: Float = 0.6f,
    width: Dp = 4.dp,
    pad: Dp = 4.dp,
    alignment: Alignment.Horizontal = Alignment.End,
): Modifier = this.drawWithContent {
    drawContent()
    if (scrollState.maxValue > 0) {
        val viewportHeight = size.height
        val totalHeight = scrollState.maxValue.toFloat() + viewportHeight
        val thumbHeight = ((viewportHeight / totalHeight) * viewportHeight).coerceAtLeast(20.dp.toPx())
        val maxScroll = scrollState.maxValue.toFloat()
        val scrollOffset = scrollState.value.toFloat()
        val thumbOffset = if (maxScroll > 0f) (scrollOffset / maxScroll) * (viewportHeight - thumbHeight) else 0f

        val widthPx = width.toPx()
        val padPx = pad.toPx()
        val xOffset = when (alignment) {
            Alignment.Start -> padPx
            else -> size.width - widthPx - padPx
        }

        drawRoundRect(
            color = color,
            topLeft = Offset(xOffset, thumbOffset),
            size = Size(widthPx, thumbHeight),
            cornerRadius = CornerRadius(widthPx / 2, widthPx / 2),
            alpha = alpha,
        )
    }
}
