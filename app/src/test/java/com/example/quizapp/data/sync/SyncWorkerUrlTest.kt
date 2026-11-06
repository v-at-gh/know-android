package com.example.quizapp.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncWorkerUrlTest {

    @Test
    fun testBuildFeedbackBaseUrl() {
        val cases = listOf(
            "1.2.3.4" to "http://1.2.3.4:8001",
            "http://1.2.3.4" to "http://1.2.3.4:8001",
            "https://1.2.3.4" to "http://1.2.3.4:8001",
            "1.2.3.4:8001" to "http://1.2.3.4:8001",
            "http://1.2.3.4:8001" to "http://1.2.3.4:8001",
            " 1.2.3.4 " to "http://1.2.3.4:8001",
            "1.2.3.4/" to "http://1.2.3.4:8001",
        )
        cases.forEach { (input, expected) ->
            assertEquals("input='$input'", expected, buildFeedbackBaseUrl(input))
        }
        assertThrows(IllegalArgumentException::class.java) { buildFeedbackBaseUrl("") }
        assertThrows(IllegalArgumentException::class.java) { buildFeedbackBaseUrl("   ") }
        assertThrows(IllegalArgumentException::class.java) { buildFeedbackBaseUrl("://") }
    }
}
