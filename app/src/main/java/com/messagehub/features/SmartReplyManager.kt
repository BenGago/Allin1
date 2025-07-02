package com.messagehub.features

import android.content.Context
import android.util.Log
import com.messagehub.data.EnhancedMessage
import com.messagehub.network.OpenAIService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartReplyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAIService: OpenAIService
) {
    
    companion object {
        private const val TAG = "SmartReplyManager"
    }
    
    suspend fun generateSmartReplies(message: EnhancedMessage): List<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildPrompt(message)
            val response = openAIService.generateReplies(prompt)
            
            response.choices?.firstOrNull()?.message?.content
                ?.split("\n")
                ?.filter { it.isNotBlank() }
                ?.take(3)
                ?: getDefaultReplies(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate smart replies", e)
            getDefaultReplies(message)
        }
    }
    
    private fun buildPrompt(message: EnhancedMessage): String {
        return """
            Generate 3 short, appropriate reply suggestions for this message:
            
            Platform: ${message.platform}
            Sender: ${message.sender}
            Message: "${message.content}"
            
            Reply suggestions should be:
            - Brief (under 10 words each)
            - Contextually appropriate
            - Professional but friendly
            - One per line
            
            Examples:
            Thanks!
            Got it 👍
            Will check and get back to you
        """.trimIndent()
    }
    
    private fun getDefaultReplies(message: EnhancedMessage): List<String> {
        return when {
            message.content.contains("?", ignoreCase = true) -> listOf(
                "Let me check 🤔",
                "Yes, absolutely!",
                "I'll get back to you"
            )
            message.content.contains("thank", ignoreCase = true) -> listOf(
                "You're welcome! 😊",
                "Happy to help!",
                "Anytime!"
            )
            message.content.contains("meeting", ignoreCase = true) -> listOf(
                "What time works?",
                "I'll be there",
                "Let me check my calendar"
            )
            else -> listOf(
                "Thanks!",
                "Got it 👍",
                "Sounds good!"
            )
        }
    }
}

data class OpenAIResponse(
    val choices: List<OpenAIChoice>?
)

data class OpenAIChoice(
    val message: OpenAIMessage?
)

data class OpenAIMessage(
    val content: String?
)
