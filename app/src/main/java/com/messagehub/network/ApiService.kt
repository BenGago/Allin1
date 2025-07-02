package com.messagehub.network

import com.messagehub.data.Message
import com.messagehub.data.OutgoingSms
import retrofit2.http.*

interface ApiService {
    
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
