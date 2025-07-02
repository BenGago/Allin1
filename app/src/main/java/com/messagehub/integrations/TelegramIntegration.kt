package com.messagehub.integrations

import android.content.Context
import android.util.Log
import com.messagehub.data.Message
import com.messagehub.data.MessageRepository
import com.messagehub.network.TelegramApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    
    fun startPolling() {
        CoroutineScope(Dispatchers.IO).launch {
            pollForUpdates()
        }
    }
    
    private suspend fun pollForUpdates() {
        var offset = 0
        while (true) {
            try {
                val updates = telegramApiService.getUpdates(offset)
                updates.result?.forEach { update ->
                    update.message?.let { message ->
                        handleIncomingMessage(message)
                        offset = update.updateId + 1
                    }
                }
                kotlinx.coroutines.delay(1000) // Poll every second
            } catch (e: Exception) {
                Log.e(TAG, "Error polling Telegram updates", e)
                kotlinx.coroutines.delay(5000) // Wait 5 seconds on error
            }
        }
    }
    
    private suspend fun handleIncomingMessage(telegramMessage: TelegramMessage) {
        val message = Message(
            id = telegramMessage.messageId.toString(),
            platform = "telegram",
            sender = telegramMessage.from?.username ?: telegramMessage.from?.firstName ?: "Unknown",
            content = telegramMessage.text ?: "",
            timestamp = System.currentTimeMillis().toString(),
            recipientId = telegramMessage.chat.id.toString()
        )
        
        // Send to backend
        try {
            val deviceId = messageRepository.getDeviceId()
            if (deviceId.isNotEmpty()) {
                // You would send this to your backend here
                Log.d(TAG, "Received Telegram message: ${message.content}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process Telegram message", e)
        }
    }
    
    suspend fun sendMessage(chatId: String, text: String): Boolean {
        return try {
            val response = telegramApiService.sendMessage(chatId, text)
            response.ok
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Telegram message", e)
            false
        }
    }
}

// Telegram API Data Classes
data class TelegramUpdate(
    val updateId: Int,
    val message: TelegramMessage?
)

data class TelegramMessage(
    val messageId: Int,
    val from: TelegramUser?,
    val chat: TelegramChat,
    val text: String?
)

data class TelegramUser(
    val id: Long,
    val isBot: Boolean,
    val firstName: String,
    val lastName: String?,
    val username: String?
)

data class TelegramChat(
    val id: Long,
    val type: String,
    val title: String?,
    val username: String?,
    val firstName: String?,
    val lastName: String?
)

data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T?
)
