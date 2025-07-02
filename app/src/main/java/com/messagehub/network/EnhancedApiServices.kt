package com.messagehub.network

import com.messagehub.integrations.*
import retrofit2.http.*

interface TelegramApiService {
    
    @POST("bot{token}/sendMessage")
    @FormUrlEncoded
    suspend fun sendMessage(
        @Path("token") token: String = getBotToken(),
        @Field("chat_id") chatId: String,
        @Field("text") text: String,
        @Field("reply_to_message_id") replyToMessageId: Int? = null,
        @Field("parse_mode") parseMode: String? = null
    ): TelegramResponse<Any>
    
    @POST("bot{token}/sendPhoto")
    @FormUrlEncoded
    suspend fun sendPhoto(
        @Path("token") token: String = getBotToken(),
        @Field("chat_id") chatId: String,
        @Field("photo") photo: String,
        @Field("caption") caption: String? = null
    ): TelegramResponse<Any>
    
    @POST("bot{token}/sendVideo")
    @FormUrlEncoded
    suspend fun sendVideo(
        @Path("token") token: String = getBotToken(),
        @Field("chat_id") chatId: String,
        @Field("video") video: String,
        @Field("caption") caption: String? = null
    ): TelegramResponse<Any>
    
    @POST("bot{token}/sendAudio")
    @FormUrlEncoded
    suspend fun sendAudio(
        @Path("token") token: String = getBotToken(),
        @Field("chat_id") chatId: String,
        @Field("audio") audio: String,
        @Field("caption") caption: String? = null
    ): TelegramResponse<Any>
    
    @POST("bot{token}/sendDocument")
    @FormUrlEncoded
    suspend fun sendDocument(
        @Path("token") token: String = getBotToken(),
        @Field("chat_id") chatId: String,
        @Field("document") document: String,
        @Field("caption") caption: String? = null
    ): TelegramResponse<Any>
    
    @POST("bot{token}/answerCallbackQuery")
    @FormUrlEncoded
    suspend fun answerCallbackQuery(
        @Path("token") token: String = getBotToken(),
        @Field("callback_query_id") callbackQueryId: String,
        @Field("text") text: String? = null
    ): TelegramResponse<Any>
    
    companion object {
        private fun getBotToken(): String = "YOUR_TELEGRAM_BOT_TOKEN"
    }
}

interface MessengerApiService {
    
    @POST("v18.0/me/messages")
    @FormUrlEncoded
    suspend fun sendTextMessage(
        @Field("recipient") recipientId: String,
        @Field("message") text: String,
        @Query("access_token") accessToken: String = getPageAccessToken()
    ): MessengerSendResponse
    
    @POST("v18.0/me/messages")
    @FormUrlEncoded
    suspend fun sendImageMessage(
        @Field("recipient") recipientId: String,
        @Field("message") imageUrl: String,
        @Query("access_token") accessToken: String = getPageAccessToken()
    ): MessengerSendResponse
    
    @POST("v18.0/me/messages")
    @FormUrlEncoded
    suspend fun sendVideoMessage(
        @Field("recipient") recipientId: String,
        @Field("message") videoUrl: String,
        @Query("access_token") accessToken: String = getPageAccessToken()
    ): MessengerSendResponse
    
    @POST("v18.0/me/messages")
    @FormUrlEncoded
    suspend fun sendAudioMessage(
        @Field("recipient") recipientId: String,
        @Field("message") audioUrl: String,
        @Query("access_token") accessToken: String = getPageAccessToken()
    ): MessengerSendResponse
    
    @POST("v18.0/me/messages")
    @FormUrlEncoded
    suspend fun sendFileMessage(
        @Field("recipient") recipientId: String,
        @Field("message") fileUrl: String,
        @Query("access_token") accessToken: String = getPageAccessToken()
    ): MessengerSendResponse
    
    @POST("v18.0/me/messages")
    @FormUrlEncoded
    suspend fun sendReaction(
        @Field("message_id") messageId: String,
        @Field("reaction") emoji: String,
        @Query("access_token") accessToken: String = getPageAccessToken()
    ): MessengerSendResponse
    
    companion object {
        private fun getPageAccessToken(): String = "YOUR_FACEBOOK_PAGE_ACCESS_TOKEN"
    }
}

interface TwitterApiService {
    
    @POST("1.1/direct_messages/events/new.json")
    @FormUrlEncoded
    suspend fun sendDirectMessage(
        @Field("recipient_id") recipientId: String,
        @Field("text") text: String
    ): TwitterDirectMessageResponse
    
    @POST("1.1/direct_messages/events/new.json")
    @FormUrlEncoded
    suspend fun sendDirectMessageWithMedia(
        @Field("recipient_id") recipientId: String,
        @Field("text") text: String,
        @Field("media_id") mediaId: String
    ): TwitterDirectMessageResponse
    
    @POST("1.1/media/upload.json")
    @Multipart
    suspend fun uploadMedia(
        @Part("media") mediaUrl: String
    ): String // Returns media_id_string
    
    @POST("1.1/direct_messages/mark_read.json")
    @FormUrlEncoded
    suspend fun markAsRead(
        @Field("last_read_event_id") lastReadEventId: String,
        @Field("recipient_id") recipientId: String
    ): Any
}
