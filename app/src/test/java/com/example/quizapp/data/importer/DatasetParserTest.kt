package com.example.quizapp.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetParserTest {

    private fun validQuestion(uuid: String = "u1", text: String = "Q?") = """
        {
          "uuid": "$uuid",
          "text": "$text",
          "answer": "a",
          "distractors": [
            {"text": "b", "why": "b is wrong"},
            {"text": "c", "why": "c is wrong"},
            {"text": "d", "why": "d is wrong"}
          ],
          "difficulty": 1,
          "hints": ["h1", "h2", "h3"]
        }
    """.trimIndent()

    @Test
    fun `parses a well-formed question`() {
        val (valid, errors) = DatasetParser.parseQuestions("[${validQuestion()}]")
        assertEquals(1, valid.size)
        assertTrue(errors.isEmpty())
        assertEquals("u1", valid[0].uuid)
        assertEquals(3, valid[0].distractors.size)
        assertEquals(3, valid[0].hints.size)
    }

    @Test
    fun `rejects question with duplicate distractors and keeps the rest`() {
        val dup = """
            {
              "uuid": "bad",
              "text": "Bad?",
              "answer": "a",
              "distractors": [
                {"text": "b"},
                {"text": "b"},
                {"text": "c"}
              ],
              "difficulty": 1,
              "hints": ["h"]
            }
        """.trimIndent()
        val (valid, errors) = DatasetParser.parseQuestions("[${validQuestion()},$dup,${validQuestion("u3")}]")
        assertEquals(2, valid.size)
        assertEquals(listOf("u1", "u3"), valid.map { it.uuid })
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("duplicate distractors"))
    }

    @Test
    fun `rejects question when answer is among distractors`() {
        val bad = """
            {
              "uuid": "bad",
              "text": "?",
              "answer": "a",
              "distractors": [{"text": "a"}, {"text": "b"}, {"text": "c"}],
              "difficulty": 1,
              "hints": []
            }
        """.trimIndent()
        val (valid, errors) = DatasetParser.parseQuestions("[$bad]")
        assertTrue(valid.isEmpty())
        assertEquals(1, errors.size)
    }

    @Test
    fun `rejects question with fewer than two distractors`() {
        val bad = """
            {
              "uuid": "bad",
              "text": "?",
              "answer": "a",
              "distractors": [{"text": "b"}],
              "difficulty": 1,
              "hints": []
            }
        """.trimIndent()
        val (valid, errors) = DatasetParser.parseQuestions("[$bad]")
        assertTrue(valid.isEmpty())
        assertTrue(errors[0].contains("need >= 2"))
    }

    @Test
    fun `accepts question without hints`() {
        val noHints = """
            {
              "uuid": "nohints",
              "text": "?",
              "answer": "a",
              "distractors": [{"text": "b"}, {"text": "c"}, {"text": "d"}],
              "difficulty": 1,
              "hints": []
            }
        """.trimIndent()
        val (valid, errors) = DatasetParser.parseQuestions("[$noHints]")
        assertEquals(1, valid.size)
        assertTrue(errors.isEmpty())
        assertTrue(valid[0].hints.isEmpty())
    }

    @Test
    fun `generates uuid when missing`() {
        val noUuid = """
            {
              "text": "?",
              "answer": "a",
              "distractors": [{"text": "b"}, {"text": "c"}, {"text": "d"}],
              "difficulty": 1,
              "hints": []
            }
        """.trimIndent()
        val (valid, _) = DatasetParser.parseQuestions("[$noUuid]")
        assertEquals(1, valid.size)
        assertNotNull(valid[0].uuid)
        assertTrue(valid[0].uuid.isNotBlank())
    }

    @Test
    fun `malformed entry does not break siblings`() {
        val malformed = """{"text": "only text"}"""
        val (valid, errors) = DatasetParser.parseQuestions("[${validQuestion()},$malformed,${validQuestion("u3")}]")
        assertEquals(2, valid.size)
        assertEquals(1, errors.size)
    }

    @Test
    fun `parses construct question correctly`() {
        val constructJson = """
            {
              "uuid": "c1",
              "type": "construct",
              "text": "Assemble find command",
              "difficulty": 1,
              "hints": ["h1"],
              "tokens": [
                {"text": "find"},
                {"text": "."},
                {"text": "-name"}
              ],
              "solution": [0, 1, 2],
              "distractorTokens": [
                {"text": "-exec"}
              ]
            }
        """.trimIndent()
        val (valid, errors) = DatasetParser.parseQuestions("[$constructJson]")
        assertEquals(1, valid.size)
        assertTrue(errors.isEmpty())
        val entry = valid[0]
        assertEquals("construct", entry.type)
        assertEquals("find . -name", entry.answer)
        assertTrue(entry.distractors.isEmpty())
        assertNotNull(entry.payloadJson)
    }

    @Test
    fun `rejects invalid construct question`() {
        val badConstructJson = """
            {
              "uuid": "c_bad",
              "type": "construct",
              "text": "Assemble command",
              "difficulty": 1,
              "hints": [],
              "tokens": [{"text": "only_one"}],
              "solution": [0]
            }
        """.trimIndent()
        val (valid, errors) = DatasetParser.parseQuestions("[$badConstructJson]")
        assertTrue(valid.isEmpty())
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("needs >= 2 tokens"))
    }

    @Test
    fun `rejects construct question if solution does not use every token`() {
        val unusedTokensJson = """
            {
              "uuid": "c_unused",
              "type": "construct",
              "text": "Assemble command",
              "difficulty": 1,
              "hints": [],
              "tokens": [
                {"text": "find"},
                {"text": "."},
                {"text": "-name"},
                {"text": "unused_token"}
              ],
              "solution": [0, 1, 2]
            }
        """.trimIndent()
        val (valid, errors) = DatasetParser.parseQuestions("[$unusedTokensJson]")
        assertTrue(valid.isEmpty())
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must use every token exactly once"))
    }
}