package com.example.quizapp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizQuestionTest {

    private fun select(
        answer: String = "Paris",
        distractors: List<com.example.quizapp.data.entity.Distractor> =
            listOf(com.example.quizapp.data.entity.Distractor("London")),
    ) = QuizQuestion(
        id = 1L, domainId = 1L, difficulty = 1, text = "Capital?",
        correctAnswer = answer, distractorPool = distractors, hints = emptyList(),
    )

    private fun construct(
        tokens: List<ConstructToken> = listOf(ConstructToken(0, "a"), ConstructToken(1, "b")),
        solution: List<Int> = listOf(0, 1),
    ) = QuizQuestion(
        id = 2L, domainId = 1L, difficulty = 1, text = "Assemble",
        correctAnswer = "a b", distractorPool = emptyList(), hints = emptyList(),
        type = QuestionType.CONSTRUCT, tokens = tokens, solution = solution,
    )

    @Test fun `select with answer and distractors is playable`() {
        assertTrue(select().isPlayable)
    }

    @Test fun `select with empty answer is not playable`() {
        assertFalse(select(answer = "").isPlayable)
    }

    @Test fun `select with no distractors is not playable`() {
        assertFalse(select(distractors = emptyList()).isPlayable)
    }

    @Test fun `construct with valid tokens and solution is playable`() {
        assertTrue(construct().isPlayable)
    }

    @Test fun `construct with empty tokens is not playable`() {
        assertFalse(construct(tokens = emptyList()).isPlayable)
    }

    @Test fun `construct with empty solution is not playable`() {
        assertFalse(construct(solution = emptyList()).isPlayable)
    }

    @Test fun `construct with out-of-range solution index is not playable`() {
        assertFalse(construct(solution = listOf(0, 5)).isPlayable)
    }

    @Test fun `construct with duplicate solution index is not playable`() {
        assertFalse(construct(solution = listOf(0, 0)).isPlayable)
    }

    @Test fun `construct with solution shorter than tokens is not playable`() {
        assertFalse(
            construct(
                tokens = listOf(ConstructToken(0, "a"), ConstructToken(1, "b"), ConstructToken(2, "c")),
                solution = listOf(0, 1),
            ).isPlayable
        )
    }

    @Test fun `parseTokens handles malformed JSON gracefully`() {
        assertTrue(parseTokens("not a json").isEmpty())
        assertTrue(parseTokens("{bad_json}").isEmpty())
        assertTrue(parseTokens(null).isEmpty())
    }

    @Test fun `parseTokens handles missing keys`() {
        val partialJson = """{"tokens": [{"text": "A"}, {"text": "B"}]}"""
        val tokens = parseTokens(partialJson)
        assertEquals(2, tokens.size)
        assertTrue(parseSolution(partialJson).isEmpty())
    }
}
