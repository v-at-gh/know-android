package com.example.quizapp.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface PingResult {
    data class Ok(val rttMs: Long) : PingResult
    data class Timeout(val ms: Long) : PingResult
    data class Refused(val message: String) : PingResult
    data class Unreachable(val message: String) : PingResult
    data class Malformed(val raw: String) : PingResult
    data class Error(val message: String) : PingResult
}

class PingClient(
    private val timeoutMs: Long = 5000L,
) {
    suspend fun ping(host: String, port: Int = 42069): PingResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs.toInt()
                socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())

                val outputStream = socket.getOutputStream()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

                val pingMsg = PingProtocol.Message.PingMsg(from = "app", ts = PingProtocol.nowIso())
                outputStream.write(PingProtocol.encode(pingMsg))
                outputStream.flush()

                val responseLine = reader.readLine() ?: return@withContext PingResult.Malformed("")
                val decoded = PingProtocol.decode(responseLine)
                when (decoded) {
                    is PingProtocol.Message.PongMsg -> {
                        val rtt = System.currentTimeMillis() - startTime
                        PingResult.Ok(rtt)
                    }
                    is PingProtocol.Message.ErrorMsg -> {
                        PingResult.Error(decoded.reason)
                    }
                    else -> {
                        PingResult.Malformed(responseLine)
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e("PingClient", "Timeout connecting to $host:$port", e)
            PingResult.Timeout(timeoutMs)
        } catch (e: ConnectException) {
            Log.e("PingClient", "Connection refused to $host:$port", e)
            PingResult.Refused(e.message ?: "Connection refused")
        } catch (e: NoRouteToHostException) {
            Log.e("PingClient", "No route to host $host:$port", e)
            PingResult.Unreachable(e.message ?: "No route to host")
        } catch (e: UnknownHostException) {
            Log.e("PingClient", "Unknown host $host", e)
            PingResult.Unreachable(e.message ?: "Unknown host")
        } catch (e: java.io.IOException) {
            Log.e("PingClient", "IO error connecting to $host:$port", e)
            PingResult.Error(e.message ?: "IO error")
        } catch (e: Exception) {
            Log.e("PingClient", "Unexpected error connecting to $host:$port", e)
            PingResult.Error(e.message ?: "Unexpected error")
        }
    }
}
