package com.example.quizapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Wraps LocalHapticFeedback with a preference gate.
 *
 * The preference is read once at the composable call site; callers pass the
 * boolean in. This keeps the helper free of DI and makes the gate testable.
 */
@Composable
fun rememberHapticTap(enabled: Boolean): (HapticFeedbackType) -> Unit {
    val haptics = LocalHapticFeedback.current
    return { type -> if (enabled) haptics.performHapticFeedback(type) }
}
