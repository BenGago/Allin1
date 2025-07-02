package com.messagehub.config

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("platform_config", Context.MODE_PRIVATE)
    
    // Telegram Configuration
    fun getTelegramBotToken(): String {
        return prefs.getString("telegram_bot_token", "") ?: ""
    }
    
    fun setTelegramBotToken(token: String) {
        prefs.edit().putString("telegram_bot_token", token).apply()
    }
    
    // Messenger Configuration
    fun getMessengerPageAccessToken(): String {
        return prefs.getString("messenger_page_access_token", "") ?: ""
    }
    
    fun setMessengerPageAccessToken(token: String) {
        prefs.edit().putString("messenger_page_access_token", token).apply()
    }
    
    fun getMessengerVerifyToken(): String {
        return prefs.getString("messenger_verify_token", "") ?: ""
    }
    
    fun setMessengerVerifyToken(token: String) {
        prefs.edit().putString("messenger_verify_token", token).apply()
    }
    
    // Twitter Configuration
    fun getTwitterBearerToken(): String {
        return prefs.getString("twitter_bearer_token", "") ?: ""
    }
    
    fun setTwitterBearerToken(token: String) {
        prefs.edit().putString("twitter_bearer_token", token).apply()
    }
    
    fun getTwitterApiKey(): String {
        return prefs.getString("twitter_api_key", "") ?: ""
    }
    
    fun setTwitterApiKey(key: String) {
        prefs.edit().putString("twitter_api_key", key).apply()
    }
    
    fun getTwitterApiSecret(): String {
        return prefs.getString("twitter_api_secret", "") ?: ""
    }
    
    fun setTwitterApiSecret(secret: String) {
        prefs.edit().putString("twitter_api_secret", secret).apply()
    }
    
    // Platform Status
    fun isTelegramEnabled(): Boolean {
        return getTelegramBotToken().isNotEmpty()
    }
    
    fun isMessengerEnabled(): Boolean {
        return getMessengerPageAccessToken().isNotEmpty()
    }
    
    fun isTwitterEnabled(): Boolean {
        return getTwitterBearerToken().isNotEmpty()
    }
}
