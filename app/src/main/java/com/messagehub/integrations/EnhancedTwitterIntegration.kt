package com.messagehub.integrations

import android.content.Context
import android.util.Log
import com.messagehub.data.*
import com.messagehub.data.MessageRepository
import com.messagehub.network.TwitterApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

@Singleton
class TwitterIntegration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val twitterApiService: TwitterApiService,
    private val messageRepository: MessageRepository
) {
    
    companion object {
        private const val TAG = "TwitterIntegration"
    }
    
    fun generateCrcResponse(crcToken: String): String {
        return try {
            val consumerSecret = "YOUR_TWITTER_CONSUMER_SECRET"
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(consumerSecret.toByteArray(), "HmacSHA256")
            mac.init(secretKeySpec)
            val hash = mac.doFinal(crcToken.toByteArray())
            val encodedHash = Base64.encodeToString(hash, Base64.NO_WRAP)
            """{"response_token": "sha256=$encodedHash"}"""
        } catch (e: Exception) {
            Log.e(TAG, "Error generating CRC response", e)
            """{"error": "Failed to generate CRC response"}"""
        }
    }
    
    suspend fun handleWebhook(json: JSONObject) {
        try {
            when {
                json.has("direct_message_events") -> {
                    val events = json.getJSONArray("direct_message_events")
                    for (i in 0 until events.length()) {
                        val event = events.getJSONObject(i)
                        handleDirectMessageEvent(event)
                    }
                }
                json.has("direct_message_indicate_typing_events") -> {
                    handleTypingEvent(json.getJSONArray("direct_message_indicate_typing_events"))
                }
                json.has("direct_message_mark_read_events") -> {
                    handleReadEvent(json.getJSONArray("direct_message_mark_read_events"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Twitter webhook", e)
        }
    }
    
    private suspend fun handleDirectMessageEvent(event: JSONObject) {
        val type = event.getString("type")
        if (type == "MessageCreate") {
            val message = parseDirectMessage(event)
            messageRepository.addMessage(message)
            
            val deviceId = messageRepository.getDeviceId()
            if (deviceId.isNotEmpty()) {
                Log.d(TAG, "Received Twitter DM: ${message.content}")
            }
        }
    }
    
    private suspend fun handleTypingEvent(events: org.json.JSONArray) {
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            val senderId = event.getString("sender_id")
            val targetId = event.getString("target_id")
            Log.d(TAG, "User $senderId is typing to $targetId")
        }
    }
    
    private suspend fun handleReadEvent(events: org.json.JSONArray) {
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            val senderId = event.getString("sender_id")
            val targetId = event.getString("target_id")
            val lastReadEventId = event.getString("last_read_event_id")
            Log.d(TAG, "User $senderId read messages up to $lastReadEventId")
        }
    }
    
    private fun parseDirectMessage(event: JSONObject): EnhancedMessage {
        val messageCreate = event.getJSONObject("message_create")
        val target = messageCreate.getJSONObject("target")
        val messageData = messageCreate.getJSONObject("message_data")
        
        val eventId = event.getString("id")
        val senderId = messageCreate.getString("sender_id")
        val recipientId = target.getString("recipient_id")
        val createdTimestamp = event.getString("created_timestamp")
        
        val content = messageData.getString("text")
        
        val attachments = if (messageData.has("attachment")) {
            parseTwitterAttachment(messageData.getJSONObject("attachment"))
        } else emptyList()
        
        val messageType = if (attachments.isNotEmpty()) {
            when (attachments.first().type) {
                AttachmentType.IMAGE -> MessageType.IMAGE
                AttachmentType.VIDEO -> MessageType.VIDEO
                AttachmentType.AUDIO -> MessageType.AUDIO
                else -> MessageType.DOCUMENT
            }
        } else MessageType.TEXT
        
        return EnhancedMessage(
            id = eventId,
            platform = "twitter",
            sender = senderId,
            content = content,
            timestamp = createdTimestamp,
            recipientId = recipientId,
            messageType = messageType,
            attachments = attachments
        )
    }
    
    private fun parseTwitterAttachment(attachment: JSONObject): List<Attachment> {
        val attachments = mutableListOf<Attachment>()
        
        when (attachment.getString("type")) {
            "media" -> {
                val media = attachment.getJSONObject("media")
                val mediaType = media.getString("type")
                
                val attachmentType = when (mediaType) {
                    "photo" -> AttachmentType.IMAGE
                    "video" -> AttachmentType.VIDEO
                    "animated_gif" -> AttachmentType.VIDEO
                    else -> AttachmentType.DOCUMENT
                }
                
                attachments.add(
                    Attachment(
                        id = media.getString("id_str"),
                        type = attachmentType,
                        url = media.getString("media_url_https"),
                        dimensions = if (media.has("sizes")) {
                            val sizes = media.getJSONObject("sizes")
                            val large = sizes.getJSONObject("large")
                            Dimensions(large.getInt("w"), large.getInt("h"))
                        } else null
                    )
                )
            }
        }
        
        return attachments
    }
    
    suspend fun sendMessage(reply: MessageReply): Boolean {
        return try {
            when (reply.messageType) {
                MessageType.TEXT -> {
                    twitterApiService.sendDirectMessage(
                        recipientId = reply.recipientId,
                        text = reply.content
                    )
                }
                MessageType.IMAGE -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        // First upload media, then send message with media
                        val mediaId = twitterApiService.uploadMedia(attachment.url)
                        twitterApiService.sendDirectMessageWithMedia(
                            recipientId = reply.recipientId,
                            text = reply.content,
                            mediaId = mediaId
                        )
                    }
                }
                MessageType.VIDEO -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        val mediaId = twitterApiService.uploadMedia(attachment.url)
                        twitterApiService.sendDirectMessageWithMedia(
                            recipientId = reply.recipientId,
                            text = reply.content,
                            mediaId = mediaId
                        )
                    }
                }
                else -> {
                    twitterApiService.sendDirectMessage(
                        recipientId = reply.recipientId,
                        text = reply.content
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Twitter DM", e)
            false
        }
    }
    
    suspend fun addReaction(messageId: String, emoji: String): Boolean {
        return try {
            // Twitter doesn't support reactions on DMs
            Log.d(TAG, "Twitter DM reactions not supported")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add reaction", e)
            false
        }
    }
}
