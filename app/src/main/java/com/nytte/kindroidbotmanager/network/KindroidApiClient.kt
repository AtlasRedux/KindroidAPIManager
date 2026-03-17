package com.nytte.kindroidbotmanager.network

import com.nytte.kindroidbotmanager.util.LogEntry
import com.nytte.kindroidbotmanager.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class KindroidApiClient(
    private val apiKey: String,
    private val aiId: String,
    private val baseUrl: String,
    private val log: (LogEntry) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Send a message to the Kindroid AI and return the response text.
     * Message format matches KindroidAPI.cpp: "<Message to you from USER in channel CHAN> text"
     */
    suspend fun sendMessage(username: String, channelName: String, message: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val formattedMessage = "<Message to you from $username in channel $channelName> $message"

                val body = buildJsonObject {
                    put("ai_id", aiId)
                    put("message", formattedMessage)
                }.toString()

                log(LogEntry(LogTag.DEBUG, "Sending to Kindroid: $formattedMessage"))

                val request = Request.Builder()
                    .url("$baseUrl/send-message")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    log(LogEntry(LogTag.ERROR, "Kindroid API error ${response.code}: $responseBody"))
                    return@withContext Result.failure(Exception("API error ${response.code}: $responseBody"))
                }

                log(LogEntry(LogTag.DEBUG, "Kindroid response (${response.code}, ${responseBody.length} bytes)"))

                // Handle plain text vs JSON (matches KindroidAPI.cpp behaviour)
                val responseText = if (responseBody.trimStart().startsWith("{")) {
                    val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject

                    // Check for error field
                    jsonResponse["error"]?.jsonPrimitive?.contentOrNull?.let { error ->
                        log(LogEntry(LogTag.ERROR, "Kindroid API error: $error"))
                        return@withContext Result.failure(Exception(error))
                    }

                    // Try response_text first, then fallback to response
                    jsonResponse["response_text"]?.jsonPrimitive?.contentOrNull
                        ?: jsonResponse["response"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                } else {
                    // Plain text response — return as-is
                    responseBody.trim()
                }

                log(LogEntry(LogTag.KINDROID, responseText))
                Result.success(responseText)
            } catch (e: Exception) {
                log(LogEntry(LogTag.ERROR, "Kindroid API exception: ${e.message}"))
                Result.failure(e)
            }
        }

    /**
     * Send a direct message (no username/channel context).
     */
    suspend fun sendDirectMessage(message: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = buildJsonObject {
                    put("ai_id", aiId)
                    put("message", message)
                }.toString()

                log(LogEntry(LogTag.DEBUG, "Direct message: $message"))

                val request = Request.Builder()
                    .url("$baseUrl/send-message")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    log(LogEntry(LogTag.ERROR, "Kindroid API error ${response.code}: $responseBody"))
                    return@withContext Result.failure(Exception("API error ${response.code}"))
                }

                log(LogEntry(LogTag.DEBUG, "Kindroid direct response (${response.code}, ${responseBody.length} bytes)"))

                val responseText = if (responseBody.trimStart().startsWith("{")) {
                    val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject
                    jsonResponse["response_text"]?.jsonPrimitive?.contentOrNull
                        ?: jsonResponse["response"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                } else {
                    responseBody.trim()
                }

                log(LogEntry(LogTag.KINDROID, responseText))
                Result.success(responseText)
            } catch (e: Exception) {
                log(LogEntry(LogTag.ERROR, "Kindroid API exception: ${e.message}"))
                Result.failure(e)
            }
        }

    fun shutdown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
