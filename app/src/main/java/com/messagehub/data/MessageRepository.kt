package com.messagehub.data

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SmsManager
import android.util.Log
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.messagehub.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val userId: String, // Added for user separation
    val platform: String,
    val sender: String,
    val content: String,
    val timestamp: String,
    val recipientId: String? = null,
    val messageType: String = "text",
    val attachments: String = "", // JSON string
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val replyToId: String? = null,
    val reactions: String = "", // JSON string
    val metadata: String = "" // JSON string
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val userId: String, // Added for user separation
    val platform: String,
    val participants: String, // JSON string
    val lastMessageId: String? = null,
    val unreadCount: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val platform: String,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0,
    val profilePicture: String? = null,
    val encryptionKey: String? = null,
    val platformCredentials: String = "", // JSON string
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getMessagesByUser(userId: String): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE userId = :userId AND platform = :platform ORDER BY timestamp DESC")
    suspend fun getMessagesByUserAndPlatform(userId: String, platform: String): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE id = :id AND userId = :userId")
    suspend fun getMessageById(id: String, userId: String): MessageEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE platform = :platform AND userId = :userId")
    suspend fun deleteMessagesByUserAndPlatform(userId: String, platform: String)
    
    @Query("UPDATE messages SET isRead = 1 WHERE id = :messageId AND userId = :userId")
    suspend fun markAsRead(messageId: String, userId: String)
    
    @Query("SELECT COUNT(*) FROM messages WHERE userId = :userId AND isRead = 0")
    suspend fun getUnreadCount(userId: String): Int
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getChatsByUser(userId: String): List<ChatEntity>
    
    @Query("SELECT * FROM chats WHERE userId = :userId AND platform = :platform")
    suspend fun getChatsByUserAndPlatform(userId: String, platform: String): List<ChatEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)
    
    @Delete
    suspend fun deleteChat(chat: ChatEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY lastSeen DESC")
    suspend fun getAllUsers(): List<UserEntity>
    
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE isOnline = 1")
    suspend fun getOnlineUsers(): List<UserEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Update
    suspend fun updateUser(user: UserEntity)
    
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    @Query("UPDATE users SET isOnline = :isOnline, lastSeen = :lastSeen WHERE id = :userId")
    suspend fun updateUserStatus(userId: String, isOnline: Boolean, lastSeen: Long)
}

@Database(
    entities = [MessageEntity::class, ChatEntity::class, UserEntity::class],
    version = 2, // Incremented for schema changes
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun userDao(): UserDao
}

class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else json.decodeFromString(value)
    }
    
    @TypeConverter
    fun fromStringMap(value: Map<String, String>): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        return if (value.isEmpty()) emptyMap() else json.decodeFromString(value)
    }
}

@Singleton
class MessageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "message_hub_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val database: MessageDatabase by lazy {
        Room.databaseBuilder(
            context,
            MessageDatabase::class.java,
            "message_database"
        )
        .addMigrations(MIGRATION_1_2)
        .build()
    }
    
    private val messageDao = database.messageDao()
    private val chatDao = database.chatDao()
    private val userDao = database.userDao()
    
    private val smsManager = SmsManager.getDefault()
    
    companion object {
        private const val TAG = "MessageRepository"
        private const val CURRENT_USER_KEY = "current_user_id"
        private const val DEVICE_ID_KEY = "device_id"
        
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add userId column to messages table
                database.execSQL("ALTER TABLE messages ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                
                // Add userId column to chats table
                database.execSQL("ALTER TABLE chats ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                
                // Create users table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS users (
                        id TEXT PRIMARY KEY NOT NULL,
                        username TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        platform TEXT NOT NULL,
                        isOnline INTEGER NOT NULL DEFAULT 0,
                        lastSeen INTEGER NOT NULL DEFAULT 0,
                        profilePicture TEXT,
                        encryptionKey TEXT,
                        platformCredentials TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }
    }
    
    // User Management
    fun getCurrentUserId(): String {
        return encryptedPrefs.getString(CURRENT_USER_KEY, "") ?: ""
    }
    
    fun setCurrentUserId(userId: String) {
        encryptedPrefs.edit().putString(CURRENT_USER_KEY, userId).apply()
    }
    
    fun getDeviceId(): String {
        return encryptedPrefs.getString(DEVICE_ID_KEY, "") ?: ""
    }
    
    fun saveDeviceId(deviceId: String) {
        encryptedPrefs.edit().putString(DEVICE_ID_KEY, deviceId).apply()
    }
    
    // User CRUD Operations
    suspend fun createUser(user: User): Boolean = withContext(Dispatchers.IO) {
        try {
            val userEntity = user.toEntity()
            userDao.insertUser(userEntity)
            setCurrentUserId(user.id)
            Log.d(TAG, "User created: ${user.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user", e)
            false
        }
    }
    
    suspend fun getAllUsers(): List<User> = withContext(Dispatchers.IO) {
        try {
            userDao.getAllUsers().map { it.toUser() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all users", e)
            emptyList()
        }
    }
    
    suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        try {
            userDao.getUserById(userId)?.toUser()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by ID", e)
            null
        }
    }
    
    suspend fun updateUser(user: User): Boolean = withContext(Dispatchers.IO) {
        try {
            userDao.updateUser(user.toEntity())
            Log.d(TAG, "User updated: ${user.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user", e)
            false
        }
    }
    
    suspend fun updateUserStatus(userId: String, isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            userDao.updateUserStatus(userId, isOnline, System.currentTimeMillis())
            Log.d(TAG, "User status updated: $userId = $isOnline")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user status", e)
            false
        }
    }
    
    suspend fun getOnlineUsers(): List<User> = withContext(Dispatchers.IO) {
        try {
            userDao.getOnlineUsers().map { it.toUser() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting online users", e)
            emptyList()
        }
    }
    
    // Message Operations (User-Specific)
    suspend fun getMessagesForCurrentUser(): List<Message> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            if (currentUserId.isEmpty()) {
                Log.w(TAG, "No current user set")
                return@withContext emptyList()
            }
            
            messageDao.getMessagesByUser(currentUserId).map { it.toMessage() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting messages for current user", e)
            emptyList()
        }
    }
    
    suspend fun getMessagesByUserAndPlatform(userId: String, platform: String): List<Message> = withContext(Dispatchers.IO) {
        try {
            messageDao.getMessagesByUserAndPlatform(userId, platform).map { it.toMessage() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting messages by user and platform", e)
            emptyList()
        }
    }
    
    suspend fun insertMessage(message: Message): Boolean = withContext(Dispatchers.IO) {
        try {
            messageDao.insertMessage(message.toEntity())
            Log.d(TAG, "Message inserted: ${message.id} for user: ${message.userId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting message", e)
            false
        }
    }
    
    suspend fun insertMessages(messages: List<Message>): Boolean = withContext(Dispatchers.IO) {
        try {
            messageDao.insertMessages(messages.map { it.toEntity() })
            Log.d(TAG, "Inserted ${messages.size} messages")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting messages", e)
            false
        }
    }
    
    suspend fun deleteMessage(message: Message): Boolean = withContext(Dispatchers.IO) {
        try {
            messageDao.deleteMessage(message.toEntity())
            Log.d(TAG, "Message deleted: ${message.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message", e)
            false
        }
    }
    
    suspend fun markAsRead(messageId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            messageDao.markAsRead(messageId, userId)
            Log.d(TAG, "Message marked as read: $messageId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error marking message as read", e)
            false
        }
    }
    
    suspend fun getUnreadCount(userId: String): Int = withContext(Dispatchers.IO) {
        try {
            messageDao.getUnreadCount(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unread count", e)
            0
        }
    }
    
    // Chat Operations (User-Specific)
    suspend fun getChatsForCurrentUser(): List<Chat> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            if (currentUserId.isEmpty()) {
                Log.w(TAG, "No current user set")
                return@withContext emptyList()
            }
            
            chatDao.getChatsByUser(currentUserId).map { it.toChat() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chats for current user", e)
            emptyList()
        }
    }
    
    suspend fun insertChat(chat: Chat): Boolean = withContext(Dispatchers.IO) {
        try {
            chatDao.insertChat(chat.toEntity())
            Log.d(TAG, "Chat inserted: ${chat.id} for user: ${chat.userId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting chat", e)
            false
        }
    }
    
    // SMS Operations
    suspend fun sendSms(recipient: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            smsManager.sendTextMessage(recipient, null, message, null, null)
            Log.d(TAG, "SMS sent to $recipient")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            false
        }
    }
    
    // Platform Credentials Management
    suspend fun savePlatformCredentials(userId: String, platform: String, credentials: PlatformCredential): Boolean {
        return try {
            val user = getUserById(userId)
            if (user != null) {
                val updatedCredentials = user.platformCredentials.toMutableMap()
                updatedCredentials[platform] = credentials
                val updatedUser = user.copy(platformCredentials = updatedCredentials)
                updateUser(updatedUser)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving platform credentials", e)
            false
        }
    }
    
    suspend fun getPlatformCredentials(userId: String, platform: String): PlatformCredential? {
        return try {
            getUserById(userId)?.platformCredentials?.get(platform)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting platform credentials", e)
            null
        }
    }
}

// Extension functions for conversion
private fun MessageEntity.toMessage(): Message {
    val json = Json { ignoreUnknownKeys = true }
    return Message(
        id = id,
        userId = userId,
        platform = platform,
        sender = sender,
        content = content,
        timestamp = timestamp,
        recipientId = recipientId,
        messageType = messageType,
        attachments = if (attachments.isEmpty()) emptyList() else json.decodeFromString(attachments),
        isRead = isRead,
        isDelivered = isDelivered,
        replyToId = replyToId,
        reactions = emptyList(), // Would need JSON parsing for reactions
        metadata = if (metadata.isEmpty()) null else json.decodeFromString(metadata)
    )
}

private fun Message.toEntity(): MessageEntity {
    val json = Json { ignoreUnknownKeys = true }
    return MessageEntity(
        id = id,
        userId = userId,
        platform = platform,
        sender = sender,
        content = content,
        timestamp = timestamp,
        recipientId = recipientId,
        messageType = messageType,
        attachments = json.encodeToString(attachments),
        isRead = isRead,
        isDelivered = isDelivered,
        replyToId = replyToId,
        reactions = "", // Would need JSON serialization for reactions
        metadata = json.encodeToString(metadata ?: emptyMap())
    )
}

private fun ChatEntity.toChat(): Chat {
    val json = Json { ignoreUnknownKeys = true }
    return Chat(
        id = id,
        userId = userId,
        platform = platform,
        participants = if (participants.isEmpty()) emptyList() else json.decodeFromString(participants),
        lastMessage = null, // Would need to fetch from messages table
        unreadCount = unreadCount,
        isArchived = isArchived,
        createdAt = createdAt
    )
}

private fun Chat.toEntity(): ChatEntity {
    val json = Json { ignoreUnknownKeys = true }
    return ChatEntity(
        id = id,
        userId = userId,
        platform = platform,
        participants = json.encodeToString(participants),
        lastMessageId = lastMessage?.id,
        unreadCount = unreadCount,
        isArchived = isArchived,
        createdAt = createdAt
    )
}

private fun UserEntity.toUser(): User {
    val json = Json { ignoreUnknownKeys = true }
    return User(
        id = id,
        username = username,
        displayName = displayName,
        platform = platform,
        isOnline = isOnline,
        lastSeen = lastSeen,
        profilePicture = profilePicture,
        encryptionKey = encryptionKey,
        platformCredentials = if (platformCredentials.isEmpty()) emptyMap() else json.decodeFromString(platformCredentials)
    )
}

private fun User.toEntity(): UserEntity {
    val json = Json { ignoreUnknownKeys = true }
    return UserEntity(
        id = id,
        username = username,
        displayName = displayName,
        platform = platform,
        isOnline = isOnline,
        lastSeen = lastSeen,
        profilePicture = profilePicture,
        encryptionKey = encryptionKey,
        platformCredentials = json.encodeToString(platformCredentials),
        createdAt = System.currentTimeMillis()
    )
}
