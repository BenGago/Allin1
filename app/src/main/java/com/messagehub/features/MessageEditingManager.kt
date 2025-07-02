package com.messagehub.features

import android.util.Log
import com.messagehub.data.EnhancedMessage
import com.messagehub.data.MessageRepository
import com.messagehub.network.TelegramApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageEditingManager @Inject constructor(
    private val telegramApiService: TelegramApiService,
    private val messageRepository: MessageRepository
) {
    
    companion object {
        private const val TAG = "MessageEditingManager"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun editMessage(messageId: String, newContent: String, platform: String, chatId: String) {
        scope.launch {
            try {
                when (platform.lowercase()) {
                    "telegram" -> {
                        telegramApiService.editMessageText(
                            chatId = chatId,
                            messageId = messageId.toInt(),
                            text = newContent
                        )
                        
                        // Update local message
                        messageRepository.markMessageAsEdited(messageId, newContent)
                    }
                    "messenger", "twitter" -> {
                        // These platforms don't support editing, so we log it
                        messageRepository.addEditLog(messageId, newContent, platform)
                        Log.d(TAG, "$platform doesn't support message editing, logged locally")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to edit message on $platform", e)
            }
        }
    }
    
    fun deleteMessage(messageId: String, platform: String, chatId: String) {
        scope.launch {
            try {
                when (platform.lowercase()) {
                    "telegram" -> {
                        telegramApiService.deleteMessage(chatId, messageId.toInt())
                        messageRepository.markMessageAsDeleted(messageId)
                    }
                    "messenger", "twitter" -> {
                        // Mark as deleted locally only
                        messageRepository.markMessageAsDeleted(messageId)
                        Log.d(TAG, "$platform doesn't support message deletion, marked locally")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete message on $platform", e)
            }
        }
    }
}
