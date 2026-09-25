package com.amisayem.kothabolbo.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amisayem.kothabolbo.KothaBolboApplication

class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as KothaBolboApplication).container
        val uid = container.authRepository.currentUid ?: return Result.success()
        return runCatching {
            container.messageRepository.cleanupExpired(uid)
            container.storyRepository.cleanupExpired(uid)
            container.mediaRepository.drainExpiredQueue()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
