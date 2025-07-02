package com.messagehub.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.messagehub.integrations.MessengerIntegration
import com.messagehub.integrations.TelegramIntegration
import com.messagehub.integrations.TwitterIntegration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlatformIntegrationService : Service() {
    
    @Inject
    lateinit var telegramIntegration: TelegramIntegration
    
    @Inject
    lateinit var messengerIntegration: MessengerIntegration
    
    @Inject
    lateinit var twitterIntegration: TwitterIntegration
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "PlatformIntegrationService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Platform Integration Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPlatformIntegrations()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext.cancel()
    }
    
    private fun startPlatformIntegrations() {
        serviceScope.launch {
            try {
                // Start Telegram polling
                telegramIntegration.startPolling()
                Log.d(TAG, "Telegram integration started")
                
                // Start Messenger webhook listener
                messengerIntegration.startWebhookListener()
                Log.d(TAG, "Messenger integration started")
                
                // Start Twitter DM polling
                twitterIntegration.startPolling()
                Log.d(TAG, "Twitter integration started")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting platform integrations", e)
            }
        }
    }
}
