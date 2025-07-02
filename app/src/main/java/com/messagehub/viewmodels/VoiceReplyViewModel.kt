package com.messagehub.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagehub.features.VoiceReplyAssistant
import com.messagehub.features.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceReplyViewModel @Inject constructor(
    private val voiceReplyAssistant: VoiceReplyAssistant
) : ViewModel() {
    
    val voiceState: StateFlow<VoiceState> = voiceReplyAssistant.voiceState
    val transcribedText: StateFlow<String> = voiceReplyAssistant.transcribedText
    val aiResponse: StateFlow<String> = voiceReplyAssistant.aiResponse
    
    fun startVoiceRecording(onResult: (String) -> Unit) {
        voiceReplyAssistant.startVoiceRecording(onResult)
    }
    
    fun stopVoiceRecording() {
        voiceReplyAssistant.stopVoiceRecording()
    }
    
    override fun onCleared() {
        super.onCleared()
        voiceReplyAssistant.stopVoiceRecording()
    }
}
