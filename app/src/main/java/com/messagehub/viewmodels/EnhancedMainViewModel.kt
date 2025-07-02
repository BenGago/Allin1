package com.messagehub.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagehub.data.Message
import com.messagehub.data.MessageRepository
import com.messagehub.data.UiState
import com.messagehub.data.User
import com.messagehub.features.MessageQueueProcessor
import com.messagehub.features.RealTimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EnhancedMainViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val messageQueueProcessor: MessageQueueProcessor,
    private val realTimeManager: RealTimeManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = combine(
        _uiState,
        realTimeManager.incomingMessages,
        realTimeManager.typingUsers,
        realTimeManager.onlineUsers
    ) { state, incomingMessages, typingUsers, onlineUsers ->
        state.copy(
            messages = state.messages + incomingMessages,
            typingUsers = typingUsers,
            onlineUsers = onlineUsers
        )
    }.asStateFlow()
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            val deviceId = messageRepository.getDeviceId()
            _uiState.value = _uiState.value.copy(deviceId = deviceId)
            
            if (deviceId.isNotEmpty()) {
                refreshMessages()
                loadCurrentUser()
            }
        }
    }
    
    private fun loadCurrentUser() {
        viewModelScope.launch {
            // Load current user from preferences or create default
            val deviceId = messageRepository.getDeviceId()
            val user = User(
                id = deviceId,
                username = "user_$deviceId",
                displayName = "User",
                platform = "messagehub",
                isOnline = true,
                lastSeen = System.currentTimeMillis()
            )
            _currentUser.value = user
            
            // Update user status in real-time manager
            realTimeManager.updateUserStatus(user, true)
        }
    }
    
    fun updateUserProfile(username: String, displayName: String, profilePicture: String? = null) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch
            val updatedUser = currentUser.copy(
                username = username,
                displayName = displayName,
                profilePicture = profilePicture
            )
            
            _currentUser.value = updatedUser
            realTimeManager.updateUserStatus(updatedUser, true)
            
            // Save to preferences
            saveUserToPreferences(updatedUser)
        }
    }
    
    fun switchUser(newUser: User) {
        viewModelScope.launch {
            // Set previous user offline
            _currentUser.value?.let { oldUser ->
                realTimeManager.updateUserStatus(oldUser, false)
            }
            
            // Set new user online
            _currentUser.value = newUser
            realTimeManager.updateUserStatus(newUser, true)
            
            // Update device ID and refresh data
            messageRepository.saveDeviceId(newUser.id)
            _uiState.value = _uiState.value.copy(deviceId = newUser.id)
            
            refreshMessages()
            saveUserToPreferences(newUser)
        }
    }
    
    fun createNewUser(username: String, displayName: String): User {
        val newUser = User(
            id = "user_${System.currentTimeMillis()}",
            username = username,
            displayName = displayName,
            platform = "messagehub",
            isOnline = true,
            lastSeen = System.currentTimeMillis()
        )
        
        viewModelScope.launch {
            switchUser(newUser)
        }
        
        return newUser
    }
    
    fun saveDeviceId(deviceId: String) {
        viewModelScope.launch {
            messageRepository.saveDeviceId(deviceId)
            _uiState.value = _uiState.value.copy(deviceId = deviceId)
            refreshMessages()
        }
    }
    
    fun refreshMessages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val messages = messageRepository.getAllMessages()
                _uiState.value = _uiState.value.copy(
                    messages = messages,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun sendReply(originalMessage: Message, replyText: String) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch
            
            val replyMessage = Message(
                id = System.currentTimeMillis().toString(),
                platform = originalMessage.platform,
                sender = currentUser.displayName,
                content = replyText,
                timestamp = System.currentTimeMillis().toString(),
                recipientId = originalMessage.recipientId ?: originalMessage.sender
            )
            
            // Queue the message for processing
            messageQueueProcessor.queueOutgoingMessage(replyMessage)
            
            // Add to local messages immediately for UI feedback
            val currentMessages = _uiState.value.messages.toMutableList()
            currentMessages.add(0, replyMessage)
            _uiState.value = _uiState.value.copy(messages = currentMessages)
        }
    }
    
    fun setTypingIndicator(chatId: String, isTyping: Boolean) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch
            realTimeManager.setTypingIndicator(currentUser.id, chatId, isTyping)
        }
    }
    
    fun getQueueStats() {
        viewModelScope.launch {
            val stats = messageQueueProcessor.getQueueStats()
            _uiState.value = _uiState.value.copy(queueStats = stats)
        }
    }
    
    private fun saveUserToPreferences(user: User) {
        // Save user data to SharedPreferences
        // This would be implemented with actual SharedPreferences calls
    }
    
    override fun onCleared() {
        super.onCleared()
        // Set user offline when ViewModel is cleared
        viewModelScope.launch {
            _currentUser.value?.let { user ->
                realTimeManager.updateUserStatus(user, false)
            }
        }
    }
}
