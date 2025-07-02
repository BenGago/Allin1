package com.messagehub.integrations

import android.content.Context
import android.util.Log
import com.messagehub.data.Message
import com.messagehub.data.MessageRepository
import com.messagehub.network.TwitterApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitterIntegration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val twitterApiService: TwitterApiService,
    private val messageRepository: MessageRepository
) {
    
    companion object {
        private const val TAG = "TwitterIntegration"
    }
    
    fun startPolling() {
        CoroutineScope(Dispatchers.IO).launch {
            pollForDirectMessages()
        }
    }
    
    private suspend fun pollForDirectMessages() {
        while (true) {
            try {
                checkForNewDirectMessages()
                kotlinx.coroutines.delay(30000) // Check every 30 seconds (Twitter rate limits)
            } catch (e: Exception) {
                Log.e(TAG, "Error polling Twitter DMs", e)
                kotlinx.coroutines.delay(60000) // Wait 1 minute on error
            }
        }
    }
    
    private suspend fun checkForNewDirectMessages() {
        try {
            val deviceId = messageRepository.getDeviceId()
            if (deviceId.isNotEmpty()) {
                val events = twitterApiService.getDirectMessageEvents()
                events.events?.forEach { event ->
                    if (event.type == "MessageCreate") {
                        handleIncomingDirectMessage(event)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Twitter DMs", e)
        }
    }
    
    private suspend fun handleIncomingDirectMessage(event: TwitterDirectMessageEvent) {
        val messageCreate = event.messageCreate
        if (messageCreate != null) {
            val message = Message(
                id = event.id ?: System.currentTimeMillis().toString(),
                platform = "twitter",
                sender = messageCreate.senderId ?: "Unknown",
                content = messageCreate.messageData?.text ?: "",
                timestamp = event.createdTimestamp ?: System.currentTimeMillis().toString(),
                recipientId = messageCreate.senderId
            )
            
            try {
                val deviceId = messageRepository.getDeviceId()
                if (deviceId.isNotEmpty()) {
                    Log.d(TAG, "Received Twitter DM: ${message.content}")
                    // Send to backend
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process Twitter DM", e)
            }
        }
    }
    
    suspend fun sendDirectMessage(recipientId: String, text: String): Boolean {
        return try {
            val request = TwitterDirectMessageRequest(
                event = TwitterDirectMessageEventRequest(
                    type = "MessageCreate",
                    messageCreate = TwitterMessageCreateRequest(
                        target = TwitterTargetRequest(recipientId),
                        messageData = TwitterMessageDataRequest(text)
                    )
                )
            )
            
            val response = twitterApiService.sendDirectMessage(request)
            response.event != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Twitter DM", e)
            false
        }
    }
}

// Twitter API Data Classes
data class TwitterDirectMessageEventsResponse(
    val events: List<TwitterDirectMessageEvent>?
)

data class TwitterDirectMessageEvent(
    val id: String?,
    val createdTimestamp: String?,
    val type: String?,
    val messageCreate: TwitterMessageCreate?
)

data class TwitterMessageCreate(
    val target: TwitterTarget?,
    val senderId: String?,
    val messageData: TwitterMessageData?
)

data class TwitterTarget(
    val recipientId: String?
)

data class TwitterMessageData(
    val text: String?
)

data class TwitterDirectMessageRequest(
    val event: TwitterDirectMessageEventRequest
)

data class TwitterDirectMessageEventRequest(
    val type: String,
    val messageCreate: TwitterMessageCreateRequest
)

data class TwitterMessageCreateRequest(
    val target: TwitterTargetRequest,
    val messageData: TwitterMessageDataRequest
)

data class TwitterTargetRequest(
    val recipientId: String
)

data class TwitterMessageDataRequest(
    val text: String
)

data class TwitterDirectMessageResponse(
    val event: TwitterDirectMessageEvent?
)
