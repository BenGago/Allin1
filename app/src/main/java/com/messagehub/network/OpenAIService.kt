package com.messagehub.network

import retrofit2.http.*

interface OpenAIService {
    
    @POST("v1/chat/completions")
    suspend fun generateResponse(
        @Body request: OpenAIRequest,
        @Header("Authorization") authorization: String = "Bearer ${getApiKey()}"
    ): OpenAIResponse
    
    companion object {
        private fun getApiKey(): String {
            return "your_openai_api_key" // Store securely in production
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

data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    @SerializedName("max_tokens") val maxTokens: Int,
    val temperature: Double
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
