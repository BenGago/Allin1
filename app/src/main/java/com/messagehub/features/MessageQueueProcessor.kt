package com.messagehub.features

import android.util.Log
import com.messagehub.cache.RedisManager
import com.messagehub.data.Message
import com.messagehub.integrations.MessengerIntegration
import com.messagehub.integrations.TelegramIntegration
import com.messagehub.integrations.TwitterIntegration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageQueueProcessor @Inject constructor(
    private val redisManager: RedisManager,
    private val telegramIntegration: TelegramIntegration,
    private val messengerIntegration: MessengerIntegration,
    private val twitterIntegration: TwitterIntegration
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _queueStats = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    val queueStats: StateFlow<Map<String, Map<String, Long>>> = _queueStats.asStateFlow()
    
    private val _processingStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val processingStatus: StateFlow<Map<String, Boolean>> = _processingStatus.asStateFlow()
    
    companion object {
        private const val TAG = "MessageQueueProcessor"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }
    
    init {
        startProcessing()
    }
    
    fun startProcessing() {
        scope.launch {
            val platforms = listOf("telegram", "messenger", "twitter", "sms")
            
            platforms.forEach { platform ->
                launch {
                    processQueueForPlatform(platform)
                }
            }
        }
    }
    
    private suspend fun processQueueForPlatform(platform: String) {
        while (true) {
            try {
                val messageData = redisManager.dequeueMessage(platform)
                if (messageData != null) {
                    processMessage(platform, messageData)
                } else {
                    delay(1000) // Wait 1 second if no messages
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing queue for $platform", e)
                delay(5000) // Wait 5 seconds on error
            }
        }
    }
    
    private suspend fun processMessage(platform: String, messageData: String) {
        try {
            val message = json.decodeFromString<Message>(messageData)
            Log.d(TAG, "Processing message for $platform: ${message.id}")
            
            val success = when (platform.lowercase()) {
                "telegram" -> processTelegramMessage(message)
                "messenger" -> processMessengerMessage(message)
                "twitter" -> processTwitterMessage(message)
                "sms" -> processSmsMessage(message)
                else -> {
                    Log.w(TAG, "Unknown platform: $platform")
                    false
                }
            }
            
            if (success) {
                redisManager.incrementMessageStats(platform, "sent")
                Log.d(TAG, "Message processed successfully: ${message.id}")
            } else {
                handleFailedMessage(platform, messageData, message)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process message", e)
            redisManager.incrementMessageStats(platform, "failed")
        }
    }
    
    private suspend fun processTelegramMessage(message: Message): Boolean {
        return try {
            val recipientId = message.recipientId ?: return false
            telegramIntegration.sendMessage(recipientId, message.content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Telegram message", e)
            false
        }
    }
    
    private suspend fun processMessengerMessage(message: Message): Boolean {
        return try {
            val recipientId = message.recipientId ?: return false
            messengerIntegration.sendMessage(recipientId, message.content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Messenger message", e)
            false
        }
    }
    
    private suspend fun processTwitterMessage(message: Message): Boolean {
        return try {
            val recipientId = message.recipientId ?: return false
            twitterIntegration.sendDirectMessage(recipientId, message.content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Twitter message", e)
            false
        }
    }
    
    private suspend fun processSmsMessage(message: Message): Boolean {
        return try {
            // SMS processing would be handled by MessageRepository
            Log.d(TAG, "SMS message queued for processing: ${message.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process SMS message", e)
            false
        }
    }
    
    private suspend fun handleFailedMessage(platform: String, messageData: String, message: Message) {
        try {
            val retryCount = message.metadata?.get("retryCount")?.toIntOrNull() ?: 0
            
            if (retryCount < MAX_RETRY_ATTEMPTS) {
                // Add retry metadata
                val updatedMessage = message.copy(
                    metadata = (message.metadata ?: mutableMapOf()).apply {
                        put("retryCount", (retryCount + 1).toString())
                        put("lastRetryAt", System.currentTimeMillis().toString())
                    }
                )
                
                val updatedMessageData = json.encodeToString(updatedMessage)
                
                // Wait before retry
                delay(RETRY_DELAY_MS * (retryCount + 1))
                
                // Re-queue the message
                redisManager.queueMessage(platform, updatedMessageData)
                Log.d(TAG, "Message re-queued for retry: ${message.id}, attempt: ${retryCount + 1}")
            } else {
                // Max retries reached, move to dead letter queue
                redisManager.queueMessage("${platform}_failed", messageData)
                redisManager.incrementMessageStats(platform, "failed")
                Log.w(TAG, "Message failed after max retries: ${message.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle failed message", e)
        }
    }
    
    suspend fun queueOutgoingMessage(message: Message) {
        try {
            val messageData = json.encodeToString(message)
            redisManager.queueMessage(message.platform, messageData)
            redisManager.incrementMessageStats(message.platform, "queued")
            Log.d(TAG, "Message queued: ${message.id} for ${message.platform}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue message", e)
        }
    }
    
    suspend fun getQueueStats(): Map<String, Map<String, Long>> {
        val platforms = listOf("telegram", "messenger", "twitter", "sms")
        val stats = mutableMapOf<String, Map<String, Long>>()
        
        platforms.forEach { platform ->
            stats[platform] = redisManager.getMessageStats(platform)
        }
        
        return stats
    }
    
    fun stopProcessing() {
        scope.coroutineContext.cancel()
    }
    
    private fun updateProcessingStatus(platform: String, isProcessing: Boolean) {
        val currentStatus = _processingStatus.value.toMutableMap()
        currentStatus[platform] = isProcessing
        _processingStatus.value = currentStatus
    }
}
