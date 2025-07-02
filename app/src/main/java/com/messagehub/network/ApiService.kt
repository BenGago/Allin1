package com.messagehub.network

import com.messagehub.data.Message
import com.messagehub.data.OutgoingSms
import retrofit2.http.*

interface ApiService {
    
    @POST("sms/incoming")
    suspend fun sendIncomingSms(
        @Field("sender") sender: String,
        @Field("message") message: String,
        @Field("timestamp") timestamp: Long,
        @Field("deviceId") deviceId: String
    )
    
    @GET("sms/outgoing")
    suspend fun getOutgoingSms(
        @Query("deviceId") deviceId: String
    ): List<OutgoingSms>
    
    @POST("sms/sent/{id}")
    suspend fun markSmsAsSent(@Path("id") id: String)
    
    @GET("messages")
    suspend fun getMessages(
        @Query("deviceId") deviceId: String
    ): List<Message>
    
    @POST("messages/reply")
    suspend fun sendReply(
        @Field("platform") platform: String,
        @Field("recipient_id") recipientId: String,
        @Field("message") message: String,
        @Field("deviceId") deviceId: String
    )
}
