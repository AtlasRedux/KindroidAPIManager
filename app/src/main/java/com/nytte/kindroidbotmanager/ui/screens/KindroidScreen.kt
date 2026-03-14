package com.nytte.kindroidbotmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nytte.kindroidbotmanager.data.model.BotProfile

@Composable
fun KindroidScreen(
    profile: BotProfile,
    onProfileChange: (BotProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Kindroid API Settings", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = profile.profileName,
            onValueChange = { onProfileChange(profile.copy(profileName = it)) },
            label = { Text("Profile Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = profile.apiKey,
            onValueChange = { onProfileChange(profile.copy(apiKey = it)) },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = profile.aiId,
            onValueChange = { onProfileChange(profile.copy(aiId = it)) },
            label = { Text("AI ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = profile.baseUrl,
            onValueChange = { onProfileChange(profile.copy(baseUrl = it)) },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = profile.personaName,
            onValueChange = { onProfileChange(profile.copy(personaName = it)) },
            label = { Text("Persona Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
