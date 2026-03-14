package com.nytte.kindroidbotmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BotProfile(
    val profileName: String = "",
    val apiKey: String = "",
    val aiId: String = "",
    val baseUrl: String = "https://api.kindroid.ai/v1",
    val personaName: String = "User",
    val discordToken: String = "",
    val discordEnabled: Boolean = true,
    val twitchUsername: String = "",
    val twitchOAuth: String = "",
    val twitchChannel: String = "",
    val twitchEnabled: Boolean = false,
    // Phase 2 fields - stored but not used in UI yet
    val announceMessage: String = "",
    val announceDiscordChannel: String = "",
    val announceHours: Int = 0,
    val announceMins: Int = 30,
    val announceDiscord: Boolean = false,
    val announceTwitch: Boolean = false
)
