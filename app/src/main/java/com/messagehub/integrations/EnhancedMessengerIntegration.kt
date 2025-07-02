package com.messagehub.integrations

import android.content.Context
import android.util.Log
import com.messagehub.data.*
import com.messagehub.data.MessageRepository
import com.messagehub.network.MessengerApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessengerIntegration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messengerApiService: MessengerApiService,
    private val messageRepository: MessageRepository
) {
    
    companion object {
        private const val TAG = "MessengerIntegration"
    }
    
    suspend fun handleWebhook(json: JSONObject) {
        try {
            if (json.getString("object") == "page") {
                val entries = json.getJSONArray("entry")
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    if (entry.has("messaging")) {
                        val messaging = entry.getJSONArray("messaging")
                        for (j in 0 until messaging.length()) {
                            val messagingEvent = messaging.getJSONObject(j)
                            handleMessagingEvent(messagingEvent)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Messenger webhook", e)
        }
    }
    
    private suspend fun handleMessagingEvent(event: JSONObject) {
        when {
            event.has("message") -> handleMessage(event)
            event.has("postback") -> handlePostback(event)
            event.has("reaction") -> handleReaction(event)
            event.has("read") -> handleRead(event)
            event.has("delivery") -> handleDelivery(event)
        }
    }
    
    private suspend fun handleMessage(event: JSONObject) {
        val message = parseMessage(event)
        messageRepository.addMessage(message)
        
        val deviceId = messageRepository.getDeviceId()
        if (deviceId.isNotEmpty()) {
            Log.d(TAG, "Received Messenger message: ${message.content}")
        }
    }
    
    private suspend fun handlePostback(event: JSONObject) {
        val postback = event.getJSONObject("postback")
        val payload = postback.getString("payload")
        val title = postback.optString("title")
        
        Log.d(TAG, "Postback received: $title ($payload)")
    }
    
    private suspend fun handleReaction(event: JSONObject) {
        val reaction = event.getJSONObject("reaction")
        val messageId = reaction.getString("mid")
        val emoji = reaction.optString("emoji")
        val action = reaction.getString("action") // "react" or "unreact"
        
        val sender = event.getJSONObject("sender")
        val senderId = sender.getString("id")
        
        if (action == "react" && emoji.isNotEmpty()) {
            val reactionObj = Reaction(
                emoji = emoji,
                userId = senderId,
                userName = "User", // You'd fetch this from user profile
                timestamp = System.currentTimeMillis().toString()
            )
            messageRepository.addReaction(messageId, reactionObj)
        } else if (action == "unreact") {
            messageRepository.removeReaction(messageId, senderId)
        }
    }
    
    private suspend fun handleRead(event: JSONObject) {
        val read = event.getJSONObject("read")
        val watermark = read.getLong("watermark")
        Log.d(TAG, "Message read up to: $watermark")
    }
    
    private suspend fun handleDelivery(event: JSONObject) {
        val delivery = event.getJSONObject("delivery")
        val watermark = delivery.getLong("watermark")
        Log.d(TAG, "Message delivered up to: $watermark")
    }
    
    private fun parseMessage(event: JSONObject): EnhancedMessage {
        val message = event.getJSONObject("message")
        val sender = event.getJSONObject("sender")
        val recipient = event.getJSONObject("recipient")
        val timestamp = event.getLong("timestamp")
        
        val messageId = message.getString("mid")
        val senderId = sender.getString("id")
        val recipientId = recipient.getString("id")
        
        val content = when {
            message.has("text") -> message.getString("text")
            message.has("attachments") -> {
                val attachments = message.getJSONArray("attachments")
                val attachment = attachments.getJSONObject(0)
                val type = attachment.getString("type")
                "[$type attachment]"
            }
            else -> "[Unknown message type]"
        }
        
        val messageType = when {
            message.has("text") -> MessageType.TEXT
            message.has("attachments") -> {
                val attachments = message.getJSONArray("attachments")
                val attachment = attachments.getJSONObject(0)
                when (attachment.getString("type")) {
                    "image" -> MessageType.IMAGE
                    "video" -> MessageType.VIDEO
                    "audio" -> MessageType.AUDIO
                    "file" -> MessageType.DOCUMENT
                    else -> MessageType.TEXT
                }
            }
            else -> MessageType.TEXT
        }
        
        val attachments = if (message.has("attachments")) {
            parseMessengerAttachments(message.getJSONArray("attachments"))
        } else emptyList()
        
        return EnhancedMessage(
            id = messageId,
            platform = "messenger",
            sender = senderId,
            content = content,
            timestamp = timestamp.toString(),
            recipientId = recipientId,
            messageType = messageType,
            attachments = attachments
        )
    }
    
    private fun parseMessengerAttachments(attachmentsJson: org.json.JSONArray): List<Attachment> {
        val attachments = mutableListOf<Attachment>()
        
        for (i in 0 until attachmentsJson.length()) {
            val attachment = attachmentsJson.getJSONObject(i)
            val type = attachment.getString("type")
            val payload = attachment.getJSONObject("payload")
            
            val attachmentType = when (type) {
                "image" -> AttachmentType.IMAGE
                "video" -> AttachmentType.VIDEO
                "audio" -> AttachmentType.AUDIO
                "file" -> AttachmentType.DOCUMENT
                else -> AttachmentType.DOCUMENT
            }
            
            attachments.add(
                Attachment(
                    id = payload.optString("attachment_id", ""),
                    type = attachmentType,
                    url = payload.getString("url"),
                    fileName = payload.optString("name"),
                    mimeType = payload.optString("mime_type")
                )
            )
        }
        
        return attachments
    }
    
    suspend fun sendMessage(reply: MessageReply): Boolean {
        return try {
            when (reply.messageType) {
                MessageType.TEXT -> {
                    messengerApiService.sendTextMessage(
                        recipientId = reply.recipientId,
                        text = reply.content
                    )
                }
                MessageType.IMAGE -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        messengerApiService.sendImageMessage(
                            recipientId = reply.recipientId,
                            imageUrl = attachment.url
                        )
                    }
                }
                MessageType.VIDEO -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        messengerApiService.sendVideoMessage(
                            recipientId = reply.recipientId,
                            videoUrl = attachment.url
                        )
                    }
                }
                MessageType.AUDIO -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        messengerApiService.sendAudioMessage(
                            recipientId = reply.recipientId,
                            audioUrl = attachment.url
                        )
                    }
                }
                MessageType.DOCUMENT -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        messengerApiService.sendFileMessage(
                            recipientId = reply.recipientId,
                            fileUrl = attachment.url
                        )
                    }
                }
                else -> {
                    messengerApiService.sendTextMessage(
                        recipientId = reply.recipientId,
                        text = reply.content
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Messenger message", e)
            false
        }
    }
    
    suspend fun addReaction(messageId: String, emoji: String): Boolean {
        return try {
            messengerApiService.sendReaction(messageId, emoji)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add reaction", e)
            false
        }
    }
}
