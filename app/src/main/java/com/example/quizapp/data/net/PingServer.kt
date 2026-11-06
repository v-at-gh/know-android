package com.example.quizapp.data.net

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class PingServer(
    private val port: Int = 42069,
    private val onEvent: (String) -> Unit = {},
) {
    private var serverSocket: ServerSocket? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    val isRunning: Boolean
        get() = serverSocket != null && !serverSocket!!.isClosed

    fun start() {
        if (isRunning) return
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        scope.launch {
            try {
                val server = ServerSocket(port).apply {
                    reuseAddress = true
                }
                serverSocket = server
                Log.d("PingServer", "Started on port $port")
                while (isActive && !server.isClosed) {
                    try {
                        val clientSocket = server.accept()
                        launch { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (!server.isClosed) {
                            Log.e("PingServer", "Error accepting connection", e)
                        }
                        break
                    }
                }
            } catch (e: BindException) {
                Log.e("PingServer", "Bind failed on port $port: ${e.message}", e)
                onEvent("bind failed: 42069 in use")
            } catch (e: Exception) {
                Log.e("PingServer", "Server error on port $port", e)
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        scope.cancel()
        Log.d("PingServer", "Stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val outputStream = s.getOutputStream()

                val line = reader.readLine()
                if (line == null) {
                    return
                }

                val timeStr = LocalTime.now().format(timeFormatter)
                val clientAddress = s.inetAddress?.hostAddress ?: "unknown"

                val decoded = PingProtocol.decode(line)
                when (decoded) {
                    is PingProtocol.Message.PingMsg -> {
                        onEvent("$timeStr ping from $clientAddress")
                        val pong = PingProtocol.Message.PongMsg(ts = PingProtocol.nowIso())
                        outputStream.write(PingProtocol.encode(pong))
                        outputStream.flush()
                    }
                    else -> {
                        onEvent("$timeStr error from $clientAddress: malformed or unknown")
                        val err = PingProtocol.Message.ErrorMsg(
                            reason = if (decoded == null) "malformed" else "unknown type"
                        )
                        outputStream.write(PingProtocol.encode(err))
                        outputStream.flush()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PingServer", "Error handling client connection", e)
        }
    }
}
