package com.example.quizapp.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PingProtocolTest {

    @Test
    fun `ping message encode and decode round-trip`() {
        val original = PingProtocol.Message.PingMsg(from = "app", ts = "2026-10-16T12:34:56.789Z")
        val encoded = PingProtocol.encode(original)
        val decoded = PingProtocol.decode(String(encoded, Charsets.UTF_8))
        assertEquals(original, decoded)
    }

    @Test
    fun `pong message encode and decode round-trip`() {
        val original = PingProtocol.Message.PongMsg(ts = "2026-10-16T12:34:56.792Z")
        val encoded = PingProtocol.encode(original)
        val decoded = PingProtocol.decode(String(encoded, Charsets.UTF_8))
        assertEquals(original, decoded)
    }

    @Test
    fun `error message encode and decode round-trip`() {
        val original = PingProtocol.Message.ErrorMsg(reason = "unknown type")
        val encoded = PingProtocol.encode(original)
        val decoded = PingProtocol.decode(String(encoded, Charsets.UTF_8))
        assertEquals(original, decoded)
    }

    @Test
    fun `decode malformed string returns null`() {
        val malformed = "not valid json\n"
        val decoded = PingProtocol.decode(malformed)
        assertNull(decoded)
    }
}
