package com.example.quizapp.data.net

import org.json.JSONObject
import java.time.Instant

object PingProtocol {

    private const val LINE_TERMINATOR = '\n'

    data class Ping(val from: String, val ts: String)
    data class Pong(val ts: String)

    sealed interface Message {
        data class PingMsg(val from: String, val ts: String) : Message
        data class PongMsg(val ts: String) : Message
        data class ErrorMsg(val reason: String) : Message
    }

    fun encode(msg: Message): ByteArray {
        val payload = when (msg) {
            is Message.PingMsg -> """{"type":"ping","from":"${msg.from}","ts":"${msg.ts}"}"""
            is Message.PongMsg -> """{"type":"pong","ts":"${msg.ts}"}"""
            is Message.ErrorMsg -> """{"type":"error","reason":"${msg.reason}"}"""
        }
        return (payload + LINE_TERMINATOR).toByteArray(Charsets.UTF_8)
    }

    fun decode(raw: String): Message? {
        val obj = try { JSONObject(raw.trim()) } catch (_: Exception) { return null }
        return when (obj.optString("type")) {
            "ping" -> Message.PingMsg(obj.optString("from"), obj.optString("ts"))
            "pong" -> Message.PongMsg(obj.optString("ts"))
            "error" -> Message.ErrorMsg(obj.optString("reason"))
            else -> null
        }
    }

    fun nowIso(): String = Instant.now().toString()
}
