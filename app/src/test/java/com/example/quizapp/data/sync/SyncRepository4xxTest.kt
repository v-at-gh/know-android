package com.example.quizapp.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRepository4xxTest {

    @Test
    fun testClassifyResponse() {
        assertEquals(SyncOutcome.Success, classifyResponse(200))
        assertEquals(SyncOutcome.PermanentFailure, classifyResponse(400))
        assertEquals(SyncOutcome.PermanentFailure, classifyResponse(422))
        assertEquals(SyncOutcome.TransientFailure, classifyResponse(500))
        assertEquals(SyncOutcome.TransientFailure, classifyResponse(503))
    }
}
