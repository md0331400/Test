package com.amisayem.kothabolbo

import android.content.Context
import com.amisayem.kothabolbo.data.local.KothaDatabase
import com.amisayem.kothabolbo.data.local.PreferencesRepository
import com.amisayem.kothabolbo.data.remote.VercelApi
import com.amisayem.kothabolbo.data.repository.AccountRepository
import com.amisayem.kothabolbo.data.repository.AuthRepository
import com.amisayem.kothabolbo.data.repository.ConnectivityRepository
import com.amisayem.kothabolbo.data.repository.DriveBackupRepository
import com.amisayem.kothabolbo.data.repository.FeedRepository
import com.amisayem.kothabolbo.data.repository.MediaRepository
import com.amisayem.kothabolbo.data.repository.MessageRepository
import com.amisayem.kothabolbo.data.repository.PresenceRepository
import com.amisayem.kothabolbo.data.repository.StoryRepository
import com.amisayem.kothabolbo.data.repository.UpdateRepository
import com.amisayem.kothabolbo.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val realtime = FirebaseDatabase.getInstance()
    val database = KothaDatabase.get(appContext)
    val dao = database.dao()
    val preferences = PreferencesRepository(appContext)
    val api = VercelApi()

    val authRepository = AuthRepository(auth, firestore)
    val userRepository = UserRepository(firestore, dao)
    val mediaRepository = MediaRepository(appContext, authRepository, api, dao)
    val messageRepository = MessageRepository(firestore, authRepository, dao, api)
    val feedRepository = FeedRepository(firestore, mediaRepository, dao)
    val storyRepository = StoryRepository(firestore, mediaRepository, dao)
    val presenceRepository = PresenceRepository(realtime)
    val connectivityRepository = ConnectivityRepository(appContext)
    val driveRepository = DriveBackupRepository(appContext, dao, userRepository, messageRepository)
    val updateRepository = UpdateRepository(firestore)
    val accountRepository = AccountRepository(auth, firestore, realtime, userRepository, mediaRepository, dao)
}
