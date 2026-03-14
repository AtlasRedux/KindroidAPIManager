package com.nytte.kindroidbotmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nytte.kindroidbotmanager.ui.theme.AccentBlue
import com.nytte.kindroidbotmanager.ui.theme.ErrorRed
import com.nytte.kindroidbotmanager.ui.theme.SuccessGreen

@Composable
fun BotControlBar(
    isRunning: Boolean,
    discordConnected: Boolean,
    twitchConnected: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Start/Stop buttons
        Button(
            onClick = onStart,
            enabled = !isRunning,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text("Start")
        }

        OutlinedButton(
            onClick = onStop,
            enabled = isRunning,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
        ) {
            Text("Stop")
        }

        Spacer(Modifier.weight(1f))

        // Status indicators
        StatusDot("Discord", discordConnected)
        Spacer(Modifier.width(8.dp))
        StatusDot("Twitch", twitchConnected)
    }
}

@Composable
private fun StatusDot(label: String, connected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (connected) SuccessGreen else ErrorRed)
        )
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
