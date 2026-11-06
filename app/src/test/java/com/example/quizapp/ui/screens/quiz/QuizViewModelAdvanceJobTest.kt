package com.example.quizapp.ui.screens.quiz

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuizViewModelAdvanceJobTest {

    @Test
    fun testToggleWhyCancelsAdvanceJob() {
        val src = File("src/main/java/com/example/quizapp/ui/screens/quiz/QuizViewModel.kt").readText()
        val toggleWhyBlock = extractFunction(src, "fun toggleWhy()")
        assertTrue("toggleWhy must cancel advanceJob", "advanceJob?.cancel()" in toggleWhyBlock)
        val toggleSwapBlock = extractFunction(src, "fun toggleSwap()")
        assertTrue("toggleSwap must cancel advanceJob", "advanceJob?.cancel()" in toggleSwapBlock)
    }

    private fun extractFunction(source: String, signature: String): String {
        val idx = source.indexOf(signature)
        if (idx == -1) return ""
        return source.substring(idx, minOf(idx + 300, source.length))
    }
}
