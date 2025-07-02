package com.messagehub.features

import android.content.Context
import android.util.Log
import com.messagehub.data.EnhancedMessage
import com.messagehub.network.OpenAIService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictiveMessaging @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAIService: OpenAIService
) {
    
    companion object {
        private const val TAG = "PredictiveMessaging"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _predictions = MutableStateFlow<List<String>>(emptyList())
    val predictions: StateFlow<List<String>> = _predictions.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val conversationHistory = mutableListOf<EnhancedMessage>()
    
    fun addToConversationHistory(message: EnhancedMessage) {
        conversationHistory.add(message)
        if (conversationHistory.size > 20) {
            conversationHistory.removeAt(0) // Keep recent messages only
        }
    }
    
    fun predictNextMessage(
        currentInput: String,
        recipientName: String,
        platform: String,
        conversationContext: List<EnhancedMessage> = emptyList()
    ) {
        if (currentInput.length < 3) {
            _predictions.value = emptyList()
            return
        }
        
        scope.launch {
            try {
                _isLoading.value = true
                
                val predictions = generatePredictions(
                    currentInput,
                    recipientName,
                    platform,
                    conversationContext
                )
                
                _predictions.value = predictions
                
            } catch (e: Exception) {
                Log.e(TAG, "Prediction failed", e)
                _predictions.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun generatePredictions(
        currentInput: String,
        recipientName: String,
        platform: String,
        context: List<EnhancedMessage>
    ): List<String> {
        val contextMessages = context.takeLast(5)
        val conversationContext = contextMessages.joinToString("\n") { 
            "${it.sender}: ${it.content}" 
        }
        
        val prompt = buildPredictionPrompt(
            currentInput,
            recipientName,
            platform,
            conversationContext
        )
        
        val response = openAIService.generateResponse(prompt)
        val completions = response.choices?.firstOrNull()?.message?.content
        
        return parseCompletions(completions, currentInput)
    }
    
    private fun buildPredictionPrompt(
        currentInput: String,
        recipientName: String,
        platform: String,
        context: String
    ): String {
        return """
            You are a predictive text assistant. Complete the user's message naturally.
            
            Conversation context:
            $context
            
            Current platform: $platform
            Recipient: $recipientName
            User is typing: "$currentInput"
            
            Generate 3 different completions for this message:
            1. A short completion (1-5 words)
            2. A medium completion (5-15 words)
            3. A longer completion (15+ words)
            
            Make completions natural, contextually appropriate, and match the conversation tone.
            For platform-specific style:
            - SMS: Casual, brief
            - Telegram: Can be longer, emoji-friendly
            - Messenger: Conversational, reaction-friendly
            - Twitter: Concise, hashtag-aware
            
            Format as:
            1. [completion 1]
            2. [completion 2]
            3. [completion 3]
        """.trimIndent()
    }
    
    private fun parseCompletions(response: String?, currentInput: String): List<String> {
        if (response == null) return emptyList()
        
        val completions = mutableListOf<String>()
        
        // Parse numbered completions
        val lines = response.split("\n")
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^[123]\\..+"))) {
                val completion = trimmed.substring(2).trim()
                if (completion.isNotEmpty()) {
                    // Combine with current input
                    val fullMessage = currentInput + completion
                    completions.add(fullMessage)
                }
            }
        }
        
        // Fallback: simple word completions
        if (completions.isEmpty()) {
            completions.addAll(getSimplePredictions(currentInput))
        }
        
        return completions.take(3)
    }
    
    private fun getSimplePredictions(currentInput: String): List<String> {
        val lastWord = currentInput.split(" ").lastOrNull()?.lowercase() ?: ""
        
        val commonCompletions = mapOf(
            "how" to listOf("how are you?", "how's it going?", "how was your day?"),
            "what" to listOf("what's up?", "what are you doing?", "what do you think?"),
            "i'm" to listOf("i'm good", "i'm busy", "i'm thinking of you"),
            "can" to listOf("can you help me?", "can we talk?", "can you call me?"),
            "let's" to listOf("let's meet up", "let's talk later", "let's do this"),
            "see" to listOf("see you later", "see you soon", "see what I mean?"),
            "talk" to listOf("talk to you later", "talk soon", "talk to me"),
            "miss" to listOf("miss you", "miss you too", "miss talking to you")
        )
        
        return commonCompletions[lastWord] ?: listOf(
            currentInput + " you",
            currentInput + " later",
            currentInput + " soon"
        )
    }
    
    fun clearPredictions() {
        _predictions.value = emptyList()
    }
}
