package com.messagehub.features

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlocklistManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("blocklist", Context.MODE_PRIVATE)
    
    private val _blockedUsers = MutableStateFlow<Set<String>>(getBlockedUsers())
    val blockedUsers: StateFlow<Set<String>> = _blockedUsers.asStateFlow()
    
    private val _mutedChats = MutableStateFlow<Set<String>>(getMutedChats())
    val mutedChats: StateFlow<Set<String>> = _mutedChats.asStateFlow()
    
    fun blockUser(userId: String, platform: String) {
        val key = "$platform:$userId"
        val currentBlocked = getBlockedUsers().toMutableSet()
        currentBlocked.add(key)
        saveBlockedUsers(currentBlocked)
        _blockedUsers.value = currentBlocked
    }
    
    fun unblockUser(userId: String, platform: String) {
        val key = "$platform:$userId"
        val currentBlocked = getBlockedUsers().toMutableSet()
        currentBlocked.remove(key)
        saveBlockedUsers(currentBlocked)
        _blockedUsers.value = currentBlocked
    }
    
    fun isUserBlocked(userId: String, platform: String): Boolean {
        val key = "$platform:$userId"
        return _blockedUsers.value.contains(key)
    }
    
    fun muteChat(chatId: String, platform: String, duration: Long = 0) {
        val key = if (duration > 0) {
            "$platform:$chatId:${System.currentTimeMillis() + duration}"
        } else {
            "$platform:$chatId:permanent"
        }
        
        val currentMuted = getMutedChats().toMutableSet()
        currentMuted.add(key)
        saveMutedChats(currentMuted)
        _mutedChats.value = currentMuted
    }
    
    fun unmuteChat(chatId: String, platform: String) {
        val currentMuted = getMutedChats().toMutableSet()
        val toRemove = currentMuted.filter { it.startsWith("$platform:$chatId:") }
        currentMuted.removeAll(toRemove.toSet())
        saveMutedChats(currentMuted)
        _mutedChats.value = currentMuted
    }
    
    fun isChatMuted(chatId: String, platform: String): Boolean {
        val now = System.currentTimeMillis()
        return _mutedChats.value.any { mutedKey ->
            when {
                mutedKey == "$platform:$chatId:permanent" -> true
                mutedKey.startsWith("$platform:$chatId:") -> {
                    val timestamp = mutedKey.substringAfterLast(":").toLongOrNull()
                    timestamp != null && timestamp > now
                }
                else -> false
            }
        }
    }
    
    private fun getBlockedUsers(): Set<String> {
        return prefs.getStringSet("blocked_users", emptySet()) ?: emptySet()
    }
    
    private fun saveBlockedUsers(blockedUsers: Set<String>) {
        prefs.edit().putStringSet("blocked_users", blockedUsers).apply()
    }
    
    private fun getMutedChats(): Set<String> {
        return prefs.getStringSet("muted_chats", emptySet()) ?: emptySet()
    }
    
    private fun saveMutedChats(mutedChats: Set<String>) {
        prefs.edit().putStringSet("muted_chats", mutedChats).apply()
    }
}
