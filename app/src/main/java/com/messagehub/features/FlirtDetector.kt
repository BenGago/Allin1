package com.messagehub.features

import android.content.Context
import android.util.Log
import com.messagehub.data.EnhancedMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class FlirtDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "FlirtDetector"
    }
    
    private val _flirtScore = MutableStateFlow(0f)
    val flirtScore: StateFlow<Float> = _flirtScore.asStateFlow()
    
    private val _crushProbability = MutableStateFlow(0f)
    val crushProbability: StateFlow<Float> = _crushProbability.asStateFlow()
    
    private val _flirtAnalysis = MutableStateFlow(FlirtAnalysis())
    val flirtAnalysis: StateFlow<FlirtAnalysis> = _flirtAnalysis.asStateFlow()
    
    // Flirt detection keywords with weights
    private val flirtKeywords = mapOf(
        // High intensity
        "sexy" to 10f, "hot" to 10f, "gorgeous" to 8f, "beautiful" to 7f,
        "want you" to 15f, "need you" to 12f, "miss you" to 8f,
        "thinking of you" to 9f, "can't stop thinking" to 12f,
        
        // Medium intensity
        "cute" to 6f, "sweet" to 5f, "handsome" to 6f, "pretty" to 6f,
        "babe" to 8f, "baby" to 8f, "honey" to 7f, "love" to 6f,
        "crush" to 9f, "like you" to 8f, "special" to 5f,
        
        // Filipino flirt terms
        "ganda" to 7f, "pogi" to 7f, "crush kita" to 12f, "type kita" to 10f,
        "kilig" to 8f, "torpe" to 6f, "lambing" to 9f, "sinta" to 8f,
        "mahal" to 10f, "lab" to 8f, "beh" to 5f, "bb" to 6f,
        
        // Suggestive
        "tonight" to 7f, "alone" to 8f, "bed" to 9f, "sleep" to 4f,
        "dream" to 6f, "kiss" to 12f, "hug" to 8f, "touch" to 10f,
        "close" to 5f, "together" to 7f, "date" to 8f
    )
    
    private val flirtEmojis = mapOf(
        "😘" to 12f, "😍" to 10f, "🥰" to 9f, "😋" to 7f, "😏" to 11f,
        "🤤" to 8f, "🔥" to 9f, "💋" to 15f, "💕" to 8f, "💖" to 9f,
        "💗" to 8f, "💘" to 12f, "💝" to 7f, "💞" to 9f, "💟" to 8f,
        "❤️" to 8f, "🧡" to 6f, "💛" to 6f, "💚" to 6f, "💙" to 6f,
        "💜" to 6f, "🖤" to 7f, "🤍" to 6f, "🤎" to 6f, "💯" to 5f,
        "🍑" to 15f, "🍆" to 15f, "🌶️" to 10f, "🥵" to 12f, "😈" to 11f,
        "👅" to 13f, "💦" to 14f, "🍯" to 8f, "🌹" to 7f
    )
    
    private val conversationHistory = mutableListOf<EnhancedMessage>()
    
    fun analyzeFlirtLevel(message: EnhancedMessage): FlirtAnalysis {
        // Add to conversation history
        conversationHistory.add(message)
        if (conversationHistory.size > 50) {
            conversationHistory.removeAt(0) // Keep recent messages only
        }
        
        val analysis = performFlirtAnalysis(message)
        _flirtAnalysis.value = analysis
        _flirtScore.value = analysis.flirtScore
        
        // Calculate crush probability based on conversation patterns
        val crushProb = calculateCrushProbability()
        _crushProbability.value = crushProb
        
        Log.d(TAG, "Flirt analysis: Score=${analysis.flirtScore}, Crush=${crushProb}%")
        
        return analysis
    }
    
    private fun performFlirtAnalysis(message: EnhancedMessage): FlirtAnalysis {
        val content = message.content.lowercase()
        var score = 0f
        val detectedKeywords = mutableListOf<String>()
        val detectedEmojis = mutableListOf<String>()
        
        // Analyze keywords
        flirtKeywords.forEach { (keyword, weight) ->
            if (content.contains(keyword)) {
                score += weight
                detectedKeywords.add(keyword)
            }
        }
        
        // Analyze emojis
        flirtEmojis.forEach { (emoji, weight) ->
            if (content.contains(emoji)) {
                score += weight
                detectedEmojis.add(emoji)
            }
        }
        
        // Analyze patterns
        score += analyzeMessagePatterns(content)
        
        // Normalize score to 0-100
        val normalizedScore = min(score, 100f)
        
        return FlirtAnalysis(
            flirtScore = normalizedScore,
            intensity = getFlirtIntensity(normalizedScore),
            detectedKeywords = detectedKeywords,
            detectedEmojis = detectedEmojis,
            messageLength = message.content.length,
            timestamp = message.timestamp,
            sender = message.sender,
            analysis = generateFlirtAnalysisText(normalizedScore, detectedKeywords, detectedEmojis)
        )
    }
    
    private fun analyzeMessagePatterns(content: String): Float {
        var patternScore = 0f
        
        // Long messages can be more intimate
        if (content.length > 100) patternScore += 3f
        if (content.length > 200) patternScore += 2f
        
        // Multiple question marks (curiosity/interest)
        if (content.contains("???")) patternScore += 4f
        if (content.contains("??")) patternScore += 2f
        
        // Ellipsis (suggestive pause)
        if (content.contains("...")) patternScore += 5f
        
        // Repetitive letters (excitement)
        if (Regex("[a-z]\\1{2,}").containsMatchIn(content)) patternScore += 3f
        
        // All caps words (intensity)
        if (Regex("[A-Z]{3,}").containsMatchIn(content)) patternScore += 2f
        
        // Time references
        if (content.contains("tonight") || content.contains("later") || content.contains("tomorrow")) {
            patternScore += 4f
        }
        
        // Personal pronouns (intimacy)
        val personalPronouns = listOf("you and me", "us", "we", "together")
        personalPronouns.forEach { pronoun ->
            if (content.contains(pronoun)) patternScore += 3f
        }
        
        return patternScore
    }
    
    private fun getFlirtIntensity(score: Float): FlirtIntensity {
        return when {
            score >= 80f -> FlirtIntensity.EXTREMELY_FLIRTY
            score >= 60f -> FlirtIntensity.VERY_FLIRTY
            score >= 40f -> FlirtIntensity.MODERATELY_FLIRTY
            score >= 20f -> FlirtIntensity.SLIGHTLY_FLIRTY
            score >= 10f -> FlirtIntensity.SUBTLE_HINTS
            else -> FlirtIntensity.NOT_FLIRTY
        }
    }
    
    private fun calculateCrushProbability(): Float {
        if (conversationHistory.size < 5) return 0f
        
        val recentMessages = conversationHistory.takeLast(20)
        var crushScore = 0f
        
        // Analyze conversation patterns
        val messageFrequency = recentMessages.size.toFloat()
        val avgFlirtScore = recentMessages.map { analyzeFlirtLevel(it).flirtScore }.average().toFloat()
        
        // Frequency bonus
        crushScore += min(messageFrequency * 2f, 30f)
        
        // Average flirt score
        crushScore += avgFlirtScore * 0.5f
        
        // Consistency bonus (regular flirting)
        val flirtyMessages = recentMessages.count { analyzeFlirtLevel(it).flirtScore > 20f }
        val consistency = (flirtyMessages.toFloat() / recentMessages.size) * 100f
        crushScore += consistency * 0.3f
        
        // Recent activity bonus
        val recentFlirtyMessages = recentMessages.takeLast(5).count { analyzeFlirtLevel(it).flirtScore > 30f }
        crushScore += recentFlirtyMessages * 5f
        
        return min(crushScore, 100f)
    }
    
    private fun generateFlirtAnalysisText(score: Float, keywords: List<String>, emojis: List<String>): String {
        val intensity = getFlirtIntensity(score)
        
        return when (intensity) {
            FlirtIntensity.EXTREMELY_FLIRTY -> "🔥 SUPER FLIRTY! This person is definitely into you!"
            FlirtIntensity.VERY_FLIRTY -> "😍 Very flirty vibes! They're showing serious interest."
            FlirtIntensity.MODERATELY_FLIRTY -> "😊 Moderate flirting detected. They might like you!"
            FlirtIntensity.SLIGHTLY_FLIRTY -> "😌 Subtle flirting. Could be friendly or interested."
            FlirtIntensity.SUBTLE_HINTS -> "🤔 Very subtle hints. Hard to tell if flirting."
            FlirtIntensity.NOT_FLIRTY -> "😐 No flirting detected. Just normal conversation."
        }
    }
    
    fun getFlirtTrends(): FlirtTrends {
        val recentMessages = conversationHistory.takeLast(10)
        val scores = recentMessages.map { analyzeFlirtLevel(it).flirtScore }
        
        return FlirtTrends(
            averageScore = scores.average().toFloat(),
            peakScore = scores.maxOrNull() ?: 0f,
            trend = if (scores.size >= 3) {
                val recent = scores.takeLast(3).average()
                val older = scores.dropLast(3).average()
                when {
                    recent > older + 10 -> "📈 Increasing"
                    recent < older - 10 -> "📉 Decreasing"
                    else -> "➡️ Stable"
                }
            } else "📊 Not enough data",
            totalMessages = conversationHistory.size
        )
    }
}

data class FlirtAnalysis(
    val flirtScore: Float = 0f,
    val intensity: FlirtIntensity = FlirtIntensity.NOT_FLIRTY,
    val detectedKeywords: List<String> = emptyList(),
    val detectedEmojis: List<String> = emptyList(),
    val messageLength: Int = 0,
    val timestamp: String = "",
    val sender: String = "",
    val analysis: String = ""
)

data class FlirtTrends(
    val averageScore: Float,
    val peakScore: Float,
    val trend: String,
    val totalMessages: Int
)

enum class FlirtIntensity {
    NOT_FLIRTY,
    SUBTLE_HINTS,
    SLIGHTLY_FLIRTY,
    MODERATELY_FLIRTY,
    VERY_FLIRTY,
    EXTREMELY_FLIRTY
}
