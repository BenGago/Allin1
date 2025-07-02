package com.messagehub.features

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.messagehub.data.EnhancedMessage
import com.messagehub.network.OpenAIService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceReplyAssistant @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAIService: OpenAIService
) {
    
    companion object {
        private const val TAG = "VoiceReplyAssistant"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()
    
    private val _aiResponse = MutableStateFlow("")
    val aiResponse: StateFlow<String> = _aiResponse.asStateFlow()
    
    fun startVoiceRecording(onResult: (String) -> Unit) {
        if (!hasAudioPermission()) {
            Log.e(TAG, "Audio permission not granted")
            return
        }
        
        _voiceState.value = VoiceState.LISTENING
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech")
                    _voiceState.value = VoiceState.RECORDING
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Update voice level indicator
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                    _voiceState.value = VoiceState.PROCESSING
                }
                
                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: $error")
                    _voiceState.value = VoiceState.ERROR
                    handleSpeechError(error)
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcribedText = matches?.firstOrNull() ?: ""
                    
                    Log.d(TAG, "Transcribed: $transcribedText")
                    _transcribedText.value = transcribedText
                    
                    if (transcribedText.isNotEmpty()) {
                        processVoiceInput(transcribedText, onResult)
                    } else {
                        _voiceState.value = VoiceState.IDLE
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull() ?: ""
                    _transcribedText.value = partialText
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        speechRecognizer?.startListening(intent)
    }
    
    private fun processVoiceInput(transcribedText: String, onResult: (String) -> Unit) {
        scope.launch {
            try {
                _voiceState.value = VoiceState.GENERATING_AI_RESPONSE
                
                val aiResponse = generateAIResponse(transcribedText)
                _aiResponse.value = aiResponse
                
                _voiceState.value = VoiceState.COMPLETED
                onResult(aiResponse)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice input", e)
                _voiceState.value = VoiceState.ERROR
            }
        }
    }
    
    private suspend fun generateAIResponse(userInput: String): String {
        return try {
            val prompt = buildAIPrompt(userInput)
            val response = openAIService.generateResponse(prompt)
            
            response.choices?.firstOrNull()?.message?.content?.trim() 
                ?: "I heard you, but I'm not sure how to respond to that."
        } catch (e: Exception) {
            Log.e(TAG, "AI response generation failed", e)
            "Sorry, I couldn't generate a response right now."
        }
    }
    
    private fun buildAIPrompt(userInput: String): String {
        return """
            You are a helpful voice assistant for messaging. The user said: "$userInput"
            
            Generate a natural, conversational response that would be appropriate to send as a text message.
            Keep it:
            - Concise (under 100 words)
            - Natural and friendly
            - Contextually appropriate
            - Ready to send as-is
            
            Response:
        """.trimIndent()
    }
    
    fun stopVoiceRecording() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _voiceState.value = VoiceState.IDLE
    }
    
    private fun handleSpeechError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech input matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
        Log.e(TAG, "Speech error: $errorMessage")
    }
    
    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // Smart context-aware responses
    fun generateContextualResponse(
        originalMessage: EnhancedMessage,
        voiceInput: String,
        onResult: (String) -> Unit
    ) {
        scope.launch {
            try {
                _voiceState.value = VoiceState.GENERATING_AI_RESPONSE
                
                val contextualPrompt = """
                    Original message from ${originalMessage.sender}: "${originalMessage.content}"
                    Platform: ${originalMessage.platform}
                    
                    User's voice input for reply: "$voiceInput"
                    
                    Generate an appropriate response that:
                    - Responds to the original message context
                    - Incorporates the user's voice input intent
                    - Matches the platform's communication style
                    - Is natural and conversational
                    
                    Response:
                """.trimIndent()
                
                val response = openAIService.generateResponse(contextualPrompt)
                val aiResponse = response.choices?.firstOrNull()?.message?.content?.trim()
                    ?: "Got it, let me respond to that."
                
                _aiResponse.value = aiResponse
                _voiceState.value = VoiceState.COMPLETED
                onResult(aiResponse)
                
            } catch (e: Exception) {
                Log.e(TAG, "Contextual response generation failed", e)
                _voiceState.value = VoiceState.ERROR
            }
        }
    }
}

enum class VoiceState {
    IDLE,
    LISTENING,
    RECORDING,
    PROCESSING,
    GENERATING_AI_RESPONSE,
    COMPLETED,
    ERROR
}
