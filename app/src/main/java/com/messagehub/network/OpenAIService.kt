package com.messagehub.network

import com.google.gson.annotations.SerializedName // ✅ ADDED THIS IMPORT TO FIX THE ERROR
import retrofit2.http.*

interface OpenAIService {

    @POST("v1/chat/completions")
    suspend fun generateResponse(
        @Body request: OpenAIRequest,
        // ⚠️ Security Warning: Do not hardcode API keys in production apps.
        @Header("Authorization") authorization: String = "Bearer ${getApiKey()}"
    ): OpenAIResponse

    companion object {
        private fun getApiKey(): String {
            return "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" // This should be stored securely
        }
    }
}

// Extension function for easier use
suspend fun OpenAIService.generateResponse(prompt: String): OpenAIResponse {
    val request = OpenAIRequest(
        model = "gpt-3.5-turbo",
        messages = listOf(
            OpenAIMessage(
                role = "user",
                content = prompt
            )
        ),
        maxTokens = 150,
        temperature = 0.7
    )

    return generateResponse(request)
}

// --- Data Classes ---

data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    @SerializedName("max_tokens") val maxTokens: Int?, // Made nullable for safety
    val temperature: Double? // Made nullable for safety
)

data class OpenAIMessage(
    val role: String,
    val content: String
)

data class OpenAIResponse(
    val choices: List<OpenAIChoice>?
)

data class OpenAIChoice(
    val message: OpenAIMessage?
)
