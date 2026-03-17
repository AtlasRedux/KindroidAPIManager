package com.nytte.kindroidbotmanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nytte.kindroidbotmanager.ui.components.BotControlBar
import com.nytte.kindroidbotmanager.ui.components.ProfileSelector
import com.nytte.kindroidbotmanager.ui.screens.AnnounceScreen
import com.nytte.kindroidbotmanager.ui.screens.ConsoleScreen
import com.nytte.kindroidbotmanager.ui.screens.DiscordScreen
import com.nytte.kindroidbotmanager.ui.screens.KindroidScreen
import com.nytte.kindroidbotmanager.ui.screens.TwitchScreen
import com.nytte.kindroidbotmanager.ui.theme.KindroidBotTheme
import com.nytte.kindroidbotmanager.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — service works either way, just no notification on deny */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            KindroidBotTheme {
                // Defer ViewModel creation by one frame so the activity is fully
                // settled (permission dialog, edge-to-edge, etc.) before the
                // ViewModel init block tries to bind the foreground service.
                var ready by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { ready = true }

                if (ready) {
                    MainApp()
                }
            }
        }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    KINDROID("Kindroid", Icons.Default.Api),
    DISCORD("Discord", Icons.Default.SmartToy),
    TWITCH("Twitch", Icons.AutoMirrored.Filled.Chat),
    ANNOUNCE("Announce", Icons.Default.Notifications),
    CONSOLE("Console", Icons.Default.Terminal)
}

@Composable
private fun MainApp(viewModel: MainViewModel = viewModel()) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val selectedName by viewModel.selectedProfileName.collectAsStateWithLifecycle()
    val botRunning by viewModel.botRunning.collectAsStateWithLifecycle()
    val discordConnected by viewModel.discordConnected.collectAsStateWithLifecycle()
    val twitchConnected by viewModel.twitchConnected.collectAsStateWithLifecycle()
    val logLines by viewModel.logLines.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = Tab.entries

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Profile selector
            ProfileSelector(
                profiles = profiles.map { it.profileName },
                selectedName = selectedName,
                onSelect = viewModel::selectProfile,
                onNew = viewModel::newProfile,
                onSave = viewModel::saveProfile,
                onDelete = viewModel::deleteProfile
            )

            // Bot control bar
            BotControlBar(
                isRunning = botRunning,
                discordConnected = discordConnected,
                twitchConnected = twitchConnected,
                onStart = viewModel::startBot,
                onStop = viewModel::stopBot
            )

            HorizontalDivider()

            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.title) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) }
                    )
                }
            }

            // Tab content
            when (tabs[selectedTab]) {
                Tab.KINDROID -> KindroidScreen(
                    profile = selectedProfile,
                    onProfileChange = viewModel::updateCurrentProfile
                )
                Tab.DISCORD -> DiscordScreen(
                    profile = selectedProfile,
                    onProfileChange = viewModel::updateCurrentProfile
                )
                Tab.TWITCH -> TwitchScreen(
                    profile = selectedProfile,
                    onProfileChange = viewModel::updateCurrentProfile
                )
                Tab.ANNOUNCE -> AnnounceScreen(
                    profile = selectedProfile,
                    isRunning = botRunning,
                    onProfileChange = viewModel::updateCurrentProfile,
                    onSendNow = viewModel::sendAnnouncementNow
                )
                Tab.CONSOLE -> ConsoleScreen(
                    logLines = logLines,
                    debugMode = debugMode,
                    onToggleDebug = viewModel::toggleDebug,
                    onClearLog = viewModel::clearLog,
                    onSendDirect = viewModel::sendDirectMessage
                )
            }
        }
    }
}
