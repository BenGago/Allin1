package com.messagehub.features

import android.content.Context
import android.util.Log
import androidx.work.*
import com.messagehub.data.MessageReply
import com.messagehub.data.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetryQueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository
) {
    
    companion object {
        private const val TAG = "RetryQueueManager"
        private const val RETRY_WORK_TAG = "message_retry"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)
    
    private val _queuedMessages = MutableStateFlow<List<QueuedMessage>>(emptyList())
    val queuedMessages: StateFlow<List<QueuedMessage>> = _queuedMessages.asStateFlow()
    
    fun queueMessage(reply: MessageReply, retryCount: Int = 0) {
        scope.launch {
            val queuedMessage = QueuedMessage(
                id = generateId(),
                reply = reply,
                timestamp = System.currentTimeMillis(),
                retryCount = retryCount,
                maxRetries = 3,
                status = QueueStatus.PENDING
            )
            
            // Add to local queue
            val currentQueue = _queuedMessages.value.toMutableList()
            currentQueue.add(queuedMessage)
            _queuedMessages.value = currentQueue
            
            // Schedule retry work
            scheduleRetryWork(queuedMessage)
        }
    }
    
    private fun scheduleRetryWork(queuedMessage: QueuedMessage) {
        val delay = calculateRetryDelay(queuedMessage.retryCount)
        
        val workRequest = OneTimeWorkRequestBuilder<MessageRetryWorker>()
            .setInputData(
                Data.Builder()
                    .putString("queued_message", Json.encodeToString(queuedMessage))
                    .build()
            )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(RETRY_WORK_TAG)
            .build()
        
        workManager.enqueue(workRequest)
    }
    
    private fun calculateRetryDelay(retryCount: Int): Long {
        // Exponential backoff: 1s, 2s, 4s, 8s, etc.
        return (1000L * Math.pow(2.0, retryCount.toDouble())).toLong()
    }
    
    fun markMessageSent(messageId: String) {
        scope.launch {
            val currentQueue = _queuedMessages.value.toMutableList()
            val index = currentQueue.indexOfFirst { it.id == messageId }
            if (index != -1) {
                currentQueue[index] = currentQueue[index].copy(status = QueueStatus.SENT)
                _queuedMessages.value = currentQueue
            }
        }
    }
    
    fun markMessageFailed(messageId: String) {
        scope.launch {
            val currentQueue = _queuedMessages.value.toMutableList()
            val index = currentQueue.indexOfFirst { it.id == messageId }
            if (index != -1) {
                val message = currentQueue[index]
                if (message.retryCount < message.maxRetries) {
                    // Retry
                    val updatedMessage = message.copy(
                        retryCount = message.retryCount + 1,
                        status = QueueStatus.RETRYING
                    )
                    currentQueue[index] = updatedMessage
                    scheduleRetryWork(updatedMessage)
                } else {
                    // Max retries reached
                    currentQueue[index] = message.copy(status = QueueStatus.FAILED)
                }
                _queuedMessages.value = currentQueue
            }
        }
    }
    
    fun clearQueue() {
        _queuedMessages.value = emptyList()
        workManager.cancelAllWorkByTag(RETRY_WORK_TAG)
    }
    
    private fun generateId(): String {
        return "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}

data class QueuedMessage(
    val id: String,
    val reply: MessageReply,
    val timestamp: Long,
    val retryCount: Int,
    val maxRetries: Int,
    val status: QueueStatus
)

enum class QueueStatus {
    PENDING,
    SENDING,
    SENT,
    RETRYING,
    FAILED
}

class MessageRetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val queuedMessageJson = inputData.getString("queued_message")
            val queuedMessage = Json.decodeFromString<QueuedMessage>(queuedMessageJson!!)
            
            // Attempt to send the message
            // This would integrate with your existing message sending logic
            
            Result.success()
        } catch (e: Exception) {
            Log.e("MessageRetryWorker", "Retry failed", e)
            Result.retry()
        }
    }
}
