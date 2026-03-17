package com.nytte.kindroidbotmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nytte.kindroidbotmanager.data.model.BotProfile

@Composable
fun AnnounceScreen(
    profile: BotProfile,
    isRunning: Boolean,
    onProfileChange: (BotProfile) -> Unit,
    onSendNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Announcement Settings", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = profile.announceMessage,
            onValueChange = { onProfileChange(profile.copy(announceMessage = it)) },
            label = { Text("Announcement Message") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = profile.announceDiscordChannel,
            onValueChange = { onProfileChange(profile.copy(announceDiscordChannel = it)) },
            label = { Text("Discord Channel ID (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Interval", style = MaterialTheme.typography.titleSmall)

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = if (profile.announceHours == 0) "" else profile.announceHours.toString(),
                onValueChange = {
                    val hours = it.filter { c -> c.isDigit() }.take(3).toIntOrNull() ?: 0
                    onProfileChange(profile.copy(announceHours = hours))
                },
                label = { Text("Hours") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = if (profile.announceMins == 0) "" else profile.announceMins.toString(),
                onValueChange = {
                    val mins = it.filter { c -> c.isDigit() }.take(2).toIntOrNull() ?: 0
                    onProfileChange(profile.copy(announceMins = mins.coerceAtMost(59)))
                },
                label = { Text("Minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = profile.announceDiscord,
                onCheckedChange = { onProfileChange(profile.copy(announceDiscord = it)) }
            )
            Text("Send to Discord")
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = profile.announceTwitch,
                onCheckedChange = { onProfileChange(profile.copy(announceTwitch = it)) }
            )
            Text("Send to Twitch")
        }

        Button(
            onClick = onSendNow,
            enabled = isRunning && profile.announceMessage.isNotBlank()
                    && (profile.announceDiscord || profile.announceTwitch),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Now")
        }
    }
}
