package com.nytte.kindroidbotmanager.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nytte.kindroidbotmanager.data.model.BotProfile
import com.nytte.kindroidbotmanager.data.repository.ProfileRepository
import com.nytte.kindroidbotmanager.service.BotForegroundService
import com.nytte.kindroidbotmanager.util.LogEntry
import com.nytte.kindroidbotmanager.util.LogTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProfileRepository(application)

    // Profile state
    private val _profiles = MutableStateFlow<List<BotProfile>>(emptyList())
    val profiles: StateFlow<List<BotProfile>> = _profiles.asStateFlow()

    private val _selectedProfile = MutableStateFlow(BotProfile())
    val selectedProfile: StateFlow<BotProfile> = _selectedProfile.asStateFlow()

    private val _selectedProfileName = MutableStateFlow("")
    val selectedProfileName: StateFlow<String> = _selectedProfileName.asStateFlow()

    // Bot state
    private val _botRunning = MutableStateFlow(false)
    val botRunning: StateFlow<Boolean> = _botRunning.asStateFlow()

    private val _discordConnected = MutableStateFlow(false)
    val discordConnected: StateFlow<Boolean> = _discordConnected.asStateFlow()

    private val _twitchConnected = MutableStateFlow(false)
    val twitchConnected: StateFlow<Boolean> = _twitchConnected.asStateFlow()

    // Console log
    private val _logLines = MutableStateFlow<List<LogEntry>>(emptyList())
    val logLines: StateFlow<List<LogEntry>> = _logLines.asStateFlow()

    private val _debugMode = MutableStateFlow(false)
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()

    // Service binding
    private var service: BotForegroundService? = null
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as BotForegroundService.BotBinder).getService()
            service = svc
            bound = true
            // Collect logs from service
            viewModelScope.launch {
                svc.logFlow.collect { entry ->
                    val current = _logLines.value.toMutableList()
                    current.add(entry)
                    // Cap at 500 entries
                    if (current.size > 500) current.removeAt(0)
                    _logLines.value = current
                }
            }
            // Periodic status poll
            viewModelScope.launch {
                while (true) {
                    _botRunning.value = svc.isRunning
                    _discordConnected.value = svc.isDiscordConnected
                    _twitchConnected.value = svc.isTwitchConnected
                    kotlinx.coroutines.delay(2000)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            _botRunning.value = false
        }
    }

    init {
        // Bind to service (defensive — don't crash the app if this fails)
        try {
            val context = getApplication<Application>()
            val intent = Intent(context, BotForegroundService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Failed to bind service", e)
        }

        // Load profiles
        viewModelScope.launch {
            try {
                _profiles.value = repository.loadAll()
                if (_profiles.value.isNotEmpty()) {
                    selectProfile(_profiles.value.first().profileName)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to load profiles", e)
            }
        }
    }

    fun selectProfile(name: String) {
        val profile = _profiles.value.find { it.profileName == name } ?: return
        _selectedProfile.value = profile
        _selectedProfileName.value = name
    }

    fun updateCurrentProfile(profile: BotProfile) {
        _selectedProfile.value = profile
    }

    fun saveProfile() {
        val profile = _selectedProfile.value
        if (profile.profileName.isBlank()) return
        viewModelScope.launch {
            repository.addOrUpdate(profile)
            _profiles.value = repository.loadAll()
            _selectedProfileName.value = profile.profileName
        }
    }

    fun deleteProfile() {
        val name = _selectedProfile.value.profileName
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.delete(name)
            _profiles.value = repository.loadAll()
            if (_profiles.value.isNotEmpty()) {
                selectProfile(_profiles.value.first().profileName)
            } else {
                _selectedProfile.value = BotProfile()
                _selectedProfileName.value = ""
            }
        }
    }

    fun newProfile() {
        _selectedProfile.value = BotProfile()
        _selectedProfileName.value = ""
    }

    fun startBot() {
        val profile = _selectedProfile.value
        if (profile.apiKey.isBlank() || profile.aiId.isBlank()) {
            addLog(LogTag.ERROR, "API Key and AI ID are required to start")
            return
        }

        // Auto-save if named
        if (profile.profileName.isNotBlank()) {
            saveProfile()
        }

        val context = getApplication<Application>()
        val intent = Intent(context, BotForegroundService::class.java)
        context.startForegroundService(intent)

        service?.startBots(profile) ?: addLog(LogTag.ERROR, "Service not bound")
    }

    fun stopBot() {
        service?.stopBots()
        _botRunning.value = false
        _discordConnected.value = false
        _twitchConnected.value = false
    }

    fun sendDirectMessage(text: String) {
        if (text.isBlank()) return
        addLog(LogTag.INFO, "Direct: $text")
        service?.sendDirectMessage(text) ?: addLog(LogTag.ERROR, "Service not bound")
    }

    fun toggleDebug() {
        _debugMode.value = !_debugMode.value
    }

    fun clearLog() {
        _logLines.value = emptyList()
    }

    private fun addLog(tag: LogTag, message: String) {
        val entry = LogEntry(tag, message)
        val current = _logLines.value.toMutableList()
        current.add(entry)
        if (current.size > 500) current.removeAt(0)
        _logLines.value = current
    }

    override fun onCleared() {
        if (bound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {}
        }
        super.onCleared()
    }
}
