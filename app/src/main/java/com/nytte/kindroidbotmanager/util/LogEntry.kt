package com.nytte.kindroidbotmanager.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class LogTag {
    INFO, ERROR, DEBUG, DISCORD, TWITCH, KINDROID, ANNOUNCE
}

data class LogEntry(
    val tag: LogTag,
    val message: String,
    val timestamp: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
) {
    fun formatted(): String = "[$timestamp] [${tag.name}] $message"
}
