package com.amisayem.kothabolbo.data.repository

import com.amisayem.kothabolbo.BuildConfig
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.domain.model.AppUpdate
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UpdateRepository(private val firestore: FirebaseFirestore) {
    private var checkedThisSession = false

    suspend fun checkOnce(): AppUpdate? {
        if (checkedThisSession) return null
        checkedThisSession = true
        return runCatching {
            val doc = firestore.collection(AppConstants.APP_CONFIG).document(AppConstants.VERSION_DOC).get().await()
            val latestCode = doc.getLong("latestVersionCode") ?: doc.getLong("versionCode") ?: return null
            if (latestCode <= BuildConfig.VERSION_CODE) return null
            AppUpdate(
                versionName = doc.getString("latestVersionName") ?: doc.getString("versionName") ?: latestCode.toString(),
                updateUrl = doc.getString("updateUrl").orEmpty(),
                force = doc.getBoolean("forceUpdate") ?: doc.getBoolean("force") ?: false,
                message = doc.getString("message") ?: "A new version of Kotha Bolbo is available."
            )
        }.getOrNull()
    }
}
