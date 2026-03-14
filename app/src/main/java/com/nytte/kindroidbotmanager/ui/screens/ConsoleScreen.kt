package com.nytte.kindroidbotmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nytte.kindroidbotmanager.ui.theme.*
import com.nytte.kindroidbotmanager.util.LogEntry
import com.nytte.kindroidbotmanager.util.LogTag

@Composable
fun ConsoleScreen(
    logLines: List<LogEntry>,
    debugMode: Boolean,
    onToggleDebug: () -> Unit,
    onClearLog: () -> Unit,
    onSendDirect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var directMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Filter debug lines unless debug mode is on
    val filteredLines = if (debugMode) logLines else logLines.filter { it.tag != LogTag.DEBUG }

    // Auto-scroll to bottom
    LaunchedEffect(filteredLines.size) {
        if (filteredLines.isNotEmpty()) {
            listState.animateScrollToItem(filteredLines.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Checkbox(
                    checked = debugMode,
                    onCheckedChange = { onToggleDebug() },
                    modifier = Modifier.size(20.dp)
                )
                Text("Debug", fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClearLog, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Log", modifier = Modifier.size(18.dp))
            }
        }

        // Log display
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(8.dp)
        ) {
            items(filteredLines) { entry ->
                Text(
                    text = entry.formatted(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when (entry.tag) {
                        LogTag.ERROR -> ErrorRed
                        LogTag.DISCORD -> Color(0xFF7289DA)
                        LogTag.TWITCH -> Color(0xFF9146FF)
                        LogTag.KINDROID -> AccentBlue
                        LogTag.ANNOUNCE -> Color(0xFFFFB74D)
                        LogTag.DEBUG -> TextSecondary
                        LogTag.INFO -> TextPrimary
                    },
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        // Direct message input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = directMessage,
                onValueChange = { directMessage = it },
                label = { Text("Direct Message to Kin") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (directMessage.isNotBlank()) {
                        onSendDirect(directMessage)
                        directMessage = ""
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
