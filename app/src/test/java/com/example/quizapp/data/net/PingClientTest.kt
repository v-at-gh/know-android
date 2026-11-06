package com.example.quizapp.data.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
class PingClientTest {

    @Test
    fun `ping success returns Ok with rtt`() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            try {
                val client = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                reader.readLine() // read ping
                val pong = PingProtocol.Message.PongMsg(ts = PingProtocol.nowIso())
                client.getOutputStream().write(PingProtocol.encode(pong))
                client.getOutputStream().flush()
                client.close()
            } catch (_: Exception) {}
        }

        val client = PingClient()
        val result = client.ping("127.0.0.1", port)
        serverSocket.close()
        executor.shutdown()

        assertTrue(result is PingResult.Ok)
        assertTrue((result as PingResult.Ok).rttMs >= 0)
    }

    @Test
    fun `ping timeout returns Timeout`() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            try {
                val client = serverSocket.accept()
                // Do not respond, hang until timeout
                Thread.sleep(2000L)
                client.close()
            } catch (_: Exception) {}
        }

        val client = PingClient(timeoutMs = 500L)
        val result = client.ping("127.0.0.1", port)
        serverSocket.close()
        executor.shutdown()

        assertTrue(result is PingResult.Timeout)
    }
}
