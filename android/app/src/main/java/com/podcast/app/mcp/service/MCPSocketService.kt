package com.podcast.app.mcp.service

import android.app.Service
import android.content.Intent
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.IBinder
import com.podcast.app.mcp.bridge.MCPCommandHandler
import com.podcast.app.mcp.models.MCPRequest
import com.podcast.app.mcp.models.MCPResponse
import com.podcast.app.mcp.models.MCPStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * MCP Socket Service for IPC with the Python MCP server.
 *
 * Uses abstract namespace sockets (LocalServerSocket) for secure
 * inter-process communication on Android.
 *
 * Security features:
 * - Abstract namespace sockets (not filesystem-based)
 * - HMAC authentication for incoming requests
 * - Input validation on all commands
 * - Rate limiting
 */
@AndroidEntryPoint
class MCPSocketService : Service() {

    @Inject
    lateinit var commandHandler: MCPCommandHandler

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: LocalServerSocket? = null
    private var isRunning = false

    // Session key for HMAC authentication (generated on service start)
    private val sessionKey: String by lazy { generateSessionKey() }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Rate limiting
    private val requestTimes = mutableListOf<Long>()
    private val maxRequestsPerMinute = 60

    override fun onCreate() {
        super.onCreate()
        startSocketServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serverSocket?.close()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSocketServer() {
        serviceScope.launch {
            try {
                // Abstract namespace socket (no filesystem path)
                serverSocket = LocalServerSocket(SOCKET_NAME)
                isRunning = true

                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(clientSocket: LocalSocket) {
        serviceScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                val writer = PrintWriter(clientSocket.outputStream, true)

                val requestLine = reader.readLine() ?: return@launch

                // Rate limiting check
                if (!checkRateLimit()) {
                    val errorResponse = MCPResponse(
                        id = UUID.randomUUID().toString(),
                        status = MCPStatus.ERROR,
                        action = "unknown",
                        error = "Rate limit exceeded"
                    )
                    writer.println(json.encodeToString(MCPResponse.serializer(), errorResponse))
                    return@launch
                }

                val request = try {
                    json.decodeFromString(MCPRequest.serializer(), requestLine)
                } catch (e: Exception) {
                    val errorResponse = MCPResponse(
                        id = UUID.randomUUID().toString(),
                        status = MCPStatus.INVALID_REQUEST,
                        action = "unknown",
                        error = "Invalid request format"
                    )
                    writer.println(json.encodeToString(MCPResponse.serializer(), errorResponse))
                    return@launch
                }

                // Verify HMAC authentication
                if (!verifyAuth(request)) {
                    val errorResponse = MCPResponse(
                        id = request.id,
                        status = MCPStatus.UNAUTHORIZED,
                        action = request.action,
                        error = "Authentication failed"
                    )
                    writer.println(json.encodeToString(MCPResponse.serializer(), errorResponse))
                    return@launch
                }

                // Handle the command
                val response = commandHandler.handleCommand(request)
                writer.println(json.encodeToString(MCPResponse.serializer(), response))

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    clientSocket.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
            }
        }
    }

    private fun verifyAuth(request: MCPRequest): Boolean {
        val authToken = request.authToken ?: return false
        val expectedToken = generateHmac(request.id + request.action + request.timestamp)
        return authToken == expectedToken
    }

    private fun generateHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(sessionKey.toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray())
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSessionKey(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60_000

        // Remove old entries
        requestTimes.removeAll { it < oneMinuteAgo }

        if (requestTimes.size >= maxRequestsPerMinute) {
            return false
        }

        requestTimes.add(now)
        return true
    }

    /**
     * Get the session key for external authentication.
     * This should be securely shared with the MCP server.
     */
    fun retrieveSessionKey(): String = sessionKey

    companion object {
        // Abstract namespace socket (starts with null byte internally)
        const val SOCKET_NAME = "podcast_app_mcp"
    }
}
