package com.nytte.kindroidbotmanager.network

import com.nytte.kindroidbotmanager.util.LogEntry
import com.nytte.kindroidbotmanager.util.LogTag
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TwitchIrcClient(
    username: String,
    oauthToken: String,
    channel: String,
    private val kindroidApi: KindroidApiClient,
    private val log: (LogEntry) -> Unit,
    private val scope: CoroutineScope
) {
    // Normalize inputs (matches TwitchBot.cpp lines 13-24)
    private val username = username.lowercase().trim()
    private val channel = channel.lowercase().trim().removePrefix("#")
    private val oauthToken = oauthToken.trim().let {
        if (it.startsWith("oauth:", ignoreCase = true)) it else "oauth:$it"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val running = AtomicBoolean(false)
    private var webSocket: WebSocket? = null

    fun start() {
        if (running.getAndSet(true)) return
        log(LogEntry(LogTag.INFO, "Twitch bot starting for #$channel..."))
        scope.launch { connectionLoop() }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        log(LogEntry(LogTag.INFO, "Twitch bot stopping..."))
        webSocket?.close(1000, "Shutting down")
        webSocket = null
    }

    val isConnected: Boolean get() = running.get() && webSocket != null

    private suspend fun connectionLoop() {
        while (running.get()) {
            try {
                connect()
            } catch (e: Exception) {
                if (!running.get()) break
                log(LogEntry(LogTag.ERROR, "Twitch connection error: ${e.message}"))
            }
            if (!running.get()) break
            log(LogEntry(LogTag.INFO, "Twitch reconnecting in 5 seconds..."))
            delay(5000)
        }
    }

    private suspend fun connect() {
        val request = Request.Builder()
            .url("wss://irc-ws.chat.twitch.tv:443")
            .build()

        val completable = CompletableDeferred<Unit>()
        val listener = TwitchWebSocketListener(completable)
        webSocket = client.newWebSocket(request, listener)

        completable.await()
        webSocket = null
    }

    private inner class TwitchWebSocketListener(
        private val completable: CompletableDeferred<Unit>
    ) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            log(LogEntry(LogTag.DEBUG, "Twitch WebSocket connected"))
            // Send IRC auth sequence (matches TwitchBot.cpp lines 229-248)
            webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
            webSocket.send("PASS $oauthToken")
            webSocket.send("NICK $username")
            webSocket.send("JOIN #$channel")
            log(LogEntry(LogTag.INFO, "Twitch: Joining #$channel as $username"))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Twitch sends multiple IRC lines per WebSocket frame
            text.split("\r\n").filter { it.isNotBlank() }.forEach { line ->
                try {
                    handleIrcLine(webSocket, line)
                } catch (e: Exception) {
                    log(LogEntry(LogTag.ERROR, "Error handling Twitch line: ${e.message}"))
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            log(LogEntry(LogTag.ERROR, "Twitch WebSocket failure: ${t.message}"))
            completable.complete(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            log(LogEntry(LogTag.INFO, "Twitch WebSocket closed: $code $reason"))
            completable.complete(Unit)
        }
    }

    private fun handleIrcLine(ws: WebSocket, line: String) {
        // Handle PING (matches TwitchBot.cpp lines 453-458)
        if (line.startsWith("PING")) {
            val rest = line.removePrefix("PING").trim()
            ws.send("PONG $rest")
            log(LogEntry(LogTag.DEBUG, "Twitch PONG"))
            return
        }

        // Handle PRIVMSG
        if (!line.contains("PRIVMSG")) return

        // Parse IRC format: @tags :user!user@user.tmi.twitch.tv PRIVMSG #channel :message
        // Extract sender (between first ':' and '!')
        val senderStart = line.indexOf(':')
        val senderEnd = line.indexOf('!')
        if (senderStart < 0 || senderEnd < 0 || senderEnd <= senderStart) return
        val sender = line.substring(senderStart + 1, senderEnd)

        // Skip own messages
        if (sender.equals(username, ignoreCase = true)) return

        // Extract message content (after the second ':' which follows PRIVMSG #channel)
        val privmsgIndex = line.indexOf("PRIVMSG")
        if (privmsgIndex < 0) return
        val afterPrivmsg = line.substring(privmsgIndex)
        val colonIndex = afterPrivmsg.indexOf(':')
        if (colonIndex < 0) return
        val messageContent = afterPrivmsg.substring(colonIndex + 1)

        // Check for mention (matches TwitchBot.cpp mention detection)
        val mentionTag = "@$username"
        if (!messageContent.contains(mentionTag, ignoreCase = true)) return

        // Strip mention and trim
        val cleanMessage = messageContent
            .replace(Regex("@${Regex.escape(username)}", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleanMessage.isEmpty()) return

        log(LogEntry(LogTag.TWITCH, "$sender: $cleanMessage"))

        // Process in background
        scope.launch {
            try {
                val result = kindroidApi.sendMessage(sender, channel, cleanMessage)
                result.onSuccess { response ->
                    if (response.isNotBlank()) {
                        sendChatMessage(ws, "@$sender $response")
                    }
                }
                result.onFailure { e ->
                    log(LogEntry(LogTag.ERROR, "Failed to get Kindroid response: ${e.message}"))
                }
            } catch (e: Exception) {
                log(LogEntry(LogTag.ERROR, "Error processing Twitch message: ${e.message}"))
            }
        }
    }

    /**
     * Send a chat message, splitting at 450 chars on word boundaries.
     * Matches TwitchBot.cpp lines 417-448.
     */
    private fun sendChatMessage(ws: WebSocket, message: String) {
        val maxLen = 450
        var remaining = message

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLen) {
                ws.send("PRIVMSG #$channel :$remaining")
                log(LogEntry(LogTag.DEBUG, "Twitch sent: $remaining"))
                break
            }

            // Find last space before maxLen
            var splitAt = remaining.lastIndexOf(' ', maxLen)
            if (splitAt <= 0) splitAt = maxLen

            val chunk = remaining.substring(0, splitAt).trim()
            remaining = remaining.substring(splitAt).trim()

            ws.send("PRIVMSG #$channel :$chunk")
            log(LogEntry(LogTag.DEBUG, "Twitch sent chunk: $chunk"))
        }
    }

    fun sendAnnouncement(message: String) {
        val ws = webSocket ?: return
        scope.launch(Dispatchers.IO) {
            sendChatMessage(ws, message)
            log(LogEntry(LogTag.ANNOUNCE, "Twitch announcement sent"))
        }
    }

    fun shutdown() {
        stop()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
