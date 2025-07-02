package com.messagehub.features

import android.util.Log
import com.messagehub.network.MessengerApiService
import com.messagehub.network.TelegramApiService
import com.messagehub.network.TwitterApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TypingIndicatorManager @Inject constructor(
    private val telegramApiService: TelegramApiService,
    private val messengerApiService: MessengerApiService,
    private val twitterApiService: TwitterApiService
) {
    
    companion object {
        private const val TAG = "TypingIndicatorManager"
        private const val TYPING_DURATION = 5000L // 5 seconds
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun showTyping(platform: String, recipientId: String) {
        scope.launch {
            try {
                when (platform.lowercase()) {
                    "telegram" -> {
                        telegramApiService.sendChatAction(recipientId, "typing")
                        // Telegram typing indicator lasts 5 seconds
                    }
                    "messenger" -> {
                        messengerApiService.sendTypingIndicator(recipientId, "typing_on")
                        delay(TYPING_DURATION)
                        messengerApiService.sendTypingIndicator(recipientId, "typing_off")
                    }
                    "twitter" -> {
                        // Twitter doesn't have native typing indicators
                        // We'll mock it in the UI
                        Log.d(TAG, "Simulating typing for Twitter DM to $recipientId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show typing indicator for $platform", e)
            }
        }
    }
    
    fun hideTyping(platform: String, recipientId: String) {
        scope.launch {
            try {
                when (platform.lowercase()) {
                    "messenger" -> {
                        messengerApiService.sendTypingIndicator(recipientId, "typing_off")
                    }
                    // Telegram and Twitter typing indicators auto-expire
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide typing indicator for $platform", e)
            }
        }
    }
}
