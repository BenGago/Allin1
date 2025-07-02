package com.messagehub.features

import android.content.Context
import android.util.Log
import com.messagehub.data.EnhancedMessage
import com.messagehub.data.Reaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageReactionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "MessageReactionManager"
    }
    
    private val _availableEmojis = MutableStateFlow(getDefaultEmojis())
    val availableEmojis: StateFlow<List<EmojiReaction>> = _availableEmojis.asStateFlow()
    
    private val _recentEmojis = MutableStateFlow<List<String>>(emptyList())
    val recentEmojis: StateFlow<List<String>> = _recentEmojis.asStateFlow()
    
    private val _stickerPacks = MutableStateFlow(getDefaultStickerPacks())
    val stickerPacks: StateFlow<List<StickerPack>> = _stickerPacks.asStateFlow()
    
    fun getQuickReactions(): List<EmojiReaction> {
        return listOf(
            EmojiReaction("❤️", "Love", "love"),
            EmojiReaction("😂", "Laugh", "laugh"),
            EmojiReaction("😮", "Wow", "wow"),
            EmojiReaction("😢", "Sad", "sad"),
            EmojiReaction("😡", "Angry", "angry"),
            EmojiReaction("👍", "Like", "like")
        )
    }
    
    fun getContextualReactions(message: EnhancedMessage): List<EmojiReaction> {
        val content = message.content.lowercase()
        
        return when {
            content.contains("funny") || content.contains("joke") || content.contains("haha") -> {
                listOf(
                    EmojiReaction("😂", "LMAO", "laugh"),
                    EmojiReaction("🤣", "Dead", "dead"),
                    EmojiReaction("😆", "Funny", "funny"),
                    EmojiReaction("😄", "Haha", "haha")
                )
            }
            
            content.contains("love") || content.contains("miss") -> {
                listOf(
                    EmojiReaction("❤️", "Love", "love"),
                    EmojiReaction("💕", "Hearts", "hearts"),
                    EmojiReaction("🥰", "Adore", "adore"),
                    EmojiReaction("😘", "Kiss", "kiss")
                )
            }
            
            content.contains("food") || content.contains("eat") -> {
                listOf(
                    EmojiReaction("🤤", "Drool", "drool"),
                    EmojiReaction("😋", "Yum", "yum"),
                    EmojiReaction("🍽️", "Hungry", "hungry"),
                    EmojiReaction("👨‍🍳", "Chef", "chef")
                )
            }
            
            content.contains("tired") || content.contains("sleep") -> {
                listOf(
                    EmojiReaction("😴", "Sleepy", "sleepy"),
                    EmojiReaction("🥱", "Yawn", "yawn"),
                    EmojiReaction("💤", "Sleep", "sleep"),
                    EmojiReaction("🛌", "Bed", "bed")
                )
            }
            
            else -> getQuickReactions()
        }
    }
    
    fun addRecentEmoji(emoji: String) {
        val current = _recentEmojis.value.toMutableList()
        current.remove(emoji) // Remove if already exists
        current.add(0, emoji) // Add to front
        
        // Keep only last 20 recent emojis
        if (current.size > 20) {
            current.removeAt(current.size - 1)
        }
        
        _recentEmojis.value = current
    }
    
    fun createReaction(messageId: String, emoji: String, userId: String, userName: String): Reaction {
        addRecentEmoji(emoji)
        
        return Reaction(
            emoji = emoji,
            userId = userId,
            userName = userName,
            timestamp = System.currentTimeMillis().toString()
        )
    }
    
    private fun getDefaultEmojis(): List<EmojiReaction> {
        return listOf(
            // Faces
            EmojiReaction("😀", "Grinning", "happy"),
            EmojiReaction("😃", "Smiley", "happy"),
            EmojiReaction("😄", "Smile", "happy"),
            EmojiReaction("😁", "Grin", "happy"),
            EmojiReaction("😆", "Laughing", "laugh"),
            EmojiReaction("😅", "Sweat Smile", "laugh"),
            EmojiReaction("🤣", "Rolling", "laugh"),
            EmojiReaction("😂", "Joy", "laugh"),
            EmojiReaction("🙂", "Slight Smile", "happy"),
            EmojiReaction("🙃", "Upside Down", "silly"),
            EmojiReaction("😉", "Wink", "flirt"),
            EmojiReaction("😊", "Blush", "happy"),
            EmojiReaction("😇", "Innocent", "angel"),
            EmojiReaction("🥰", "Hearts", "love"),
            EmojiReaction("😍", "Heart Eyes", "love"),
            EmojiReaction("🤩", "Star Eyes", "wow"),
            EmojiReaction("😘", "Kiss", "love"),
            EmojiReaction("😗", "Kissing", "love"),
            EmojiReaction("😚", "Kiss Closed", "love"),
            EmojiReaction("😙", "Kiss Smile", "love"),
            EmojiReaction("🥲", "Tear Joy", "emotional"),
            EmojiReaction("😋", "Yum", "food"),
            EmojiReaction("😛", "Tongue", "silly"),
            EmojiReaction("😜", "Wink Tongue", "silly"),
            EmojiReaction("🤪", "Zany", "crazy"),
            EmojiReaction("😝", "Squint Tongue", "silly"),
            EmojiReaction("🤑", "Money", "rich"),
            EmojiReaction("🤗", "Hug", "love"),
            EmojiReaction("🤭", "Hand Over", "shy"),
            EmojiReaction("🤫", "Shush", "secret"),
            EmojiReaction("🤔", "Think", "thinking"),
            EmojiReaction("🤐", "Zip", "quiet"),
            EmojiReaction("🤨", "Raised Brow", "suspicious"),
            EmojiReaction("😐", "Neutral", "meh"),
            EmojiReaction("😑", "Expressionless", "meh"),
            EmojiReaction("😶", "No Mouth", "speechless"),
            EmojiReaction("😏", "Smirk", "smug"),
            EmojiReaction("😒", "Unamused", "annoyed"),
            EmojiReaction("🙄", "Eye Roll", "annoyed"),
            EmojiReaction("😬", "Grimace", "awkward"),
            EmojiReaction("🤥", "Lying", "lie"),
            EmojiReaction("😔", "Pensive", "sad"),
            EmojiReaction("😕", "Confused", "confused"),
            EmojiReaction("🙁", "Frown", "sad"),
            EmojiReaction("☹️", "Frowning", "sad"),
            EmojiReaction("😣", "Persevere", "struggle"),
            EmojiReaction("😖", "Confounded", "frustrated"),
            EmojiReaction("😫", "Tired", "exhausted"),
            EmojiReaction("😩", "Weary", "tired"),
            EmojiReaction("🥺", "Pleading", "cute"),
            EmojiReaction("😢", "Cry", "sad"),
            EmojiReaction("😭", "Sob", "crying"),
            EmojiReaction("😤", "Huff", "annoyed"),
            EmojiReaction("😠", "Angry", "mad"),
            EmojiReaction("😡", "Rage", "furious"),
            EmojiReaction("🤬", "Swearing", "angry"),
            EmojiReaction("🤯", "Explode", "mindblown"),
            EmojiReaction("😳", "Flushed", "embarrassed"),
            EmojiReaction("🥵", "Hot", "hot"),
            EmojiReaction("🥶", "Cold", "cold"),
            EmojiReaction("😱", "Scream", "shocked"),
            EmojiReaction("😨", "Fearful", "scared"),
            EmojiReaction("😰", "Anxious", "worried"),
            EmojiReaction("😥", "Disappointed", "sad"),
            EmojiReaction("😓", "Downcast", "sad"),
            EmojiReaction("🤗", "Hugging", "hug"),
            EmojiReaction("🤤", "Drool", "want"),
            EmojiReaction("😴", "Sleep", "tired"),
            EmojiReaction("😪", "Sleepy", "tired"),
            EmojiReaction("😵", "Dizzy", "confused"),
            EmojiReaction("🤐", "Zipper", "quiet"),
            EmojiReaction("🥴", "Woozy", "drunk"),
            EmojiReaction("🤢", "Nausea", "sick"),
            EmojiReaction("🤮", "Vomit", "sick"),
            EmojiReaction("🤧", "Sneeze", "sick"),
            EmojiReaction("😷", "Mask", "sick"),
            EmojiReaction("🤒", "Thermometer", "sick"),
            EmojiReaction("🤕", "Bandage", "hurt"),
            
            // Hearts
            EmojiReaction("❤️", "Red Heart", "love"),
            EmojiReaction("🧡", "Orange Heart", "love"),
            EmojiReaction("💛", "Yellow Heart", "love"),
            EmojiReaction("💚", "Green Heart", "love"),
            EmojiReaction("💙", "Blue Heart", "love"),
            EmojiReaction("💜", "Purple Heart", "love"),
            EmojiReaction("🖤", "Black Heart", "love"),
            EmojiReaction("🤍", "White Heart", "love"),
            EmojiReaction("🤎", "Brown Heart", "love"),
            EmojiReaction("💔", "Broken Heart", "heartbreak"),
            EmojiReaction("❣️", "Heart Exclamation", "love"),
            EmojiReaction("💕", "Two Hearts", "love"),
            EmojiReaction("💞", "Revolving Hearts", "love"),
            EmojiReaction("💓", "Beating Heart", "love"),
            EmojiReaction("💗", "Growing Heart", "love"),
            EmojiReaction("💖", "Sparkling Heart", "love"),
            EmojiReaction("💘", "Cupid", "love"),
            EmojiReaction("💝", "Gift Heart", "love"),
            
            // Hands
            EmojiReaction("👍", "Thumbs Up", "like"),
            EmojiReaction("👎", "Thumbs Down", "dislike"),
            EmojiReaction("👌", "OK", "perfect"),
            EmojiReaction("🤌", "Pinched", "italian"),
            EmojiReaction("🤏", "Pinch", "small"),
            EmojiReaction("✌️", "Peace", "peace"),
            EmojiReaction("🤞", "Crossed", "hope"),
            EmojiReaction("🤟", "Love You", "love"),
            EmojiReaction("🤘", "Rock", "rock"),
            EmojiReaction("🤙", "Call Me", "call"),
            EmojiReaction("👈", "Left", "point"),
            EmojiReaction("👉", "Right", "point"),
            EmojiReaction("👆", "Up", "point"),
            EmojiReaction("🖕", "Middle", "rude"),
            EmojiReaction("👇", "Down", "point"),
            EmojiReaction("☝️", "Index", "point"),
            EmojiReaction("👏", "Clap", "applause"),
            EmojiReaction("🙌", "Praise", "celebrate"),
            EmojiReaction("👐", "Open Hands", "hug"),
            EmojiReaction("🤲", "Palms Up", "pray"),
            EmojiReaction("🤝", "Handshake", "deal"),
            EmojiReaction("🙏", "Pray", "thanks"),
            
            // Fire and symbols
            EmojiReaction("🔥", "Fire", "hot"),
            EmojiReaction("💯", "Hundred", "perfect"),
            EmojiReaction("💢", "Anger", "mad"),
            EmojiReaction("💥", "Boom", "explosion"),
            EmojiReaction("💫", "Dizzy", "stars"),
            EmojiReaction("💦", "Sweat", "wet"),
            EmojiReaction("💨", "Dash", "fast"),
            EmojiReaction("🕳️", "Hole", "empty"),
            EmojiReaction("💣", "Bomb", "explosive"),
            EmojiReaction("💤", "Sleep", "zzz")
        )
    }
    
    private fun getDefaultStickerPacks(): List<StickerPack> {
        return listOf(
            StickerPack(
                id = "default",
                name = "Default Pack",
                stickers = listOf(
                    Sticker("thumbs_up", "👍", "Thumbs up"),
                    Sticker("heart", "❤️", "Heart"),
                    Sticker("laugh", "😂", "Laughing"),
                    Sticker("fire", "🔥", "Fire"),
                    Sticker("perfect", "💯", "Perfect")
                )
            ),
            StickerPack(
                id = "filipino",
                name = "Filipino Pack",
                stickers = listOf(
                    Sticker("mano", "🙏", "Mano po"),
                    Sticker("jeepney", "🚌", "Jeepney"),
                    Sticker("rice", "🍚", "Kanin"),
                    Sticker("flag", "🇵🇭", "Pilipinas"),
                    Sticker("heart_flag", "❤️🇵🇭", "Mahal ko Pilipinas")
                )
            )
        )
    }
}

data class EmojiReaction(
    val emoji: String,
    val name: String,
    val category: String
)

data class StickerPack(
    val id: String,
    val name: String,
    val stickers: List<Sticker>
)

data class Sticker(
    val id: String,
    val emoji: String,
    val name: String
)
