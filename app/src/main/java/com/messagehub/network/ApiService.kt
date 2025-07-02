package com.messagehub.network

import com.messagehub.auth.AuthResponse
import com.messagehub.auth.LoginRequest
import com.messagehub.auth.RegistrationRequest
import com.messagehub.auth.UpdateProfileRequest
import com.messagehub.data.Message
import com.messagehub.data.OutgoingSms
import com.messagehub.data.PlatformCredential
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    // Authentication endpoints
    @POST("auth/register")
    suspend fun registerUser(@Body request: RegistrationRequest): AuthResponse
    
    @POST("auth/login")
    suspend fun loginUser(@Body request: LoginRequest): AuthResponse
    
    @PUT("auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") authToken: String,
        @Body request: UpdateProfileRequest
    ): AuthResponse
    
    @DELETE("auth/account")
    suspend fun deleteAccount(@Header("Authorization") authToken: String): AuthResponse
    
    @GET("auth/verify")
    suspend fun verifyToken(@Header("Authorization") authToken: String): AuthResponse
    
    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") authToken: String): Response<Unit>
    
    // Message endpoints
    @GET("messages")
    suspend fun getMessages(
        @Header("Authorization") authToken: String,
        @Query("platform") platform: String? = null,
        @Query("chatId") chatId: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("unreadOnly") unreadOnly: Boolean = false
    ): MessageResponse
    
    @POST("messages/send")
    suspend fun sendMessage(
        @Header("Authorization") authToken: String,
        @Body message: SendMessageRequest
    ): SendMessageResponse
    
    @PUT("messages/read")
    suspend fun markMessagesAsRead(
        @Header("Authorization") authToken: String,
        @Body request: MarkAsReadRequest
    ): Response<Unit>
    
    @POST("messages/{messageId}/reactions")
    suspend fun addReaction(
        @Header("Authorization") authToken: String,
        @Path("messageId") messageId: String,
        @Body request: AddReactionRequest
    ): Response<Unit>
    
    @GET("messages/stats")
    suspend fun getMessageStats(
        @Header("Authorization") authToken: String
    ): MessageStatsResponse
    
    // Platform endpoints
    @GET("platforms")
    suspend fun getPlatforms(
        @Header("Authorization") authToken: String
    ): PlatformResponse
    
    @POST("platforms/{platform}/credentials")
    suspend fun savePlatformCredentials(
        @Header("Authorization") authToken: String,
        @Path("platform") platform: String,
        @Body credentials: PlatformCredential
    ): Response<Unit>
    
    @PUT("platforms/{platform}/toggle")
    suspend fun togglePlatform(
        @Header("Authorization") authToken: String,
        @Path("platform") platform: String,
        @Body request: TogglePlatformRequest
    ): Response<Unit>
    
    @DELETE("platforms/{platform}")
    suspend fun deletePlatform(
        @Header("Authorization") authToken: String,
        @Path("platform") platform: String
    ): Response<Unit>
    
    @POST("platforms/{platform}/test")
    suspend fun testPlatform(
        @Header("Authorization") authToken: String,
        @Path("platform") platform: String
    ): TestPlatformResponse
    
    // Legacy endpoints (for backward compatibility)
    @GET("messages/{deviceId}")
    suspend fun getMessages(@Path("deviceId") deviceId: String): List<Message>
    
    @POST("messages/sms/incoming")
    @FormUrlEncoded
    suspend fun sendIncomingSms(
        @Field("sender") sender: String,
        @Field("message") message: String,
        @Field("timestamp") timestamp: Long,
        @Field("deviceId") deviceId: String
    ): Any
    
    @GET("messages/sms/outgoing/{deviceId}")
    suspend fun getOutgoingSms(@Path("deviceId") deviceId: String): List<OutgoingSms>
    
    @POST("messages/sms/sent/{id}")
    suspend fun markSmsAsSent(@Path("id") id: String): Any
    
    @POST("messages")
    suspend fun sendMessage(@Body message: Message): Any
    
    @GET("device/{deviceId}/status")
    suspend fun getDeviceStatus(@Path("deviceId") deviceId: String): Any
}

// Data classes for API requests/responses
data class MessageResponse(
    val success: Boolean,
    val messages: List<Message>,
    val pagination: PaginationInfo,
    val unreadCount: Int
)

data class PaginationInfo(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean
)

data class SendMessageRequest(
    val platform: String,
    val recipient: String,
    val content: String,
    val messageType: String = "text",
    val replyToId: String? = null,
    val attachments: List<String> = emptyList()
)

data class SendMessageResponse(
    val success: Boolean,
    val message: String,
    val messageId: String,
    val platformMessageId: String,
    val isSent: Boolean,
    val timestamp: String
)

data class MarkAsReadRequest(
    val messageIds: List<String>
)

data class AddReactionRequest(
    val emoji: String
)

data class MessageStatsResponse(
    val success: Boolean,
    val stats: Map<String, PlatformStats>
)

data class PlatformStats(
    val totalMessages: Int,
    val unreadCount: Int,
    val sentCount: Int,
    val receivedCount: Int
)

data class PlatformResponse(
    val success: Boolean,
    val platforms: Map<String, PlatformInfo>
)

data class PlatformInfo(
    val platform: String,
    val isEnabled: Boolean,
    val lastSyncTime: String,
    val hasCredentials: Boolean
)

data class TogglePlatformRequest(
    val enabled: Boolean
)

data class TestPlatformResponse(
    val success: Boolean,
    val message: String,
    val info: Map<String, String>?
)
