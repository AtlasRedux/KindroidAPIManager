package com.nytte.kindroidbotmanager.network

import com.nytte.kindroidbotmanager.util.LogEntry
import com.nytte.kindroidbotmanager.util.LogTag
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DiscordGateway(
    private val token: String,
    private val kindroidApi: KindroidApiClient,
    private val log: (LogEntry) -> Unit,
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No read timeout for WebSocket
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val restClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val running = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var sessionId: String? = null
    private val sequenceNumber = AtomicInteger(-1)
    private val channelNameCache = mutableMapOf<String, String>()
    private var guildName: String? = null
    private var botUserId: String? = null
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun start() {
        if (running.getAndSet(true)) return
        log(LogEntry(LogTag.INFO, "Discord bot starting..."))
        scope.launch { connectionLoop() }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        log(LogEntry(LogTag.INFO, "Discord bot stopping..."))
        heartbeatJob?.cancel()
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
                log(LogEntry(LogTag.ERROR, "Discord connection error: ${e.message}"))
            }
            if (!running.get()) break
            log(LogEntry(LogTag.INFO, "Discord reconnecting in 5 seconds..."))
            delay(5000)
        }
    }

    private suspend fun connect() {
        // Step 1: Get gateway URL
        val gatewayUrl = getGatewayUrl() ?: run {
            log(LogEntry(LogTag.ERROR, "Failed to get Discord gateway URL"))
            return
        }
        log(LogEntry(LogTag.DEBUG, "Gateway URL: $gatewayUrl"))

        // Step 2: Connect WebSocket
        val wsUrl = "$gatewayUrl/?v=10&encoding=json"
        val request = Request.Builder().url(wsUrl).build()

        val completable = CompletableDeferred<Unit>()
        val listener = DiscordWebSocketListener(completable)
        webSocket = client.newWebSocket(request, listener)

        // Wait until disconnected
        completable.await()
        heartbeatJob?.cancel()
        webSocket = null
    }

    private fun getGatewayUrl(): String? {
        val request = Request.Builder()
            .url("https://discord.com/api/v10/gateway")
            .get()
            .build()
        return try {
            val response = restClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = Json.parseToJsonElement(body).jsonObject
            json["url"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            log(LogEntry(LogTag.ERROR, "Failed to get gateway: ${e.message}"))
            null
        }
    }

    private inner class DiscordWebSocketListener(
        private val completable: CompletableDeferred<Unit>
    ) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            log(LogEntry(LogTag.DEBUG, "Discord WebSocket connected"))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                handleGatewayMessage(webSocket, text)
            } catch (e: Exception) {
                log(LogEntry(LogTag.ERROR, "Error handling Discord message: ${e.message}"))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            log(LogEntry(LogTag.ERROR, "Discord WebSocket failure: ${t.message}"))
            completable.complete(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            log(LogEntry(LogTag.INFO, "Discord WebSocket closed: $code $reason"))
            completable.complete(Unit)
        }
    }

    private fun handleGatewayMessage(ws: WebSocket, text: String) {
        val json = Json.parseToJsonElement(text).jsonObject
        val op = json["op"]?.jsonPrimitive?.intOrNull ?: return
        val d = json["d"]
        val s = json["s"]?.jsonPrimitive?.intOrNull
        val t = json["t"]?.jsonPrimitive?.contentOrNull

        // Update sequence number
        if (s != null) sequenceNumber.set(s)

        when (op) {
            10 -> handleHello(ws, d)       // HELLO
            0 -> handleDispatch(ws, t, d)  // DISPATCH
            1 -> sendHeartbeat(ws)         // Heartbeat request
            7 -> {                         // Reconnect
                log(LogEntry(LogTag.INFO, "Discord requested reconnect"))
                ws.close(4000, "Reconnect requested")
            }
            9 -> {                         // Invalid session
                log(LogEntry(LogTag.INFO, "Discord invalid session, re-identifying"))
                sessionId = null
                sendIdentify(ws)
            }
            11 -> {                        // Heartbeat ACK
                log(LogEntry(LogTag.DEBUG, "Heartbeat ACK"))
            }
        }
    }

    private fun handleHello(ws: WebSocket, d: JsonElement?) {
        val interval = d?.jsonObject?.get("heartbeat_interval")?.jsonPrimitive?.longOrNull ?: 41250
        log(LogEntry(LogTag.DEBUG, "Heartbeat interval: ${interval}ms"))

        // Start heartbeat
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            // Initial jitter
            delay((interval * Math.random()).toLong())
            while (isActive && running.get()) {
                sendHeartbeat(ws)
                delay(interval)
            }
        }

        // Send IDENTIFY or RESUME
        if (sessionId != null) {
            sendResume(ws)
        } else {
            sendIdentify(ws)
        }
    }

    private fun sendIdentify(ws: WebSocket) {
        val payload = buildJsonObject {
            put("op", 2)
            putJsonObject("d") {
                put("token", token)
                put("intents", 33281) // GUILDS + GUILD_MESSAGES + MESSAGE_CONTENT
                putJsonObject("properties") {
                    put("os", "android")
                    put("browser", "kindroid_bot_android")
                    put("device", "kindroid_bot_android")
                }
            }
        }.toString()
        ws.send(payload)
        log(LogEntry(LogTag.DEBUG, "Sent IDENTIFY"))
    }

    private fun sendResume(ws: WebSocket) {
        val payload = buildJsonObject {
            put("op", 6)
            putJsonObject("d") {
                put("token", token)
                put("session_id", sessionId)
                put("seq", sequenceNumber.get())
            }
        }.toString()
        ws.send(payload)
        log(LogEntry(LogTag.DEBUG, "Sent RESUME"))
    }

    private fun sendHeartbeat(ws: WebSocket) {
        val seq = sequenceNumber.get()
        val payload = buildJsonObject {
            put("op", 1)
            put("d", if (seq >= 0) JsonPrimitive(seq) else JsonNull)
        }.toString()
        ws.send(payload)
        log(LogEntry(LogTag.DEBUG, "Sent heartbeat (seq=$seq)"))
    }

    private fun handleDispatch(ws: WebSocket, eventName: String?, d: JsonElement?) {
        val data = d?.jsonObject ?: return
        when (eventName) {
            "READY" -> {
                sessionId = data["session_id"]?.jsonPrimitive?.contentOrNull
                botUserId = data["user"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                log(LogEntry(LogTag.INFO, "Discord bot ready! User ID: $botUserId"))
            }
            "MESSAGE_CREATE" -> handleMessageCreate(data)
        }
    }

    private fun handleMessageCreate(data: JsonObject) {
        val content = data["content"]?.jsonPrimitive?.contentOrNull ?: return
        val author = data["author"]?.jsonObject ?: return
        val authorId = author["id"]?.jsonPrimitive?.contentOrNull ?: return
        val username = author["username"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
        val channelId = data["channel_id"]?.jsonPrimitive?.contentOrNull ?: return
        val guildId = data["guild_id"]?.jsonPrimitive?.contentOrNull

        // Ignore own messages
        if (authorId == botUserId) return

        // Check if bot is mentioned: look for <@BOT_ID> in content
        val mentionTag = "<@$botUserId>"
        if (botUserId == null || !content.contains(mentionTag)) return

        // Strip mention and trim
        val cleanMessage = content.replace(mentionTag, "").trim()
        if (cleanMessage.isEmpty()) return

        log(LogEntry(LogTag.DISCORD, "$username: $cleanMessage"))

        // Process in background
        scope.launch {
            try {
                // Resolve channel and guild names
                val channelName = getChannelName(channelId) ?: channelId
                val serverName = if (guildId != null) getGuildName(guildId) else "DM"

                val fullChannelName = "$serverName/$channelName"
                val result = kindroidApi.sendMessage(username, fullChannelName, cleanMessage)

                result.onSuccess { response ->
                    log(LogEntry(LogTag.DEBUG, "Kindroid replied (${response.length} chars): ${response.take(100)}"))
                    if (response.isNotBlank()) {
                        val reply = "<@$authorId> $response"
                        log(LogEntry(LogTag.DEBUG, "Sending reply to Discord channel $channelId..."))
                        sendDiscordMessage(channelId, reply)
                    } else {
                        log(LogEntry(LogTag.ERROR, "Kindroid returned blank response — nothing to send"))
                    }
                }
                result.onFailure { e ->
                    log(LogEntry(LogTag.ERROR, "Failed to get Kindroid response: ${e.message}"))
                }
            } catch (e: Exception) {
                log(LogEntry(LogTag.ERROR, "Error processing Discord message: ${e.message}"))
            }
        }
    }

    private fun getChannelName(channelId: String): String? {
        channelNameCache[channelId]?.let { return it }
        return try {
            val request = Request.Builder()
                .url("https://discord.com/api/v10/channels/$channelId")
                .addHeader("Authorization", "Bot $token")
                .get()
                .build()
            val response = restClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = Json.parseToJsonElement(body).jsonObject
            val name = json["name"]?.jsonPrimitive?.contentOrNull ?: return null
            channelNameCache[channelId] = name
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun getGuildName(guildId: String): String? {
        if (guildName != null) return guildName
        return try {
            val request = Request.Builder()
                .url("https://discord.com/api/v10/guilds/$guildId")
                .addHeader("Authorization", "Bot $token")
                .get()
                .build()
            val response = restClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = Json.parseToJsonElement(body).jsonObject
            val name = json["name"]?.jsonPrimitive?.contentOrNull
            guildName = name
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun sendDiscordMessage(channelId: String, message: String) {
        try {
            val body = buildJsonObject {
                put("content", message)
            }.toString()

            val request = Request.Builder()
                .url("https://discord.com/api/v10/channels/$channelId/messages")
                .addHeader("Authorization", "Bot $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonMediaType))
                .build()

            val response = restClient.newCall(request).execute()
            if (!response.isSuccessful) {
                log(LogEntry(LogTag.ERROR, "Failed to send Discord message: ${response.code}"))
            } else {
                log(LogEntry(LogTag.DEBUG, "Sent Discord reply to channel $channelId"))
            }
            response.close()
        } catch (e: Exception) {
            log(LogEntry(LogTag.ERROR, "Error sending Discord message: ${e.message}"))
        }
    }

    fun sendAnnouncement(message: String, channelId: String?) {
        val targetChannel = channelId ?: return
        scope.launch(Dispatchers.IO) {
            sendDiscordMessage(targetChannel, message)
            log(LogEntry(LogTag.ANNOUNCE, "Discord announcement sent"))
        }
    }

    fun shutdown() {
        stop()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        restClient.dispatcher.executorService.shutdown()
        restClient.connectionPool.evictAll()
    }
}
