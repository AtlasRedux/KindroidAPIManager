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
fun DiscordScreen(
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
        Text("Discord Settings", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = profile.discordToken,
            onValueChange = { onProfileChange(profile.copy(discordToken = it)) },
            label = { Text("Bot Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = profile.discordEnabled,
                onCheckedChange = { onProfileChange(profile.copy(discordEnabled = it)) }
            )
            Text("Enable Discord Bot")
        }
    }
}
