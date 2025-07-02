package com.messagehub.features

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigitalWellbeingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("digital_wellbeing", Context.MODE_PRIVATE)
    
    private val _dailyStats = MutableStateFlow(getDailyStats())
    val dailyStats: StateFlow<DailyUsageStats> = _dailyStats.asStateFlow()
    
    private val _wellbeingScore = MutableStateFlow(calculateWellbeingScore())
    val wellbeingScore: StateFlow<Int> = _wellbeingScore.asStateFlow()
    
    private val _suggestions = MutableStateFlow<List<WellbeingSuggestion>>(emptyList())
    val suggestions: StateFlow<List<WellbeingSuggestion>> = _suggestions.asStateFlow()
    
    fun recordMessageSent(platform: String) {
        val today = getTodayKey()
        val currentStats = getDailyStats()
        
        val updatedStats = currentStats.copy(
            messagesSent = currentStats.messagesSent + 1,
            platformUsage = currentStats.platformUsage.toMutableMap().apply {
                this[platform] = (this[platform] ?: 0) + 1
            }
        )
        
        saveDailyStats(updatedStats)
        _dailyStats.value = updatedStats
        updateWellbeingScore()
        generateSuggestions()
    }
    
    fun recordMessageReceived(platform: String) {
        val currentStats = getDailyStats()
        
        val updatedStats = currentStats.copy(
            messagesReceived = currentStats.messagesReceived + 1,
            platformUsage = currentStats.platformUsage.toMutableMap().apply {
                this[platform] = (this[platform] ?: 0) + 1
            }
        )
        
        saveDailyStats(updatedStats)
        _dailyStats.value = updatedStats
        updateWellbeingScore()
    }
    
    fun recordSessionStart() {
        val currentStats = getDailyStats()
        val updatedStats = currentStats.copy(
            sessionsStarted = currentStats.sessionsStarted + 1,
            lastSessionStart = System.currentTimeMillis()
        )
        
        saveDailyStats(updatedStats)
        _dailyStats.value = updatedStats
    }
    
    fun recordSessionEnd() {
        val currentStats = getDailyStats()
        val sessionDuration = System.currentTimeMillis() - currentStats.lastSessionStart
        
        val updatedStats = currentStats.copy(
            totalTimeSpent = currentStats.totalTimeSpent + sessionDuration,
            averageSessionLength = if (currentStats.sessionsStarted > 0) {
                (currentStats.totalTimeSpent + sessionDuration) / currentStats.sessionsStarted
            } else 0L
        )
        
        saveDailyStats(updatedStats)
        _dailyStats.value = updatedStats
        updateWellbeingScore()
        generateSuggestions()
    }
    
    private fun updateWellbeingScore() {
        val score = calculateWellbeingScore()
        _wellbeingScore.value = score
    }
    
    private fun calculateWellbeingScore(): Int {
        val stats = getDailyStats()
        var score = 100
        
        // Deduct points for excessive usage
        val hoursSpent = stats.totalTimeSpent / (1000 * 60 * 60)
        if (hoursSpent > 4) score -= ((hoursSpent - 4) * 10).toInt()
        
        // Deduct points for too many messages
        if (stats.messagesSent > 100) score -= ((stats.messagesSent - 100) / 10)
        
        // Deduct points for too many sessions
        if (stats.sessionsStarted > 20) score -= ((stats.sessionsStarted - 20) * 2)
        
        // Bonus points for balanced usage
        if (stats.platformUsage.size > 1) score += 5 // Using multiple platforms
        if (hoursSpent in 1..3) score += 10 // Healthy usage time
        
        return maxOf(0, minOf(100, score))
    }
    
    private fun generateSuggestions() {
        val stats = getDailyStats()
        val suggestions = mutableListOf<WellbeingSuggestion>()
        
        val hoursSpent = stats.totalTimeSpent / (1000 * 60 * 60)
        
        when {
            hoursSpent > 6 -> {
                suggestions.add(
                    WellbeingSuggestion(
                        type = SuggestionType.BREAK_REMINDER,
                        title = "Take a Break",
                        message = "You've been messaging for ${hoursSpent} hours today. Consider taking a break!",
                        priority = Priority.HIGH
                    )
                )
            }
            hoursSpent > 3 -> {
                suggestions.add(
                    WellbeingSuggestion(
                        type = SuggestionType.MINDFUL_USAGE,
                        title = "Mindful Messaging",
                        message = "You're having an active messaging day. Remember to stay present!",
                        priority = Priority.MEDIUM
                    )
                )
            }
        }
        
        if (stats.messagesSent > 150) {
            suggestions.add(
                WellbeingSuggestion(
                    type = SuggestionType.REDUCE_FREQUENCY,
                    title = "Message Frequency",
                    message = "You've sent ${stats.messagesSent} messages today. Consider consolidating your thoughts!",
                    priority = Priority.MEDIUM
                )
            )
        }
        
        if (stats.sessionsStarted > 30) {
            suggestions.add(
                WellbeingSuggestion(
                    type = SuggestionType.BATCH_MESSAGES,
                    title = "Batch Your Messages",
                    message = "You've opened the app ${stats.sessionsStarted} times. Try batching your messages!",
                    priority = Priority.LOW
                )
            )
        }
        
        // Positive reinforcement
        if (hoursSpent <= 2 && stats.messagesSent <= 50) {
            suggestions.add(
                WellbeingSuggestion(
                    type = SuggestionType.POSITIVE_REINFORCEMENT,
                    title = "Great Balance!",
                    message = "You're maintaining healthy messaging habits today! 🌟",
                    priority = Priority.LOW
                )
            )
        }
        
        _suggestions.value = suggestions
    }
    
    fun getWeeklyStats(): WeeklyUsageStats {
        val weeklyData = mutableMapOf<String, DailyUsageStats>()
        val calendar = Calendar.getInstance()
        
        // Get last 7 days
        repeat(7) { dayOffset ->
            calendar.add(Calendar.DAY_OF_YEAR, -dayOffset)
            val dayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            weeklyData[dayKey] = getDailyStatsForDate(dayKey)
            calendar.add(Calendar.DAY_OF_YEAR, dayOffset) // Reset
        }
        
        val totalMessages = weeklyData.values.sumOf { it.messagesSent + it.messagesReceived }
        val totalTime = weeklyData.values.sumOf { it.totalTimeSpent }
        val averageScore = weeklyData.values.map { calculateWellbeingScoreForStats(it) }.average()
        
        return WeeklyUsageStats(
            dailyStats = weeklyData,
            totalMessages = totalMessages,
            totalTimeSpent = totalTime,
            averageWellbeingScore = averageScore.toInt(),
            mostUsedPlatform = findMostUsedPlatform(weeklyData.values.toList())
        )
    }
    
    private fun findMostUsedPlatform(statsList: List<DailyUsageStats>): String {
        val platformTotals = mutableMapOf<String, Int>()
        
        statsList.forEach { stats ->
            stats.platformUsage.forEach { (platform, count) ->
                platformTotals[platform] = (platformTotals[platform] ?: 0) + count
            }
        }
        
        return platformTotals.maxByOrNull { it.value }?.key ?: "Unknown"
    }
    
    private fun calculateWellbeingScoreForStats(stats: DailyUsageStats): Int {
        var score = 100
        val hoursSpent = stats.totalTimeSpent / (1000 * 60 * 60)
        
        if (hoursSpent > 4) score -= ((hoursSpent - 4) * 10).toInt()
        if (stats.messagesSent > 100) score -= ((stats.messagesSent - 100) / 10)
        if (stats.sessionsStarted > 20) score -= ((stats.sessionsStarted - 20) * 2)
        
        return maxOf(0, minOf(100, score))
    }
    
    private fun getTodayKey(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
    }
    
    private fun getDailyStats(): DailyUsageStats {
        return getDailyStatsForDate(getTodayKey())
    }
    
    private fun getDailyStatsForDate(dateKey: String): DailyUsageStats {
        val json = prefs.getString("stats_$dateKey", null)
        return if (json != null) {
            try {
                // Parse JSON manually or use Gson
                DailyUsageStats() // Simplified for now
            } catch (e: Exception) {
                DailyUsageStats()
            }
        } else {
            DailyUsageStats()
        }
    }
    
    private fun saveDailyStats(stats: DailyUsageStats) {
        val dateKey = getTodayKey()
        // Save as JSON (simplified)
        prefs.edit().putString("stats_$dateKey", "json_data").apply()
    }
}

data class DailyUsageStats(
    val messagesSent: Int = 0,
    val messagesReceived: Int = 0,
    val totalTimeSpent: Long = 0L, // milliseconds
    val sessionsStarted: Int = 0,
    val averageSessionLength: Long = 0L,
    val platformUsage: Map<String, Int> = emptyMap(),
    val lastSessionStart: Long = 0L
)

data class WeeklyUsageStats(
    val dailyStats: Map<String, DailyUsageStats>,
    val totalMessages: Int,
    val totalTimeSpent: Long,
    val averageWellbeingScore: Int,
    val mostUsedPlatform: String
)

data class WellbeingSuggestion(
    val type: SuggestionType,
    val title: String,
    val message: String,
    val priority: Priority
)

enum class SuggestionType {
    BREAK_REMINDER,
    MINDFUL_USAGE,
    REDUCE_FREQUENCY,
    BATCH_MESSAGES,
    POSITIVE_REINFORCEMENT
}

enum class Priority {
    LOW, MEDIUM, HIGH
}
