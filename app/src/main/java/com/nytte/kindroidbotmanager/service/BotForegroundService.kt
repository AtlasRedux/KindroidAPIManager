package com.nytte.kindroidbotmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import com.nytte.kindroidbotmanager.MainActivity
import com.nytte.kindroidbotmanager.R
import com.nytte.kindroidbotmanager.data.model.BotProfile
import com.nytte.kindroidbotmanager.network.DiscordGateway
import com.nytte.kindroidbotmanager.network.KindroidApiClient
import com.nytte.kindroidbotmanager.network.TwitchIrcClient
import com.nytte.kindroidbotmanager.util.LogEntry
import com.nytte.kindroidbotmanager.util.LogTag
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class BotForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "kindroid_bot_service"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.nytte.kindroidbotmanager.STOP"
    }

    inner class BotBinder : Binder() {
        fun getService(): BotForegroundService = this@BotForegroundService
    }

    private val binder = BotBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _logFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 200)
    val logFlow: SharedFlow<LogEntry> = _logFlow

    private var kindroidApi: KindroidApiClient? = null
    private var discordGateway: DiscordGateway? = null
    private var twitchIrc: TwitchIrcClient? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var announceJob: Job? = null
    private var _running = false
    private var _inForeground = false
    val isRunning: Boolean get() = _running
    val isDiscordConnected: Boolean get() = discordGateway?.isConnected == true
    val isTwitchConnected: Boolean get() = twitchIrc?.isConnected == true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBots()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    fun startBots(profile: BotProfile) {
        if (_running) {
            emitLog(LogTag.INFO, "Bots already running, stop first")
            return
        }

        if (profile.apiKey.isBlank() || profile.aiId.isBlank()) {
            emitLog(LogTag.ERROR, "API Key and AI ID are required")
            return
        }

        _running = true
        acquireWakeLocks()

        // Start foreground
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Bot running: ${profile.profileName}"))
            _inForeground = true
        } catch (e: Exception) {
            emitLog(LogTag.ERROR, "Failed to start foreground: ${e.message}")
        }

        // Create Kindroid API client
        kindroidApi = KindroidApiClient(
            apiKey = profile.apiKey,
            aiId = profile.aiId,
            baseUrl = profile.baseUrl.ifBlank { "https://api.kindroid.ai/v1" },
            log = { emitLog(it) }
        )

        emitLog(LogTag.INFO, "Starting bot with profile: ${profile.profileName}")

        // Start Discord if enabled
        if (profile.discordEnabled && profile.discordToken.isNotBlank()) {
            discordGateway = DiscordGateway(
                token = profile.discordToken,
                kindroidApi = kindroidApi!!,
                log = { emitLog(it) },
                scope = serviceScope
            )
            discordGateway!!.start()
        } else {
            emitLog(LogTag.INFO, "Discord bot disabled or no token")
        }

        // Start Twitch if enabled
        if (profile.twitchEnabled && profile.twitchOAuth.isNotBlank() && profile.twitchChannel.isNotBlank()) {
            twitchIrc = TwitchIrcClient(
                username = profile.twitchUsername,
                oauthToken = profile.twitchOAuth,
                channel = profile.twitchChannel,
                kindroidApi = kindroidApi!!,
                log = { emitLog(it) },
                scope = serviceScope
            )
            twitchIrc!!.start()
        } else {
            emitLog(LogTag.INFO, "Twitch bot disabled or missing credentials")
        }

        // Start announcement timer if configured
        startAnnouncementTimer(profile)

        emitLog(LogTag.INFO, "Bot service started")
    }

    fun stopBots() {
        if (!_running) return
        _running = false

        emitLog(LogTag.INFO, "Stopping bots...")

        announceJob?.cancel()
        announceJob = null

        // Gracefully stop clients — don't call shutdown() on OkHttp dispatchers
        // as that kills executors while callbacks are still in-flight
        try { discordGateway?.stop() } catch (_: Exception) {}
        discordGateway = null

        try { twitchIrc?.stop() } catch (_: Exception) {}
        twitchIrc = null

        kindroidApi = null

        releaseWakeLocks()

        // Remove foreground notification only if we actually promoted to foreground
        if (_inForeground) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            _inForeground = false
        }

        // Mark the started service as done (won't destroy while ViewModel binding exists)
        stopSelf()

        emitLog(LogTag.INFO, "Bots stopped")
    }

    fun sendDirectMessage(text: String) {
        val api = kindroidApi
        if (api == null) {
            emitLog(LogTag.ERROR, "Bot not running — cannot send direct message")
            return
        }
        serviceScope.launch {
            api.sendDirectMessage(text)
        }
    }

    fun sendAnnouncementNow(profile: BotProfile) {
        if (!_running) {
            emitLog(LogTag.ERROR, "Bot not running — cannot send announcement")
            return
        }
        if (profile.announceMessage.isBlank()) {
            emitLog(LogTag.ERROR, "Announcement message is empty")
            return
        }
        if (!profile.announceDiscord && !profile.announceTwitch) {
            emitLog(LogTag.ERROR, "No announcement platform enabled")
            return
        }
        serviceScope.launch {
            dispatchAnnouncement(profile)
        }
    }

    private fun startAnnouncementTimer(profile: BotProfile) {
        announceJob?.cancel()
        announceJob = null

        if (profile.announceMessage.isBlank()) return
        if (!profile.announceDiscord && !profile.announceTwitch) return

        val totalMins = profile.announceHours * 60 + profile.announceMins
        if (totalMins <= 0) return

        val intervalMs = totalMins * 60L * 1000L
        emitLog(LogTag.ANNOUNCE, "Announcement timer started: every ${profile.announceHours}h ${profile.announceMins}m")

        announceJob = serviceScope.launch {
            while (isActive) {
                delay(intervalMs)
                dispatchAnnouncement(profile)
            }
        }
    }

    private suspend fun dispatchAnnouncement(profile: BotProfile) {
        emitLog(LogTag.ANNOUNCE, "Sending announcement...")
        if (profile.announceDiscord) {
            val dg = discordGateway
            if (dg != null) {
                dg.sendAnnouncement(
                    profile.announceMessage,
                    profile.announceDiscordChannel.ifBlank { null }
                )
                emitLog(LogTag.ANNOUNCE, "Sent to Discord")
            } else {
                emitLog(LogTag.ERROR, "Discord not connected — announcement skipped")
            }
        }
        if (profile.announceTwitch) {
            val tc = twitchIrc
            if (tc != null) {
                tc.sendAnnouncement(profile.announceMessage)
                emitLog(LogTag.ANNOUNCE, "Sent to Twitch")
            } else {
                emitLog(LogTag.ERROR, "Twitch not connected — announcement skipped")
            }
        }
    }

    private fun emitLog(entry: LogEntry) {
        _logFlow.tryEmit(entry)
    }

    private fun emitLog(tag: LogTag, message: String) {
        _logFlow.tryEmit(LogEntry(tag, message))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BotForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Kindroid Bot")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(Notification.Action.Builder(
                null, "Stop", stopIntent
            ).build())
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KindroidBot::BotWakeLock")
            wakeLock?.acquire()
        } catch (_: Exception) {}

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "KindroidBot::WifiLock")
            wifiLock?.acquire()
        } catch (_: Exception) {}
    }

    private fun releaseWakeLocks() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
        try { wifiLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wifiLock = null
    }

    override fun onDestroy() {
        stopBots()
        serviceScope.cancel()
        super.onDestroy()
    }
}
