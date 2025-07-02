package com.messagehub.network

import com.messagehub.integrations.TelegramResponse
import com.messagehub.integrations.TelegramUpdate
import retrofit2.http.*

interface TelegramApiService {
    
    @GET("bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token") token: String = getBotToken(),
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("timeout") timeout: Int = 0
    ): TelegramResponse<List<TelegramUpdate>>
    
    @POST("bot{token}/sendMessage")
    @FormUrlEncoded
    suspend fun sendMessage(
        @Path("token") token: String = getBotToken(),
        @Field("chat_id") chatId: String,
        @Field("text") text: String
    ): TelegramResponse<Any>
    
    @GET("bot{token}/getMe")
    suspend fun getMe(
        @Path("token") token: String = getBotToken()
    ): TelegramResponse<Any>
    
    companion object {
        // In a real app, this would come from secure storage or environment
        private fun getBotToken(): String {
            return "YOUR_TELEGRAM_BOT_TOKEN"
        }
    }
}
