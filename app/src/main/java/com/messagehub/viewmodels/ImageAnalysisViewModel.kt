package com.messagehub.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagehub.data.Attachment
import com.messagehub.data.ImageAnalysisResult
import com.messagehub.features.ImageAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageAnalysisViewModel @Inject constructor(
    private val imageAnalyzer: ImageAnalyzer
) : ViewModel() {
    
    private val _analysisResults = MutableStateFlow<Map<String, ImageAnalysisResult>>(emptyMap())
    private val analysisResults: StateFlow<Map<String, ImageAnalysisResult>> = _analysisResults.asStateFlow()
    
    fun analyzeImage(messageId: String, attachment: Attachment) {
        viewModelScope.launch {
            try {
                val result = imageAnalyzer.analyzeImage(attachment)
                val currentResults = _analysisResults.value.toMutableMap()
                currentResults[messageId] = result
                _analysisResults.value = currentResults
            } catch (e: Exception) {
                val errorResult = ImageAnalysisResult(
                    hasError = true,
                    errorMessage = e.message
                )
                val currentResults = _analysisResults.value.toMutableMap()
                currentResults[messageId] = errorResult
                _analysisResults.value = currentResults
            }
        }
    }
    
    fun getAnalysisForMessage(messageId: String): StateFlow<ImageAnalysisResult?> {
        val resultFlow = MutableStateFlow<ImageAnalysisResult?>(null)
        
        viewModelScope.launch {
            analysisResults.collect { results ->
                resultFlow.value = results[messageId]
            }
        }
        
        return resultFlow.asStateFlow()
    }
}
