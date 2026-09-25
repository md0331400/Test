package com.amisayem.kothabolbo

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.amisayem.kothabolbo.domain.model.AuthState
import com.amisayem.kothabolbo.service.CleanupWorker
import com.amisayem.kothabolbo.service.NotificationHelper
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class KothaBolboApplication : Application(), DefaultLifecycleObserver {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super<Application>.onCreate()
        // Configure Firebase caches before any repositories acquire references.
        runCatching {
            FirebaseFirestore.getInstance().firestoreSettings = firestoreSettings {
                setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(100L * 1024 * 1024).build())
            }
        }
        runCatching { FirebaseDatabase.getInstance().setPersistenceEnabled(true) }
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scheduleCleanup()

        appScope.launch {
            container.authRepository.authState.collectLatest { state ->
                when (state) {
                    is AuthState.SignedIn -> {
                        container.presenceRepository.connect(state.uid)
                        runCatching {
                            val token = FirebaseMessaging.getInstance().token.await()
                            container.userRepository.saveFcmToken(state.uid, token)
                        }
                        runCatching {
                            container.messageRepository.cleanupExpired(state.uid)
                            container.storyRepository.cleanupExpired(state.uid)
                            container.mediaRepository.drainExpiredQueue()
                        }
                        // Exact in-app cleanup cadence from the product contract; WorkManager is
                        // only a platform safety net when the process is backgrounded.
                        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                            kotlinx.coroutines.delay(60_000)
                            runCatching {
                                container.messageRepository.cleanupExpired(state.uid)
                                container.storyRepository.cleanupExpired(state.uid)
                                container.mediaRepository.drainExpiredQueue()
                            }
                        }
                    }
                    else -> container.presenceRepository.disconnect()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) = container.presenceRepository.setForeground(true)
    override fun onStop(owner: LifecycleOwner) = container.presenceRepository.setForeground(false)

    private fun scheduleCleanup() {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "kotha_24h_cleanup", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
}
