package com.example.quizapp.engine

import com.example.quizapp.data.entity.Distractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEngineTest {

    private fun sampleQuestions() = listOf(
        QuizQuestion(
            id = 1L,
            domainId = 1L,
            difficulty = 1,
            text = "Q1?",
            correctAnswer = "right-1",
            distractorPool = listOf(
                Distractor("wrong-a", why = "because a"),
                Distractor("wrong-b", why = "because b"),
                Distractor("wrong-c"),
            ),
            hints = listOf("hint 1.1", "hint 1.2"),
            answerWhy = "because right-1",
        ),
        QuizQuestion(
            id = 2L,
            domainId = 1L,
            difficulty = 1,
            text = "Q2?",
            correctAnswer = "right-2",
            distractorPool = listOf(
                Distractor("wrong-a"),
                Distractor("wrong-b"),
                Distractor("wrong-c"),
            ),
            hints = emptyList(),
        ),
        QuizQuestion(
            id = 3L,
            domainId = 1L,
            difficulty = 1,
            text = "Q3?",
            correctAnswer = "right-3",
            distractorPool = listOf(
                Distractor("wrong-a"),
                Distractor("wrong-b"),
                Distractor("wrong-c"),
            ),
            hints = emptyList(),
        ),
    )

    @Test
    fun `correct answer increments score`() {
        val engine = QuizEngine(sampleQuestions())
        engine.selectAnswer("right-1")
        assertEquals(1, engine.session.score)
    }

    @Test
    fun `wrong answer does not increment score`() {
        val engine = QuizEngine(sampleQuestions())
        engine.selectAnswer("wrong-a")
        assertEquals(0, engine.session.score)
    }

    @Test
    fun `selectAnswer returns answerWhy explanation when correct`() {
        val engine = QuizEngine(sampleQuestions())
        engine.selectAnswer("right-1")
        assertEquals("because right-1", engine.session.feedback?.explanation)
    }

    @Test
    fun `selectAnswer returns distractor why explanation when incorrect`() {
        val engine = QuizEngine(sampleQuestions())
        engine.selectAnswer("wrong-a")
        assertEquals("because a", engine.session.feedback?.explanation)
    }

    @Test
    fun `answer outside pool and not correct is rejected`() {
        val engine = QuizEngine(sampleQuestions())
        val result = engine.selectAnswer("garbage")
        assertNull(result)
        assertEquals(0, engine.session.score)
    }

    @Test
    fun `cannot answer twice before advance`() {
        val engine = QuizEngine(sampleQuestions())
        engine.selectAnswer("right-1")
        val second = engine.selectAnswer("wrong-a")
        assertNull(second)
        assertEquals(1, engine.session.score)
    }

    @Test
    fun `advance moves to next question`() {
        val engine = QuizEngine(sampleQuestions())
        engine.selectAnswer("right-1")
        engine.advance()
        assertEquals(1, engine.session.currentIndex)
    }

    @Test
    fun `advance without feedback is a no-op`() {
        val engine = QuizEngine(sampleQuestions())
        val advanced = engine.advance()
        assertFalse(advanced)
        assertEquals(0, engine.session.currentIndex)
    }

    @Test
    fun `session finishes after last question`() {
        val engine = QuizEngine(sampleQuestions())
        engine.selectAnswer("right-1"); engine.advance()
        engine.selectAnswer("right-2"); engine.advance()
        engine.selectAnswer("right-3"); engine.advance()
        assertTrue(engine.session.finished)
        assertEquals(3, engine.session.score)
    }

    @Test
    fun `revealing hint returns hint text and increments counter`() {
        val engine = QuizEngine(sampleQuestions())
        val hint = engine.revealHint()
        assertNotNull(hint)
        assertEquals("hint 1.1", hint)
        assertEquals(1, engine.session.revealedHintsForCurrent)
    }

    @Test
    fun `cannot reveal more hints than available`() {
        val engine = QuizEngine(sampleQuestions())
        engine.revealHint()
        engine.revealHint()
        val third = engine.revealHint()
        assertNull(third)
        assertEquals(2, engine.session.revealedHintsForCurrent)
    }

    @Test
    fun `question without hints cannot reveal any`() {
        val engine = QuizEngine(sampleQuestions())
        // we moved on to the second question, which has `hints = emptyList()`
        engine.selectAnswer("right-1")
        engine.advance()

        val hint = engine.revealHint()
        assertNull(hint)
        assertEquals(0, engine.session.revealedHintsForCurrent)
    }

    @Test
    fun `hint counter is per-question`() {
        val engine = QuizEngine(sampleQuestions())
        engine.revealHint()
        engine.selectAnswer("right-1")
        engine.advance()
        // no hints were revealed for the second question
        assertEquals(0, engine.session.revealedHintsForCurrent)
    }

    @Test
    fun `answer result carries hints used`() {
        val engine = QuizEngine(sampleQuestions())
        engine.revealHint()
        val result = engine.selectAnswer("right-1")
        assertEquals(1, result?.hintsUsed)
    }

    @Test
    fun `clearFeedback removes feedback from session`() {
        val engine = QuizEngine(sampleQuestions())
        engine.selectAnswer("wrong-a")
        assertNotNull(engine.session.feedback)
        engine.clearFeedback()
        assertNull(engine.session.feedback)
    }

    @Test
    fun `reset returns to initial state`() {
        val engine = QuizEngine(sampleQuestions())
        engine.selectAnswer("right-1")
        engine.advance()
        engine.reset()
        assertEquals(0, engine.session.currentIndex)
        assertEquals(0, engine.session.score)
        assertFalse(engine.session.finished)
    }

    @Test
    fun `replaceQuestions updates questions and reset retains replaced questions`() {
        val engine = QuizEngine(sampleQuestions())
        val frenchQuestions = sampleQuestions().map { it.copy(text = it.text + " (FR)") }
        val replaced = engine.replaceQuestions(frenchQuestions)
        assertTrue(replaced)
        assertEquals("Q1? (FR)", engine.session.questions[0].text)

        engine.selectAnswer("right-1")
        engine.advance()
        engine.reset()

        assertEquals(0, engine.session.currentIndex)
        assertEquals("Q1? (FR)", engine.session.questions[0].text)
    }

    @Test
    fun `replaceQuestions with reduced list preserves score and revealed hints`() {
        val original = sampleQuestions()
        val engine = QuizEngine(original)
        engine.revealHint()
        engine.selectAnswer("right-1")
        engine.advance()
        assertEquals(1, engine.session.score)

        val shrunkList = listOf(original[1], original[2])
        val replaced = engine.replaceQuestions(shrunkList)
        assertTrue(replaced)
        assertEquals(1, engine.session.score)
        assertEquals(shrunkList, engine.session.questions)
    }

    @Test
    fun `seekTo moves currentIndex to question matching UUID`() {
        val original = sampleQuestions()
        val engine = QuizEngine(original)
        val targetUuid = original[2].uuid
        val seekResult = engine.seekTo(targetUuid)
        assertTrue(seekResult)
        assertEquals(2, engine.session.currentIndex)
        assertEquals(targetUuid, engine.session.currentQuestion?.uuid)
    }

    @Test
    fun `submitConstruct evaluates correct token sequence`() {
        val constructQ = QuizQuestion(
            id = 10L,
            domainId = 1L,
            difficulty = 1,
            text = "Assemble find command",
            correctAnswer = "find . -name",
            distractorPool = emptyList(),
            hints = emptyList(),
            answerWhy = "find command syntax",
            type = QuestionType.CONSTRUCT,
            tokens = listOf(
                ConstructToken(0, "find"),
                ConstructToken(1, "."),
                ConstructToken(2, "-name")
            ),
            solution = listOf(0, 1, 2),
            distractorTokens = listOf(
                ConstructToken(3, "-exec")
            )
        )
        val engine = QuizEngine(listOf(constructQ))
        val result = engine.submitConstruct(listOf(0, 1, 2))
        assertNotNull(result)
        assertTrue(result!!.isCorrect)
        assertEquals("find . -name", result.selectedAnswer)
        assertEquals("find command syntax", engine.session.feedback?.explanation)
        assertEquals(1, engine.session.score)
    }

    @Test
    fun `submitConstruct evaluates incorrect token sequence`() {
        val constructQ = QuizQuestion(
            id = 10L,
            domainId = 1L,
            difficulty = 1,
            text = "Assemble find command",
            correctAnswer = "find . -name",
            distractorPool = emptyList(),
            hints = emptyList(),
            type = QuestionType.CONSTRUCT,
            tokens = listOf(
                ConstructToken(0, "find"),
                ConstructToken(1, "."),
                ConstructToken(2, "-name")
            ),
            solution = listOf(0, 1, 2),
            distractorTokens = listOf(
                ConstructToken(3, "-exec")
            )
        )
        val engine = QuizEngine(listOf(constructQ))
        val result = engine.submitConstruct(listOf(0, 3, 2))
        assertNotNull(result)
        assertFalse(result!!.isCorrect)
        assertEquals("find -exec -name", result.selectedAnswer)
        assertEquals(0, engine.session.score)
    }
}
