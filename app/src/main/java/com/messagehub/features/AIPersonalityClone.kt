package com.messagehub.features

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
class AIPersonalityClone @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAIService: OpenAIService
) {
    
    companion object {
        private const val TAG = "AIPersonalityClone"
        private const val PREFS_NAME = "personality_clone"
        private const val KEY_TRAINING_DATA = "training_data"
        private const val KEY_PERSONALITY_PROFILE = "personality_profile"
        private const val KEY_CLONE_ENABLED = "clone_enabled"
        private const val MAX_TRAINING_MESSAGES = 100
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _personalityProfile = MutableStateFlow(getPersonalityProfile())
    val personalityProfile: StateFlow<PersonalityProfile> = _personalityProfile.asStateFlow()
    
    private val _cloneEnabled = MutableStateFlow(getCloneEnabled())
    val cloneEnabled: StateFlow<Boolean> = _cloneEnabled.asStateFlow()
    
    private val _trainingProgress = MutableStateFlow(0f)
    val trainingProgress: StateFlow<Float> = _trainingProgress.asStateFlow()
    
    fun addTrainingMessage(message: EnhancedMessage) {
        if (message.sender == "You") { // Only train on user's messages
            val trainingData = getTrainingData().toMutableList()
            trainingData.add(message.content)
            
            // Keep only recent messages
            if (trainingData.size > MAX_TRAINING_MESSAGES) {
                trainingData.removeAt(0)
            }
            
            saveTrainingData(trainingData)
            
            // Auto-analyze personality every 10 messages
            if (trainingData.size % 10 == 0) {
                analyzePersonality(trainingData)
            }
        }
    }
    
    private fun analyzePersonality(messages: List<String>) {
        scope.launch {
            try {
                _trainingProgress.value = 0.5f
                
                val personalityPrompt = buildPersonalityAnalysisPrompt(messages)
                val response = openAIService.generateResponse(personalityPrompt)
                
                val analysis = response.choices?.firstOrNull()?.message?.content
                if (analysis != null) {
                    val profile = parsePersonalityAnalysis(analysis)
                    savePersonalityProfile(profile)
                    _personalityProfile.value = profile
                }
                
                _trainingProgress.value = 1f
                Log.d(TAG, "Personality analysis completed")
                
            } catch (e: Exception) {
                Log.e(TAG, "Personality analysis failed", e)
                _trainingProgress.value = 0f
            }
        }
    }
    
    private fun buildPersonalityAnalysisPrompt(messages: List<String>): String {
        val messagesSample = messages.takeLast(20).joinToString("\n") { "- \"$it\"" }
        
        return """
            Analyze the personality and texting style from these messages:
            
            $messagesSample
            
            Provide analysis in this JSON format:
            {
                "communication_style": "casual/formal/playful/sarcastic",
                "emoji_usage": "high/medium/low",
                "humor_type": "sarcastic/playful/dry/sweet",
                "affection_level": "high/medium/low",
                "energy_level": "high/medium/low",
                "common_phrases": ["phrase1", "phrase2", "phrase3"],
                "personality_traits": ["trait1", "trait2", "trait3"],
                "response_length": "short/medium/long",
                "cultural_context": "filipino/western/mixed"
            }
            
            Focus on Filipino texting patterns like "charot", "grabe", "kilig", etc.
        """.trimIndent()
    }
    
    private fun parsePersonalityAnalysis(analysis: String): PersonalityProfile {
        return try {
            // Extract JSON from response
            val jsonStart = analysis.indexOf("{")
            val jsonEnd = analysis.lastIndexOf("}") + 1
            val jsonString = analysis.substring(jsonStart, jsonEnd)
            
            gson.fromJson(jsonString, PersonalityProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse personality analysis", e)
            PersonalityProfile() // Return default profile
        }
    }
    
    fun generateClonedResponse(originalMessage: EnhancedMessage, context: String = ""): String {
        val profile = _personalityProfile.value
        
        val clonePrompt = """
            You are mimicking someone's texting style. Here's their personality profile:
            
            Communication Style: ${profile.communicationStyle}
            Emoji Usage: ${profile.emojiUsage}
            Humor Type: ${profile.humorType}
            Affection Level: ${profile.affectionLevel}
            Energy Level: ${profile.energyLevel}
            Common Phrases: ${profile.commonPhrases.joinToString(", ")}
            Personality Traits: ${profile.personalityTraits.joinToString(", ")}
            Response Length: ${profile.responseLength}
            Cultural Context: ${profile.culturalContext}
            
            Original message to respond to: "${originalMessage.content}"
            From: ${originalMessage.sender}
            Platform: ${originalMessage.platform}
            
            Additional context: $context
            
            Generate a response that perfectly matches this person's texting style. 
            Use their typical phrases, emoji patterns, and personality traits.
            Keep it natural and authentic to their voice.
            
            Response:
        """.trimIndent()
        
        return try {
            // This would be called asynchronously in practice
            "Generating personalized response..." // Placeholder
        } catch (e: Exception) {
            Log.e(TAG, "Clone response generation failed", e)
            "Hey! 😊" // Fallback
        }
    }
    
    suspend fun generateClonedResponseAsync(originalMessage: EnhancedMessage, context: String = ""): String {
        return try {
            val profile = _personalityProfile.value
            val clonePrompt = buildClonePrompt(originalMessage, profile, context)
            
            val response = openAIService.generateResponse(clonePrompt)
            response.choices?.firstOrNull()?.message?.content?.trim() 
                ?: getPersonalityBasedFallback(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Clone response generation failed", e)
            getPersonalityBasedFallback(_personalityProfile.value)
        }
    }
    
    private fun buildClonePrompt(message: EnhancedMessage, profile: PersonalityProfile, context: String): String {
        val styleInstructions = when (profile.culturalContext) {
            "filipino" -> "Use Filipino texting slang like 'charot', 'grabe', 'kilig', 'sana all', etc."
            "western" -> "Use casual Western texting style with appropriate slang."
            else -> "Mix Filipino and Western texting styles naturally."
        }
        
        return """
            Mimic this texting personality:
            
            Style: ${profile.communicationStyle} | Energy: ${profile.energyLevel}
            Humor: ${profile.humorType} | Emojis: ${profile.emojiUsage}
            Length: ${profile.responseLength} | Affection: ${profile.affectionLevel}
            
            Signature phrases: ${profile.commonPhrases.take(3).joinToString(", ")}
            Traits: ${profile.personalityTraits.take(3).joinToString(", ")}
            
            $styleInstructions
            
            Respond to: "${message.content}"
            From: ${message.sender} (${message.platform})
            
            $context
            
            Reply in their exact style:
        """.trimIndent()
    }
    
    private fun getPersonalityBasedFallback(profile: PersonalityProfile): String {
        return when (profile.energyLevel) {
            "high" -> if (profile.culturalContext == "filipino") "Grabe! Haha 😂" else "OMG yes! 😄"
            "low" -> if (profile.culturalContext == "filipino") "Oo nga eh 😌" else "Yeah, I feel you 😊"
            else -> if (profile.culturalContext == "filipino") "Sige lang! 👍" else "Sounds good! 👌"
        }
    }
    
    fun setCloneEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLONE_ENABLED, enabled).apply()
        _cloneEnabled.value = enabled
    }
    
    private fun getTrainingData(): List<String> {
        val json = prefs.getString(KEY_TRAINING_DATA, "[]") ?: "[]"
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    private fun saveTrainingData(data: List<String>) {
        val json = gson.toJson(data)
        prefs.edit().putString(KEY_TRAINING_DATA, json).apply()
    }
    
    private fun getPersonalityProfile(): PersonalityProfile {
        val json = prefs.getString(KEY_PERSONALITY_PROFILE, null)
        return if (json != null) {
            gson.fromJson(json, PersonalityProfile::class.java) ?: PersonalityProfile()
        } else {
            PersonalityProfile()
        }
    }
    
    private fun savePersonalityProfile(profile: PersonalityProfile) {
        val json = gson.toJson(profile)
        prefs.edit().putString(KEY_PERSONALITY_PROFILE, json).apply()
    }
    
    private fun getCloneEnabled(): Boolean {
        return prefs.getBoolean(KEY_CLONE_ENABLED, false)
    }
}

data class PersonalityProfile(
    val communicationStyle: String = "casual",
    val emojiUsage: String = "medium",
    val humorType: String = "playful",
    val affectionLevel: String = "medium",
    val energyLevel: String = "medium",
    val commonPhrases: List<String> = emptyList(),
    val personalityTraits: List<String> = emptyList(),
    val responseLength: String = "medium",
    val culturalContext: String = "mixed"
)
