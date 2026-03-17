package com.nytte.kindroidbotmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nytte.kindroidbotmanager.data.model.BotProfile

@Composable
fun TwitchScreen(
    profile: BotProfile,
    onProfileChange: (BotProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Twitch Settings", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = profile.twitchUsername,
            onValueChange = { onProfileChange(profile.copy(twitchUsername = it)) },
            label = { Text("Bot Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = profile.twitchChannel,
            onValueChange = { onProfileChange(profile.copy(twitchChannel = it)) },
            label = { Text("Channel") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = profile.twitchOAuth,
            onValueChange = { onProfileChange(profile.copy(twitchOAuth = it)) },
            label = { Text("OAuth Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = profile.twitchEnabled,
                onCheckedChange = { onProfileChange(profile.copy(twitchEnabled = it)) }
            )
            Text("Enable Twitch Bot")
        }
    }
}
