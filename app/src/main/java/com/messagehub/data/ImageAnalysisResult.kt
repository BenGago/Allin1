package com.messagehub.data

data class ImageAnalysisResult(
    val faceCount: Int = 0,
    val hasSmiling: Boolean = false,
    val detectedText: String = "",
    val isMeme: Boolean = false,
    val labels: List<String> = emptyList(),
    val isNSFW: Boolean = false,
    val suggestedResponse: String = "",
    val confidence: Float = 0f,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)
