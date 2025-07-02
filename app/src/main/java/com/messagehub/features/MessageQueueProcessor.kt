package com.messagehub.features

import android.util.Log
import com.messagehub.cache.RedisManager
import com.messagehub.data.Message
import com.messagehub.data.MessageRepository
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class QueueStats(
    val userId: String,
    val platform: String,
    val totalProcessed: Long = 0,
    val totalFailed: Long = 0,
    val currentQueueSize: Long = 0,
    val processingRate: Double = 0.0,
    val lastProcessedTime: Long = 0
)

@Serializable
data class ProcessingStatus(
    val userId: String,
    val platform: String,
    val isProcessing: Boolean,
    val lastActivity: Long = System.currentTimeMillis()
)

@Singleton
class MessageQueueProcessor @Inject constructor(
    private val redisManager: RedisManager,
    private val messageRepository: MessageRepository,
    private val telegramIntegration: TelegramIntegration,
    private val messengerIntegration: MessengerIntegration,
    private val twitterIntegration: TwitterIntegration
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _queueStats = MutableStateFlow<Map<String, Map<String, QueueStats>>>(emptyMap())
    val queueStats: StateFlow<Map<String, Map<String, QueueStats>>> = _queueStats.asStateFlow()
    
    private val _processingStatus = MutableStateFlow<Map<String, Map<String, Boolean>>>(emptyMap())
    val processingStatus: StateFlow<Map<String, Map<String, Boolean>>> = _processingStatus.asStateFlow()
    
    private val processingJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    
    companion object {
        private const val TAG = "MessageQueueProcessor"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val PROCESSING_DELAY_MS = 500L
        private val SUPPORTED_PLATFORMS = listOf("telegram", "messenger", "twitter", "sms")
    }
    
    fun startProcessingForUser(userId: String) {
        if (processingJobs.containsKey(userId)) {
            Log.d(TAG, "Processing already started for user: $userId")
            return
        }
        
        val job = scope.launch {
            try {
                SUPPORTED_PLATFORMS.forEach { platform ->
                    launch {
                        processQueueForUserAndPlatform(userId, platform)
                    }
                }
                
                // Start stats collection for this user
                launch {
                    collectStatsForUser(userId)
                }
                
                Log.d(TAG, "Message queue processing started for user: $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting processing for user: $userId", e)
            }
        }
        
        processingJobs[userId] = job
    }
    
    fun stopProcessingForUser(userId: String) {
        processingJobs[userId]?.cancel()
        processingJobs.remove(userId)
        
        // Clear processing status for this user
        val currentStatus = _processingStatus.value.toMutableMap()
        currentStatus.remove(userId)
        _processingStatus.value = currentStatus
        
        Log.d(TAG, "Message queue processing stopped for user: $userId")
    }
    
    private suspend fun processQueueForUserAndPlatform(userId: String, platform: String) {
        Log.d(TAG, "Starting queue processor for user: $userId, platform: $platform")
        
        while (scope.isActive && processingJobs.containsKey(userId)) {
            try {
                updateProcessingStatus(userId, platform, true)
                
                val message = redisManager.dequeueMessage(userId, platform)
                if (message != null) {
                    processMessage(message)
                    delay(PROCESSING_DELAY_MS) // Small delay between messages
                } else {
                    // No messages in queue, wait a bit longer
                    delay(2000)
                }
                
                updateProcessingStatus(userId, platform, false)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing queue for user: $userId, platform: $platform", e)
                updateProcessingStatus(userId, platform, false)
                delay(5000) // Wait longer on error
            }
        }
    }
    
    private suspend fun processMessage(message: Message) {
        try {
            Log.d(TAG, "Processing message: ${message.id} for user: ${message.userId}, platform: ${message.platform}")
            
            val success = when (message.platform.lowercase()) {
                "telegram" -> processTelegramMessage(message)
                "messenger" -> processMessengerMessage(message)
                "twitter" -> processTwitterMessage(message)
                "sms" -> processSmsMessage(message)
                else -> {
                    Log.w(TAG, "Unknown platform: ${message.platform}")
                    false
                }
            }
            
            if (success) {
                // Save to local database
                messageRepository.insertMessage(message)
                
                // Cache the message
                redisManager.cacheMessage(message)
                
                // Update statistics
                redisManager.incrementMessageStats(message.userId, message.platform, "sent")
                
                Log.d(TAG, "Message processed successfully: ${message.id}")
            } else {
                handleProcessingFailure(message)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: ${message.id}", e)
            handleProcessingFailure(message)
        }
    }
    
    private suspend fun handleProcessingFailure(message: Message) {
        try {
            val retryCount = message.metadata?.get("retryCount")?.toIntOrNull() ?: 0
            
            if (retryCount < MAX_RETRY_ATTEMPTS) {
                // Add retry metadata
                val updatedMetadata = (message.metadata ?: mutableMapOf()).apply {
                    put("retryCount", (retryCount + 1).toString())
                    put("lastRetryAt", System.currentTimeMillis().toString())
                }
                
                val updatedMessage = message.copy(metadata = updatedMetadata)
                
                // Wait before retry
                delay(RETRY_DELAY_MS * (retryCount + 1))
                
                // Re-queue the message
                redisManager.queueMessage(updatedMessage)
                Log.d(TAG, "Message re-queued for retry: ${message.id}, attempt: ${retryCount + 1}")
            } else {
                // Max retries reached, mark as failed
                redisManager.incrementMessageStats(message.userId, message.platform, "failed")
                
                // Save failed message to database for manual review
                val failedMessage = message.copy(
                    content = "[FAILED] ${message.content}",
                    metadata = (message.metadata ?: mutableMapOf()).apply {
                        put("status", "failed")
                        put("failedAt", System.currentTimeMillis().toString())
                    }
                )
                messageRepository.insertMessage(failedMessage)
                
                Log.w(TAG, "Message failed after max retries: ${message.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle processing failure", e)
        }
    }
    
    private suspend fun processTelegramMessage(message: Message): Boolean {
        return try {
            val credentials = messageRepository.getPlatformCredentials(message.userId, "telegram")
            if (credentials?.isEnabled == true && credentials.accessToken != null) {
                val recipientId = message.recipientId ?: return false
                telegramIntegration.sendMessage(recipientId, message.content)
            } else {
                Log.w(TAG, "Telegram credentials not configured for user: ${message.userId}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Telegram message", e)
            false
        }
    }
    
    private suspend fun processMessengerMessage(message: Message): Boolean {
        return try {
            val credentials = messageRepository.getPlatformCredentials(message.userId, "messenger")
            if (credentials?.isEnabled == true && credentials.accessToken != null) {
                val recipientId = message.recipientId ?: return false
                messengerIntegration.sendMessage(recipientId, message.content)
            } else {
                Log.w(TAG, "Messenger credentials not configured for user: ${message.userId}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Messenger message", e)
            false
        }
    }
    
    private suspend fun processTwitterMessage(message: Message): Boolean {
        return try {
            val credentials = messageRepository.getPlatformCredentials(message.userId, "twitter")
            if (credentials?.isEnabled == true && credentials.accessToken != null) {
                val recipientId = message.recipientId ?: return false
                twitterIntegration.sendDirectMessage(recipientId, message.content)
            } else {
                Log.w(TAG, "Twitter credentials not configured for user: ${message.userId}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Twitter message", e)
            false
        }
    }
    
    private suspend fun processSmsMessage(message: Message): Boolean {
        return try {
            val recipientId = message.recipientId ?: return false
            messageRepository.sendSms(recipientId, message.content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS message", e)
            false
        }
    }
    
    suspend fun queueOutgoingMessage(message: Message): Boolean {
        return try {
            val success = redisManager.queueMessage(message)
            if (success) {
                redisManager.incrementMessageStats(message.userId, message.platform, "queued")
                Log.d(TAG, "Message queued: ${message.id} for user: ${message.userId}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue message", e)
            false
        }
    }
    
    private suspend fun collectStatsForUser(userId: String) {
        while (scope.isActive && processingJobs.containsKey(userId)) {
            try {
                val userStats = mutableMapOf<String, QueueStats>()
                
                SUPPORTED_PLATFORMS.forEach { platform ->
                    val stats = redisManager.getMessageStats(userId, platform)
                    val queueSize = redisManager.getQueueSize(userId, platform)
                    
                    userStats[platform] = QueueStats(
                        userId = userId,
                        platform = platform,
                        totalProcessed = stats["sent"] ?: 0,
                        totalFailed = stats["failed"] ?: 0,
                        currentQueueSize = queueSize,
                        processingRate = calculateProcessingRate(stats),
                        lastProcessedTime = System.currentTimeMillis()
                    )
                }
                
                val currentStats = _queueStats.value.toMutableMap()
                currentStats[userId] = userStats
                _queueStats.value = currentStats
                
                delay(10000) // Update stats every 10 seconds
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting stats for user: $userId", e)
                delay(15000)
            }
        }
    }
    
    private fun calculateProcessingRate(stats: Map<String, Long>): Double {
        val sent = stats["sent"] ?: 0
        val failed = stats["failed"] ?: 0
        val total = sent + failed
        
        return if (total > 0) {
            (sent.toDouble() / total.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    private fun updateProcessingStatus(userId: String, platform: String, isProcessing: Boolean) {
        val currentStatus = _processingStatus.value.toMutableMap()
        val userStatus = currentStatus.getOrPut(userId) { mutableMapOf() }.toMutableMap()
        userStatus[platform] = isProcessing
        currentStatus[userId] = userStatus
        _processingStatus.value = currentStatus
    }
    
    suspend fun getQueueStatsForUser(userId: String): Map<String, QueueStats> {
        return _queueStats.value[userId] ?: emptyMap()
    }
    
    suspend fun getProcessingStatusForUser(userId: String): Map<String, Boolean> {
        return _processingStatus.value[userId] ?: emptyMap()
    }
    
    fun stopAllProcessing() {
        processingJobs.values.forEach { it.cancel() }
        processingJobs.clear()
        scope.coroutineContext.cancel()
        Log.d(TAG, "All message queue processing stopped")
    }
}
