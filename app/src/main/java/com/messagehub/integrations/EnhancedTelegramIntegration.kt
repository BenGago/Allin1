package com.messagehub.integrations

import android.content.Context
import android.util.Log
import com.messagehub.data.*
import com.messagehub.data.MessageRepository
import com.messagehub.network.TelegramApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramIntegration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramApiService: TelegramApiService,
    private val messageRepository: MessageRepository
) {
    
    companion object {
        private const val TAG = "TelegramIntegration"
    }
    
    suspend fun handleWebhook(json: JSONObject) {
        try {
            if (json.has("message")) {
                handleMessage(json.getJSONObject("message"))
            } else if (json.has("callback_query")) {
                handleCallbackQuery(json.getJSONObject("callback_query"))
            } else if (json.has("edited_message")) {
                handleEditedMessage(json.getJSONObject("edited_message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Telegram webhook", e)
        }
    }
    
    private suspend fun handleMessage(messageJson: JSONObject) {
        val message = parseMessage(messageJson)
        messageRepository.addMessage(message)
        
        // Send to backend
        val deviceId = messageRepository.getDeviceId()
        if (deviceId.isNotEmpty()) {
            // Send to your backend API
            Log.d(TAG, "Received Telegram message: ${message.content}")
        }
    }
    
    private suspend fun handleCallbackQuery(callbackJson: JSONObject) {
        // Handle inline keyboard button presses
        val data = callbackJson.optString("data")
        val messageId = callbackJson.getJSONObject("message").getString("message_id")
        
        Log.d(TAG, "Callback query: $data for message $messageId")
        
        // Answer callback query
        telegramApiService.answerCallbackQuery(
            callbackJson.getString("id"),
            "Button pressed!"
        )
    }
    
    private suspend fun handleEditedMessage(messageJson: JSONObject) {
        val message = parseMessage(messageJson, isEdited = true)
        messageRepository.updateMessage(message)
    }
    
    private fun parseMessage(messageJson: JSONObject, isEdited: Boolean = false): EnhancedMessage {
        val messageId = messageJson.getString("message_id")
        val from = messageJson.getJSONObject("from")
        val chat = messageJson.getJSONObject("chat")
        
        val sender = from.optString("username").takeIf { it.isNotEmpty() }
            ?: from.optString("first_name", "Unknown")
        
        val content = when {
            messageJson.has("text") -> messageJson.getString("text")
            messageJson.has("caption") -> messageJson.getString("caption")
            messageJson.has("sticker") -> "[Sticker: ${messageJson.getJSONObject("sticker").optString("emoji", "🙂")}]"
            messageJson.has("photo") -> "[Photo]"
            messageJson.has("video") -> "[Video]"
            messageJson.has("audio") -> "[Audio]"
            messageJson.has("voice") -> "[Voice Message]"
            messageJson.has("document") -> "[Document]"
            messageJson.has("location") -> "[Location]"
            messageJson.has("contact") -> "[Contact]"
            else -> "[Unsupported Message Type]"
        }
        
        val messageType = when {
            messageJson.has("text") -> MessageType.TEXT
            messageJson.has("photo") -> MessageType.IMAGE
            messageJson.has("video") -> MessageType.VIDEO
            messageJson.has("audio") -> MessageType.AUDIO
            messageJson.has("voice") -> MessageType.VOICE_NOTE
            messageJson.has("document") -> MessageType.DOCUMENT
            messageJson.has("sticker") -> MessageType.STICKER
            messageJson.has("location") -> MessageType.LOCATION
            messageJson.has("contact") -> MessageType.CONTACT
            else -> MessageType.TEXT
        }
        
        val attachments = parseAttachments(messageJson)
        
        return EnhancedMessage(
            id = messageId,
            platform = "telegram",
            sender = sender,
            content = content,
            timestamp = messageJson.getLong("date").toString(),
            recipientId = chat.getString("id"),
            messageType = messageType,
            attachments = attachments,
            isEdited = isEdited,
            replyTo = if (messageJson.has("reply_to_message")) {
                messageJson.getJSONObject("reply_to_message").getString("message_id")
            } else null
        )
    }
    
    private fun parseAttachments(messageJson: JSONObject): List<Attachment> {
        val attachments = mutableListOf<Attachment>()
        
        when {
            messageJson.has("photo") -> {
                val photos = messageJson.getJSONArray("photo")
                val largestPhoto = (0 until photos.length())
                    .map { photos.getJSONObject(it) }
                    .maxByOrNull { it.getInt("file_size") }
                
                largestPhoto?.let { photo ->
                    attachments.add(
                        Attachment(
                            id = photo.getString("file_id"),
                            type = AttachmentType.IMAGE,
                            url = "", // Will be filled by file download
                            fileSize = photo.optLong("file_size"),
                            dimensions = Dimensions(
                                photo.getInt("width"),
                                photo.getInt("height")
                            )
                        )
                    )
                }
            }
            messageJson.has("video") -> {
                val video = messageJson.getJSONObject("video")
                attachments.add(
                    Attachment(
                        id = video.getString("file_id"),
                        type = AttachmentType.VIDEO,
                        url = "",
                        fileSize = video.optLong("file_size"),
                        duration = video.optInt("duration"),
                        dimensions = Dimensions(
                            video.getInt("width"),
                            video.getInt("height")
                        )
                    )
                )
            }
            messageJson.has("audio") -> {
                val audio = messageJson.getJSONObject("audio")
                attachments.add(
                    Attachment(
                        id = audio.getString("file_id"),
                        type = AttachmentType.AUDIO,
                        url = "",
                        fileName = audio.optString("file_name"),
                        fileSize = audio.optLong("file_size"),
                        duration = audio.optInt("duration")
                    )
                )
            }
            messageJson.has("voice") -> {
                val voice = messageJson.getJSONObject("voice")
                attachments.add(
                    Attachment(
                        id = voice.getString("file_id"),
                        type = AttachmentType.VOICE_NOTE,
                        url = "",
                        fileSize = voice.optLong("file_size"),
                        duration = voice.optInt("duration")
                    )
                )
            }
            messageJson.has("document") -> {
                val document = messageJson.getJSONObject("document")
                attachments.add(
                    Attachment(
                        id = document.getString("file_id"),
                        type = AttachmentType.DOCUMENT,
                        url = "",
                        fileName = document.optString("file_name"),
                        fileSize = document.optLong("file_size"),
                        mimeType = document.optString("mime_type")
                    )
                )
            }
            messageJson.has("sticker") -> {
                val sticker = messageJson.getJSONObject("sticker")
                attachments.add(
                    Attachment(
                        id = sticker.getString("file_id"),
                        type = AttachmentType.STICKER,
                        url = "",
                        dimensions = Dimensions(
                            sticker.getInt("width"),
                            sticker.getInt("height")
                        )
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
                    telegramApiService.sendMessage(
                        chatId = reply.recipientId,
                        text = reply.content,
                        replyToMessageId = reply.replyTo?.toIntOrNull()
                    )
                }
                MessageType.IMAGE -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        telegramApiService.sendPhoto(
                            chatId = reply.recipientId,
                            photo = attachment.url,
                            caption = reply.content.takeIf { it.isNotEmpty() }
                        )
                    }
                }
                MessageType.VIDEO -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        telegramApiService.sendVideo(
                            chatId = reply.recipientId,
                            video = attachment.url,
                            caption = reply.content.takeIf { it.isNotEmpty() }
                        )
                    }
                }
                MessageType.AUDIO -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        telegramApiService.sendAudio(
                            chatId = reply.recipientId,
                            audio = attachment.url,
                            caption = reply.content.takeIf { it.isNotEmpty() }
                        )
                    }
                }
                MessageType.DOCUMENT -> {
                    reply.attachments.firstOrNull()?.let { attachment ->
                        telegramApiService.sendDocument(
                            chatId = reply.recipientId,
                            document = attachment.url,
                            caption = reply.content.takeIf { it.isNotEmpty() }
                        )
                    }
                }
                else -> {
                    telegramApiService.sendMessage(
                        chatId = reply.recipientId,
                        text = reply.content
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Telegram message", e)
            false
        }
    }
    
    suspend fun addReaction(messageId: String, emoji: String): Boolean {
        return try {
            // Telegram doesn't have native reactions, but we can send a message
            // or use inline keyboards for reaction-like functionality
            Log.d(TAG, "Telegram reactions not natively supported")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add reaction", e)
            false
        }
    }
}
