package com.example.quizapp.engine

import com.example.quizapp.data.entity.Distractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionOrderingTest {

    private fun q(id: Long, difficulty: Int) = QuizQuestion(
        id = id,
        domainId = 1L,
        difficulty = difficulty,
        text = "Q$id",
        correctAnswer = "a",
        distractorPool = listOf(
            Distractor("b"),
            Distractor("c"),
            Distractor("d"),
        ),
        hints = emptyList(),
    )

    @Test
    fun `empty list returns empty list`() {
        assertTrue(QuestionOrdering.shuffledByDifficulty(emptyList()).isEmpty())
    }

    @Test
    fun `groups follow difficulty order`() {
        val input = listOf(
            q(1, 3), q(2, 1), q(3, 2), q(4, 1), q(5, 3), q(6, 2),
        )
        val result = QuestionOrdering.shuffledByDifficulty(input, seed = 42L)
        val difficulties = result.map { it.difficulty }
        assertEquals(listOf(1, 1, 2, 2, 3, 3), difficulties)
    }

    @Test
    fun `all questions present, no loss`() {
        val input = (1L..10L).map { q(it, (it % 3).toInt() + 1) }
        val result = QuestionOrdering.shuffledByDifficulty(input, seed = 42L)
        assertEquals(input.size, result.size)
        assertEquals(input.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun `same seed gives same order`() {
        val input = (1L..20L).map { q(it, 1) }
        val a = QuestionOrdering.shuffledByDifficulty(input, seed = 7L)
        val b = QuestionOrdering.shuffledByDifficulty(input, seed = 7L)
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun `different seeds usually give different order`() {
        // Not an ideal test (collisions are possible), but with 20 elements,
        // the probability of two random permutations matching is negligible.
        val input = (1L..20L).map { q(it, 1) }
        val a = QuestionOrdering.shuffledByDifficulty(input, seed = 1L)
        val b = QuestionOrdering.shuffledByDifficulty(input, seed = 2L)
        assertTrue(a.map { it.id } != b.map { it.id })
    }

    @Test
    fun `shuffledByDifficulty with seed parameter is deterministic`() {
        val input = (1L..20L).map { q(it, 1) }
        val a = QuestionOrdering.shuffledByDifficulty(input, seed = 12345L)
        val b = QuestionOrdering.shuffledByDifficulty(input, seed = 12345L)
        assertEquals(a.map { it.id }, b.map { it.id })
    }
}
