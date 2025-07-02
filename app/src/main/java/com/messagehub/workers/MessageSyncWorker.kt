package com.messagehub.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.messagehub.data.MessageRepository
import com.messagehub.network.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MessageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            val deviceId = messageRepository.getDeviceId()
            if (deviceId.isNotEmpty()) {
                val messages = apiService.getMessages(deviceId)
                messageRepository.saveMessages(messages)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
