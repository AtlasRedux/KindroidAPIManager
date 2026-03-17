package com.nytte.kindroidbotmanager.data.repository

import android.content.Context
import com.nytte.kindroidbotmanager.data.model.BotProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class ProfileRepository(context: Context) {

    private val file = File(context.filesDir, "profiles.json")
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun loadAll(): List<BotProfile> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withContext emptyList()
            val text = file.readText()
            if (text.isBlank()) return@withContext emptyList()
            try {
                json.decodeFromString<List<BotProfile>>(text)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveAll(profiles: List<BotProfile>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(BotProfile.serializer()), profiles))
        }
    }

    suspend fun addOrUpdate(profile: BotProfile) {
        val profiles = loadAll().toMutableList()
        val index = profiles.indexOfFirst { it.profileName == profile.profileName }
        if (index >= 0) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }
        saveAll(profiles)
    }

    suspend fun delete(profileName: String) {
        val profiles = loadAll().toMutableList()
        profiles.removeAll { it.profileName == profileName }
        saveAll(profiles)
    }
}
