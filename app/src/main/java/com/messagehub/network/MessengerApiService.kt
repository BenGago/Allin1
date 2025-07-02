package com.messagehub.network

import com.messagehub.integrations.MessengerSendRequest
import com.messagehub.integrations.MessengerSendResponse
import retrofit2.http.*

interface MessengerApiService {
    
    @POST("v18.0/me/messages")
    suspend fun sendMessage(
        @Body request: MessengerSendRequest,
        @Query("access_token") accessToken: String = getPageAccessToken()
    ): MessengerSendResponse
    
    @GET("v18.0/me/conversations")
    suspend fun getConversations(
        @Query("access_token") accessToken: String = getPageAccessToken(),
        @Query("fields") fields: String = "participants,messages{message,from,created_time}"
    ): Any // Define proper response type based on your needs
    
    @GET("v18.0/{conversation-id}/messages")
    suspend fun getMessages(
        @Path("conversation-id") conversationId: String,
        @Query("access_token") accessToken: String = getPageAccessToken(),
        @Query("fields") fields: String = "message,from,created_time"
    ): Any
    
    companion object {
        // In a real app, this would come from secure storage
        private fun getPageAccessToken(): String {
            return "YOUR_FACEBOOK_PAGE_ACCESS_TOKEN"
        }
    }
}
