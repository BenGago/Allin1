package com.messagehub.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagehub.data.Message
import com.messagehub.data.MessageRepository
import com.messagehub.data.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val messageRepository: MessageRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            val deviceId = messageRepository.getDeviceId()
            _uiState.value = _uiState.value.copy(deviceId = deviceId)
            
            if (deviceId.isNotEmpty()) {
                refreshMessages()
            }
        }
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
            
            val messages = messageRepository.getAllMessages()
            _uiState.value = _uiState.value.copy(
                messages = messages,
                isLoading = false
            )
        }
    }
    
    fun sendReply(originalMessage: Message, replyText: String) {
        viewModelScope.launch {
            val replyMessage = Message(
                id = System.currentTimeMillis().toString(),
                platform = originalMessage.platform,
                sender = "You",
                content = replyText,
                timestamp = System.currentTimeMillis().toString(),
                recipientId = originalMessage.sender
            )
            
            messageRepository.saveMessage(replyMessage)
            refreshMessages()
        }
    }
}
