package com.messagehub.network

import com.messagehub.data.*
import retrofit2.http.*

interface ApiService {
    
    @FormUrlEncoded
    @POST("device/register")
    suspend fun registerDevice(
        @Field("deviceId") deviceId: String,
        @Field("deviceName") deviceName: String,
        @Field("fcmToken") fcmToken: String
    )
    
    @FormUrlEncoded
    @POST("device/update-token")
    suspend fun updateFCMToken(
        @Field("deviceId") deviceId: String,
        @Field("fcmToken") fcmToken: String
    )
    
    @FormUrlEncoded
    @POST("sms/incoming")
    suspend fun sendIncomingSms(
        @Field("sender") sender: String,
        @Field("message") message: String,
        @Field("timestamp") timestamp: Long,
        @Field("deviceId") deviceId: String
    )
    
    @FormUrlEncoded
    @POST("sms/incoming")
    suspend fun sendIncomingMessage(
        @Field("platform") platform: String,
        @Field("sender") sender: String,
        @Field("message") message: String,
        @Field("timestamp") timestamp: Long,
        @Field("deviceId") deviceId: String,
        @Field("messageType") messageType: String = "text",
        @Field("filePath") filePath: String? = null,
        @Field("metadata") metadata: String? = null
    )
    
    @GET("sms/outgoing")
    suspend fun getOutgoingSms(
        @Query("deviceId") deviceId: String
    ): List<OutgoingSms>
    
    @POST("sms/sent/{id}")
    suspend fun markSmsAsSent(@Path("id") id: String)
    
    @GET("messages")
    suspend fun getMessages(
        @Query("deviceId") deviceId: String,
        @Query("platform") platform: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): List<Message>
    
    @FormUrlEncoded
    @POST("messages/reply")
    suspend fun sendReply(
        @Field("platform") platform: String,
        @Field("recipientId") recipientId: String,
        @Field("message") message: String,
        @Field("deviceId") deviceId: String,
        @Field("messageType") messageType: String = "text"
    )
    
    @FormUrlEncoded
    @POST("ai/voice
    )
    
    @FormUrlEncoded
    @POST("ai/voice-reply")
    suspend fun getAIVoiceReply(
        @Field("message") message: String,
        @Field("deviceId") deviceId: String,
        @Field("context") context: String? = null,
        @Field("mood") mood: String? = null
    ): AIVoiceReplyResponse
    
    @FormUrlEncoded
    @POST("ai/detect-mood")
    suspend fun detectMood(
        @Field("message") message: String,
        @Field("deviceId") deviceId: String
    ): MoodResult
    
    @FormUrlEncoded
    @POST("notifications/test")
    suspend fun sendTestNotification(
        @Field("deviceId") deviceId: String,
        @Field("title") title: String,
        @Field("body") body: String
    ): NotificationResponse
}
