package com.messagehub.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagehub.data.Message
import com.messagehub.data.MessageRepository
import com.messagehub.data.UiState
import com.messagehub.network.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val apiService: ApiService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = combine(
        _uiState,
        messageRepository.messages
    ) { state, messages ->
        state.copy(
            messages = messages,
            deviceId = messageRepository.getDeviceId()
        )
    }.asStateFlow()
    
    fun saveDeviceId(deviceId: String) {
        messageRepository.saveDeviceId(deviceId)
        _uiState.value = _uiState.value.copy(deviceId = deviceId)
    }
    
    fun refreshMessages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val deviceId = messageRepository.getDeviceId()
                if (deviceId.isNotEmpty()) {
                    val messages = apiService.getMessages(deviceId)
                    messageRepository.saveMessages(messages)
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun sendReply(message: Message, reply: String) {
        viewModelScope.launch {
            try {
                val deviceId = messageRepository.getDeviceId()
                apiService.sendReply(
                    platform = message.platform,
                    recipientId = message.recipientId ?: message.sender,
                    message = reply,
                    deviceId = deviceId
                )
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
