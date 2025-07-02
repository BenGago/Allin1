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
class SleepModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("sleep_mode", Context.MODE_PRIVATE)
    
    private val _isSleepModeActive = MutableStateFlow(false)
    val isSleepModeActive: StateFlow<Boolean> = _isSleepModeActive.asStateFlow()
    
    private val _sleepModeSettings = MutableStateFlow(getSleepModeSettings())
    val sleepModeSettings: StateFlow<SleepModeSettings> = _sleepModeSettings.asStateFlow()
    
    init {
        checkSleepModeStatus()
    }
    
    fun setSleepModeSettings(settings: SleepModeSettings) {
        _sleepModeSettings.value = settings
        saveSleepModeSettings(settings)
        checkSleepModeStatus()
    }
    
    fun toggleSleepMode() {
        val current = _sleepModeSettings.value
        val updated = current.copy(enabled = !current.enabled)
        setSleepModeSettings(updated)
    }
    
    fun checkSleepModeStatus() {
        val settings = _sleepModeSettings.value
        val isActive = if (settings.enabled) {
            isCurrentlyInSleepHours(settings)
        } else {
            false
        }
        
        _isSleepModeActive.value = isActive
    }
    
    private fun isCurrentlyInSleepHours(settings: SleepModeSettings): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTime = currentHour * 60 + currentMinute
        
        val startTime = settings.startHour * 60 + settings.startMinute
        val endTime = settings.endHour * 60 + settings.endMinute
        
        return if (startTime <= endTime) {
            // Same day (e.g., 22:00 to 23:59)
            currentTime in startTime..endTime
        } else {
            // Crosses midnight (e.g., 22:00 to 06:00)
            currentTime >= startTime || currentTime <= endTime
        }
    }
    
    fun getSleepModeResponse(senderName: String, platform: String): String {
        val settings = _sleepModeSettings.value
        
        return when (settings.responseStyle) {
            SleepResponseStyle.SIMPLE -> "I'm sleeping right now. I'll get back to you later! 😴"
            
            SleepResponseStyle.DETAILED -> {
                val wakeTime = "${settings.endHour.toString().padStart(2, '0')}:${settings.endMinute.toString().padStart(2, '0')}"
                "Hey! I'm currently in sleep mode and will be back around $wakeTime. I'll reply to your message then! 🌙"
            }
            
            SleepResponseStyle.FUNNY -> {
                val funnyResponses = listOf(
                    "Zzz... 😴 My phone is sleeping but my dreams are active! Talk to you when I wake up!",
                    "Currently in hibernation mode 🐻 Will emerge when the sun rises!",
                    "Shh! 🤫 I'm having a conversation with my pillow right now. Back later!",
                    "Sleep mode activated! 🛌 Even my notifications are wearing pajamas!",
                    "I'm off duty in dreamland! 💤 Will return to the land of the awake soon!"
                )
                funnyResponses.random()
            }
            
            SleepResponseStyle.PERSONALIZED -> {
                val personalizedResponses = listOf(
                    "Hey $senderName! I'm sleeping right now but I'll catch up with you tomorrow! 😊",
                    "$senderName! Currently recharging my social batteries 🔋 Talk soon!",
                    "Hi $senderName! In sleep mode until morning. Your message is safe with me! 🌙",
                    "$senderName! Counting sheep right now 🐑 Will reply when I'm back!"
                )
                personalizedResponses.random()
            }
            
            SleepResponseStyle.PLATFORM_SPECIFIC -> when (platform.lowercase()) {
                "telegram" -> "🤖 Sleep mode activated! Will process your message when I reboot in the morning!"
                "messenger" -> "😴 Facebook says I'm sleeping! Will poke you back later!"
                "twitter" -> "🐦 This bird is in its nest! Will tweet back when I wake up!"
                "sms" -> "📱 Auto-reply: Currently sleeping. Will text back later!"
                else -> "I'm sleeping right now. I'll get back to you later! 😴"
            }
        }
    }
    
    fun shouldAutoReply(): Boolean {
        return _isSleepModeActive.value && _sleepModeSettings.value.autoReply
    }
    
    fun shouldSuppressNotifications(): Boolean {
        return _isSleepModeActive.value && _sleepModeSettings.value.suppressNotifications
    }
    
    private fun getSleepModeSettings(): SleepModeSettings {
        return SleepModeSettings(
            enabled = prefs.getBoolean("enabled", false),
            startHour = prefs.getInt("start_hour", 22),
            startMinute = prefs.getInt("start_minute", 0),
            endHour = prefs.getInt("end_hour", 6),
            endMinute = prefs.getInt("end_minute", 0),
            autoReply = prefs.getBoolean("auto_reply", true),
            suppressNotifications = prefs.getBoolean("suppress_notifications", true),
            responseStyle = SleepResponseStyle.valueOf(
                prefs.getString("response_style", SleepResponseStyle.SIMPLE.name) ?: SleepResponseStyle.SIMPLE.name
            ),
            weekendsOnly = prefs.getBoolean("weekends_only", false),
            emergencyKeywords = prefs.getStringSet("emergency_keywords", setOf("emergency", "urgent", "help"))?.toSet() ?: emptySet()
        )
    }
    
    private fun saveSleepModeSettings(settings: SleepModeSettings) {
        prefs.edit()
            .putBoolean("enabled", settings.enabled)
            .putInt("start_hour", settings.startHour)
            .putInt("start_minute", settings.startMinute)
            .putInt("end_hour", settings.endHour)
            .putInt("end_minute", settings.endMinute)
            .putBoolean("auto_reply", settings.autoReply)
            .putBoolean("suppress_notifications", settings.suppressNotifications)
            .putString("response_style", settings.responseStyle.name)
            .putBoolean("weekends_only", settings.weekendsOnly)
            .putStringSet("emergency_keywords", settings.emergencyKeywords)
            .apply()
    }
    
    fun isEmergencyMessage(content: String): Boolean {
        val settings = _sleepModeSettings.value
        val lowercaseContent = content.lowercase()
        
        return settings.emergencyKeywords.any { keyword ->
            lowercaseContent.contains(keyword.lowercase())
        }
    }
}

data class SleepModeSettings(
    val enabled: Boolean = false,
    val startHour: Int = 22, // 10 PM
    val startMinute: Int = 0,
    val endHour: Int = 6, // 6 AM
    val endMinute: Int = 0,
    val autoReply: Boolean = true,
    val suppressNotifications: Boolean = true,
    val responseStyle: SleepResponseStyle = SleepResponseStyle.SIMPLE,
    val weekendsOnly: Boolean = false,
    val emergencyKeywords: Set<String> = setOf("emergency", "urgent", "help")
)

enum class SleepResponseStyle {
    SIMPLE,
    DETAILED,
    FUNNY,
    PERSONALIZED,
    PLATFORM_SPECIFIC
}
