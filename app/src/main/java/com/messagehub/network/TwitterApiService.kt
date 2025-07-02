package com.messagehub.network

import com.messagehub.integrations.TwitterDirectMessageEventsResponse
import com.messagehub.integrations.TwitterDirectMessageRequest
import com.messagehub.integrations.TwitterDirectMessageResponse
import retrofit2.http.*

interface TwitterApiService {
    
    @GET("1.1/direct_messages/events/list.json")
    suspend fun getDirectMessageEvents(
        @Query("count") count: Int = 50
    ): TwitterDirectMessageEventsResponse
    
    @POST("1.1/direct_messages/events/new.json")
    suspend fun sendDirectMessage(
        @Body request: TwitterDirectMessageRequest
    ): TwitterDirectMessageResponse
    
    @GET("1.1/account/verify_credentials.json")
    suspend fun verifyCredentials(): Any
}
