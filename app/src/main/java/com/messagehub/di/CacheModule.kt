package com.messagehub.di

import com.messagehub.cache.RedisManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {
    
    @Provides
    @Singleton
    fun provideRedisManager(): RedisManager {
        return RedisManager()
    }
}
