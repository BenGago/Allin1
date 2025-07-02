package com.messagehub.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.messagehub.R
import com.messagehub.data.MessageRepository
import com.messagehub.network.ApiService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class MessageSyncService : Service() {
    
    @Inject
    lateinit var apiService: ApiService
    
    @Inject
    lateinit var messageRepository: MessageRepository
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    
    companion object {
        private const val CHANNEL_ID = "MessageSyncChannel"
        private const val NOTIFICATION_ID = 1
        private const val SYNC_INTERVAL = 30_000L // 30 seconds
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startSyncLoop()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        serviceScope.cancel()
    }
    
    private fun startSyncLoop() {
        syncJob = serviceScope.launch {
            while (isActive) {
                try {
                    syncMessages()
                    checkOutgoingSms()
                } catch (e: Exception) {
                    Log.e("MessageSyncService", "Sync error", e)
                }
                delay(SYNC_INTERVAL)
            }
        }
    }
    
    private suspend fun syncMessages() {
        val deviceId = messageRepository.getDeviceId()
        if (deviceId.isEmpty()) return
        
        try {
            val messages = apiService.getMessages(deviceId)
            messageRepository.saveMessages(messages)
        } catch (e: Exception) {
            Log.e("MessageSyncService", "Failed to sync messages", e)
        }
    }
    
    private suspend fun checkOutgoingSms() {
        val deviceId = messageRepository.getDeviceId()
        if (deviceId.isEmpty()) return
        
        try {
            val outgoingSms = apiService.getOutgoingSms(deviceId)
            outgoingSms.forEach { sms ->
                messageRepository.sendSms(sms.recipient, sms.message)
                // Mark as sent in backend
                apiService.markSmsAsSent(sms.id)
            }
        } catch (e: Exception) {
            Log.e("MessageSyncService", "Failed to check outgoing SMS", e)
        }
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Message Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps messages synchronized"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Message Hub")
            .setContentText("Syncing messages...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }
}
