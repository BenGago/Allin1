package com.messagehub.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagehub.data.EnhancedMessage
import com.messagehub.features.AIPersonalityClone
import com.messagehub.features.FlirtDetector
import com.messagehub.features.MoodDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodViewModel @Inject constructor(
    private val moodDetector: MoodDetector,
    private val flirtDetector: FlirtDetector,
    private val aiPersonalityClone: AIPersonalityClone
) : ViewModel() {
    
    val currentMood = moodDetector.currentMood
    val sextModeEnabled = moodDetector.sextModeEnabled
    val flirtScore = flirtDetector.flirtScore
    val crushProbability = flirtDetector.crushProbability
    val flirtAnalysis = flirtDetector.flirtAnalysis
    val cloneEnabled = aiPersonalityClone.cloneEnabled
    val trainingProgress = aiPersonalityClone.trainingProgress
    val personalityProfile = aiPersonalityClone.personalityProfile
    
    fun analyzeMessage(message: EnhancedMessage) {
        viewModelScope.launch {
            // Detect mood
            moodDetector.detectMood(message)
            
            // Analyze flirt level
            flirtDetector.analyzeFlirtLevel(message)
            
            // Add to AI training data
            aiPersonalityClone.addTrainingMessage(message)
        }
    }
    
    fun setSextModeEnabled(enabled: Boolean) {
        moodDetector.setSextModeEnabled(enabled)
    }
    
    fun setCloneEnabled(enabled: Boolean) {
        aiPersonalityClone.setCloneEnabled(enabled)
    }
    
    suspend fun generateMoodBasedResponse(message: EnhancedMessage, userInput: String): String {
        val mood = moodDetector.detectMood(message)
        
        return if (cloneEnabled.value) {
            aiPersonalityClone.generateClonedResponseAsync(message, userInput)
        } else {
            moodDetector.getMoodBasedResponse(mood, userInput)
        }
    }
}
