package com.messagehub.di

import com.messagehub.network.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }
    
    @Provides
    @Singleton
    @Named("main")
    fun provideMainRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://your-backend-api.com/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    @Named("telegram")
    fun provideTelegramRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    @Named("messenger")
    fun provideMessengerRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://graph.facebook.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    @Named("twitter")
    fun provideTwitterRetrofit(): Retrofit {
        // Twitter requires OAuth 1.0a authentication
        val twitterClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                // Add Twitter OAuth headers here
                // This is a simplified version - you'd need proper OAuth implementation
                val newRequest = request.newBuilder()
                    .addHeader("Authorization", "Bearer YOUR_TWITTER_BEARER_TOKEN")
                    .build()
                chain.proceed(newRequest)
            }
            .build()
        
        return Retrofit.Builder()
            .baseUrl("https://api.twitter.com/")
            .client(twitterClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideApiService(@Named("main") retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideTelegramApiService(@Named("telegram") retrofit: Retrofit): TelegramApiService {
        return retrofit.create(TelegramApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideMessengerApiService(@Named("messenger") retrofit: Retrofit): MessengerApiService {
        return retrofit.create(MessengerApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideTwitterApiService(@Named("twitter") retrofit: Retrofit): TwitterApiService {
        return retrofit.create(TwitterApiService::class.java)
    }
}
