package com.messagehub.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagehub.data.EnhancedMessage
import com.messagehub.features.EmojiReaction
import com.messagehub.features.MessageReactionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReactionViewModel @Inject constructor(
    private val reactionManager: MessageReactionManager
) : ViewModel() {
    
    val availableEmojis: StateFlow<List<EmojiReaction>> = reactionManager.availableEmojis
    val recentEmojis: StateFlow<List<String>> = reactionManager.recentEmojis
    val stickerPacks = reactionManager.stickerPacks
    
    fun getQuickReactions(): List<EmojiReaction> {
        return reactionManager.getQuickReactions()
    }
    
    fun getContextualReactions(message: EnhancedMessage): List<EmojiReaction> {
        return reactionManager.getContextualReactions(message)
    }
    
    fun addReaction(messageId: String, emoji: String, userId: String = "You", userName: String = "You") {
        viewModelScope.launch {
            val reaction = reactionManager.createReaction(messageId, emoji, userId, userName)
            // Here you would typically send the reaction to your backend
            // and update the local message with the new reaction
        }
    }
}
