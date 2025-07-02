package com.messagehub.features

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.messagehub.data.EnhancedMessage
import com.messagehub.data.MessageMood
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "MoodDetector"
        private const val PREFS_NAME = "mood_detector"
        private const val KEY_SEXT_MODE_ENABLED = "sext_mode_enabled"
        private const val KEY_MOOD_SENSITIVITY = "mood_sensitivity"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _currentMood = MutableStateFlow(MessageMood.NEUTRAL)
    val currentMood: StateFlow<MessageMood> = _currentMood.asStateFlow()
    
    private val _sextModeEnabled = MutableStateFlow(getSextModeEnabled())
    val sextModeEnabled: StateFlow<Boolean> = _sextModeEnabled.asStateFlow()
    
    // Spicy keywords and emojis
    private val spicyKeywords = setOf(
        // Flirty
        "sexy", "hot", "beautiful", "gorgeous", "cute", "babe", "baby", "honey", "sweetheart",
        "miss you", "thinking of you", "can't wait", "want you", "need you",
        // Filipino flirty
        "ganda", "pogi", "mahal", "sinta", "love", "crush", "kilig", "lambing",
        // Suggestive
        "tonight", "alone", "bed", "sleep", "dream", "kiss", "hug", "touch",
        // Playful
        "tease", "naughty", "bad", "good girl", "good boy", "daddy", "mommy"
    )
    
    private val spicyEmojis = setOf(
        "😘", "😍", "🥰", "😋", "😏", "🤤", "🔥", "💋", "💕", "💖", "💗", "💘", "💝", "💞", "💟",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💯", "💢", "💥",
        "🍑", "🍆", "🌶️", "🥵", "🥶", "😈", "👅", "💦", "🍯", "🔞", "🎭", "🌹"
    )
    
    private val romanticKeywords = setOf(
        "love", "forever", "always", "together", "relationship", "boyfriend", "girlfriend",
        "date", "dinner", "movie", "walk", "sunset", "stars", "moon", "heart"
    )
    
    private val playfulKeywords = setOf(
        "haha", "lol", "lmao", "funny", "joke", "silly", "crazy", "wild", "fun", "party",
        "game", "play", "tease", "prank", "meme", "savage", "charot", "joke lang"
    )
    
    private val sadKeywords = setOf(
        "sad", "cry", "hurt", "pain", "sorry", "miss", "lonely", "empty", "broken",
        "upset", "angry", "mad", "frustrated", "tired", "stressed"
    )
    
    fun detectMood(message: EnhancedMessage): MessageMood {
        val content = message.content.lowercase()
        val mood = analyzeMoodFromContent(content)
        
        _currentMood.value = mood
        
        // Auto-enable sext mode if spicy content detected
        if (mood == MessageMood.SPICY && !getSextModeEnabled()) {
            Log.d(TAG, "Spicy content detected, suggesting sext mode")
        }
        
        return mood
    }
    
    private fun analyzeMoodFromContent(content: String): MessageMood {
        var spicyScore = 0
        var romanticScore = 0
        var playfulScore = 0
        var sadScore = 0
        
        // Check keywords
        spicyKeywords.forEach { keyword ->
            if (content.contains(keyword)) spicyScore += 2
        }
        
        romanticKeywords.forEach { keyword ->
            if (content.contains(keyword)) romanticScore += 1
        }
        
        playfulKeywords.forEach { keyword ->
            if (content.contains(keyword)) playfulScore += 1
        }
        
        sadKeywords.forEach { keyword ->
            if (content.contains(keyword)) sadScore += 1
        }
        
        // Check emojis
        spicyEmojis.forEach { emoji ->
            if (content.contains(emoji)) spicyScore += 3
        }
        
        // Analyze patterns
        if (content.contains("...") || content.contains("😏")) spicyScore += 1
        if (content.length > 100) romanticScore += 1 // Long messages tend to be romantic
        if (content.contains("!!") || content.contains("???")) playfulScore += 1
        
        // Determine mood based on highest score
        val maxScore = maxOf(spicyScore, romanticScore, playfulScore, sadScore)
        
        return when {
            maxScore == 0 -> MessageMood.NEUTRAL
            spicyScore == maxScore && spicyScore >= 2 -> MessageMood.SPICY
            romanticScore == maxScore -> MessageMood.ROMANTIC
            playfulScore == maxScore -> MessageMood.PLAYFUL
            sadScore == maxScore -> MessageMood.SAD
            else -> MessageMood.NEUTRAL
        }
    }
    
    fun setSextModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEXT_MODE_ENABLED, enabled).apply()
        _sextModeEnabled.value = enabled
        Log.d(TAG, "Sext mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    private fun getSextModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_SEXT_MODE_ENABLED, false)
    }
    
    fun getMoodBasedResponse(originalMood: MessageMood, userInput: String): String {
        return when (originalMood) {
            MessageMood.SPICY -> generateSpicyResponse(userInput)
            MessageMood.ROMANTIC -> generateRomanticResponse(userInput)
            MessageMood.PLAYFUL -> generatePlayfulResponse(userInput)
            MessageMood.SAD -> generateComfortingResponse(userInput)
            MessageMood.NEUTRAL -> generateNeutralResponse(userInput)
        }
    }
    
    private fun generateSpicyResponse(input: String): String {
        val spicyResponses = listOf(
            "Mmm, tell me more... 😏",
            "You're making me blush 🔥",
            "Is that so? 😈",
            "You know exactly what to say 💋",
            "Keep talking like that... 🥵",
            "You're trouble 😘",
            "I like where this is going 💕"
        )
        return spicyResponses.random()
    }
    
    private fun generateRomanticResponse(input: String): String {
        val romanticResponses = listOf(
            "You're so sweet 💕",
            "That made my heart flutter 💖",
            "I'm so lucky to have you ❤️",
            "You always know what to say 🥰",
            "Missing you too 💘",
            "Can't wait to see you 💞",
            "You make me so happy 😍"
        )
        return romanticResponses.random()
    }
    
    private fun generatePlayfulResponse(input: String): String {
        val playfulResponses = listOf(
            "Haha you're so silly! 😂",
            "OMG stop it! 🤣",
            "You're crazy lol 😜",
            "Charot! 😝",
            "Grabe ka talaga! 🤪",
            "You're such a tease! 😋",
            "I can't with you! 😆"
        )
        return playfulResponses.random()
    }
    
    private fun generateComfortingResponse(input: String): String {
        val comfortingResponses = listOf(
            "I'm here for you 🤗",
            "It's going to be okay ❤️",
            "You're stronger than you know 💪",
            "I believe in you 🌟",
            "Sending you hugs 🫂",
            "You've got this! 💕",
            "I'm always here to listen 👂"
        )
        return comfortingResponses.random()
    }
    
    private fun generateNeutralResponse(input: String): String {
        val neutralResponses = listOf(
            "Got it! 👍",
            "Sounds good 😊",
            "Okay, cool! ✨",
            "Thanks for letting me know 😌",
            "Alright! 👌",
            "Sure thing! 😄",
            "Perfect! ⭐"
        )
        return neutralResponses.random()
    }
}

enum class MessageMood {
    NEUTRAL,
    SPICY,
    ROMANTIC,
    PLAYFUL,
    SAD
}
