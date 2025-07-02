package com.messagehub.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.messagehub.features.MessageQueueProcessor
import com.messagehub.features.RealTimeManager
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
class EnhancedPlatformIntegrationService : Service() {
    
    @Inject
    lateinit var telegramIntegration: TelegramIntegration
    
    @Inject
    lateinit var messengerIntegration: MessengerIntegration
    
    @Inject
    lateinit var twitterIntegration: TwitterIntegration
    
    @Inject
    lateinit var messageQueueProcessor: MessageQueueProcessor
    
    @Inject
    lateinit var realTimeManager: RealTimeManager
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "EnhancedPlatformIntegrationService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Enhanced Platform Integration Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startEnhancedIntegrations()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopEnhancedIntegrations()
    }
    
    private fun startEnhancedIntegrations() {
        serviceScope.launch {
            try {
                // Start platform integrations
                telegramIntegration.startPolling()
                Log.d(TAG, "Telegram integration started")
                
                messengerIntegration.startWebhookListener()
                Log.d(TAG, "Messenger integration started")
                
                twitterIntegration.startPolling()
                Log.d(TAG, "Twitter integration started")
                
                // Start message queue processing
                messageQueueProcessor.startProcessing()
                Log.d(TAG, "Message queue processor started")
                
                // Start real-time features
                realTimeManager.startRealTimeUpdates()
                Log.d(TAG, "Real-time manager started")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting enhanced integrations", e)
            }
        }
    }
    
    private fun stopEnhancedIntegrations() {
        try {
            messageQueueProcessor.stopProcessing()
            realTimeManager.stopRealTimeUpdates()
            serviceScope.coroutineContext.cancel()
            Log.d(TAG, "Enhanced integrations stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping enhanced integrations", e)
        }
    }
}
