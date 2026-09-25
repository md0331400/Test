package com.amisayem.kothabolbo.ui

import android.accounts.Account
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amisayem.kothabolbo.AppContainer
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import com.amisayem.kothabolbo.core.Validators
import com.amisayem.kothabolbo.data.local.ChatCacheEntity
import com.amisayem.kothabolbo.data.local.MediaDeleteEntity
import com.amisayem.kothabolbo.domain.model.AccentTheme
import com.amisayem.kothabolbo.domain.model.AppPreferences
import com.amisayem.kothabolbo.domain.model.AppUpdate
import com.amisayem.kothabolbo.domain.model.AppearanceMode
import com.amisayem.kothabolbo.domain.model.AuthState
import com.amisayem.kothabolbo.domain.model.ChatContact
import com.amisayem.kothabolbo.domain.model.ChatMessage
import com.amisayem.kothabolbo.domain.model.ChatSummary
import com.amisayem.kothabolbo.domain.model.MediaUpload
import com.amisayem.kothabolbo.domain.model.Post
import com.amisayem.kothabolbo.domain.model.ReplyReference
import com.amisayem.kothabolbo.domain.model.SignupDraft
import com.amisayem.kothabolbo.domain.model.Story
import com.amisayem.kothabolbo.domain.model.UiMessage
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KothaViewModel(private val c: AppContainer) : ViewModel() {
    val authState: StateFlow<AuthState> = c.authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Checking)

    val uid: StateFlow<String?> = authState.map {
        (it as? AuthState.SignedIn)?.uid
    }.stateIn(viewModelScope, SharingStarted.Eagerly, c.authRepository.currentUid)

    val preferences: StateFlow<AppPreferences> = uid.flatMapLatest { c.preferences.preferences(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val connected: StateFlow<Boolean> = c.connectivityRepository.connected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val currentUser: StateFlow<UserProfile?> = uid.flatMapLatest { id ->
        if (id == null) flowOf(null) else combine(c.userRepository.cached(id), c.userRepository.observe(id)) { cache, live -> live ?: cache }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val needsProfileCompletion: StateFlow<Boolean> = currentUser.map { user ->
        user != null && (user.phone.isBlank() || user.gender.isBlank() || user.birthDay == 0 || user.birthMonth == 0 || user.birthYear == 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val liveMessages: StateFlow<List<ChatMessage>> = uid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.messageRepository.liveMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val contacts: StateFlow<List<ChatContact>> = uid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.messageRepository.contacts(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val restored: StateFlow<List<ChatMessage>> = uid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.dao.observeRestored(id).map { rows -> rows.map { it.toDomain() } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allMessages: StateFlow<List<ChatMessage>> = combine(liveMessages, restored) { live, archive ->
        (live + archive).distinctBy { it.id }.sortedBy { it.sortTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val onlineUsers: StateFlow<Set<String>> = c.presenceRepository.onlineUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activePeople: StateFlow<List<UserProfile>> = combine(currentUser, onlineUsers) { me, online -> me to online }
        .mapLatest { (me, online) ->
            if (me == null) emptyList()
            else listOf(me) + online.filter { it != me.uid }.mapNotNull { c.userRepository.get(it) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val chats: StateFlow<List<ChatSummary>> = combine(uid.filterNotNull(), liveMessages, restored, contacts, onlineUsers) { me, live, archive, contactList, online ->
        ChatInputs(me, live, archive, contactList, online)
    }.mapLatest { input -> buildChatSummaries(input) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val posts: StateFlow<List<Post>> = uid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.feedRepository.observe(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stories: StateFlow<List<Story>> = uid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.storyRepository.observe(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val busy = MutableStateFlow(false)
    val authError = MutableStateFlow<String?>(null)
    val signupDraft = MutableStateFlow(SignupDraft())
    val peopleResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val appUpdate = MutableStateFlow<AppUpdate?>(null)
    val selectedOther = MutableStateFlow<UserProfile?>(null)
    val selectedPresence = MutableStateFlow<com.amisayem.kothabolbo.domain.model.Presence?>(null)
    val selectedTyping = MutableStateFlow(false)
    val driveStatus = MutableStateFlow<String?>(null)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val uiMessages = _messages.asSharedFlow()
    private var typingOffJob: Job? = null
    private var presenceJob: Job? = null
    private var typingReadJob: Job? = null
    private var conversationReadJob: Job? = null
    private var draftSaveJob: Job? = null
    private var autoBackupJob: Job? = null
    private var driveRestoreJob: Job? = null
    private var driveRestoredThisProcess = false
    private var soundUid: String? = null
    private val knownIncoming = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            combine(uid, liveMessages) { id, messages -> id to messages }.collect { (id, messages) ->
                if (id != null) {
                    runCatching { c.messageRepository.markDelivered(id, messages) }
                    val incomingIds = messages.filter { it.receiverId == id }.mapTo(mutableSetOf()) { it.id }
                    if (soundUid != id) {
                        soundUid = id; knownIncoming.clear(); knownIncoming.addAll(incomingIds)
                    } else {
                        val freshCutoff = System.currentTimeMillis() - 10_000L
                        val hasNew = messages.any { it.receiverId == id && it.id !in knownIncoming && it.sortTime >= freshCutoff }
                        knownIncoming.clear(); knownIncoming.addAll(incomingIds)
                        if (hasNew) runCatching {
                            android.media.MediaPlayer.create(c.appContext, com.amisayem.kothabolbo.R.raw.in_app_message_sound)?.apply {
                                setOnCompletionListener { player -> player.release() }
                                start()
                            }
                        }
                    }
                }
            }
        }
        viewModelScope.launch { appUpdate.value = c.updateRepository.checkOnce() }
        // Once-per-process restore trigger for an already-bound account. Manual setup/backup
        // has its own stricter restore-before-backup path and runs immediately after selection.
        viewModelScope.launch {
            combine(uid, preferences) { id, prefs -> id to prefs.driveEmail }.distinctUntilChanged().collect { (id, email) ->
                driveRestoreJob?.cancel()
                if (id != null && email.isNotBlank() && !driveRestoredThisProcess) {
                    driveRestoreJob = launch {
                        delay(2_500)
                        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(c.appContext)?.account
                        if (account != null && account.name.equals(email, ignoreCase = true)) {
                            val result = c.driveRepository.restore(account, id)
                            if (result is AppResult.Success || (result is AppResult.Error && result.code in setOf("DRIVE_BACKUP_NOT_FOUND", "DRIVE_BACKUP_EMPTY"))) {
                                driveRestoredThisProcess = true
                            }
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(uid, preferences, liveMessages) { id, prefs, messages -> Triple(id, prefs, messages.hashCode()) }
                .distinctUntilChanged().collect { (id, prefs, _) ->
                    autoBackupJob?.cancel()
                    if (id != null && prefs.driveEmail.isNotBlank()) {
                        // Product rule: binding a Gmail means automatic backup is always ON.
                        autoBackupJob = launch {
                            delay(AppConstants.BACKUP_DEBOUNCE_MS)
                            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(c.appContext)?.account
                            if (account != null && account.name.equals(prefs.driveEmail, ignoreCase = true)) {
                                if (!driveRestoredThisProcess) return@launch
                                val result = c.driveRepository.backup(account, id)
                                if (result is AppResult.Success) c.preferences.setDriveLast(id, result.value.createdAt)
                            }
                        }
                    }
                }
        }
    }

    private suspend fun buildChatSummaries(input: ChatInputs): List<ChatSummary> {
        val all = (input.live + input.archive).distinctBy { it.id }.filter { it.visibleTo(input.me) }
        val contactsById = input.contacts.associateBy { it.uid }
        val partners = (all.map { it.partnerFor(input.me) } + input.contacts.filterNot { it.removed }.map { it.uid }).filter { it.isNotBlank() }.toSet()
        val summaries = partners.mapNotNull { partner ->
            val related = all.filter { it.partnerFor(input.me) == partner }.sortedBy { it.sortTime }
            val latest = related.lastOrNull()
            val contact = contactsById[partner]
            if (contact?.removed == true) {
                val removedAt = contact.removedAt?.toDate()?.time ?: Long.MAX_VALUE
                if (latest == null || latest.sortTime <= removedAt) return@mapNotNull null
            }
            val profile = c.userRepository.get(partner) ?: UserProfile(
                uid = partner,
                displayName = contact?.displayName ?: "Kotha Bolbo user",
                photoURL = contact?.photoURL.orEmpty()
            )
            ChatSummary(
                user = profile,
                lastMessage = latest,
                unread = related.count { it.receiverId == input.me && !it.seen && !it.read },
                isOnline = partner in input.online,
                fromArchive = latest != null && input.archive.any { it.id == latest.id }
            )
        }.sortedByDescending { it.sortTime }
        c.dao.cacheChats(summaries.map { summary ->
            ChatCacheEntity(
                ownerUid = input.me,
                partnerUid = summary.user.uid,
                displayName = summary.user.displayName,
                photoURL = summary.user.avatar,
                lastText = summary.lastMessage?.text.orEmpty(),
                lastType = summary.lastMessage?.type ?: "text",
                lastTimestamp = summary.sortTime,
                unread = summary.unread
            )
        })
        return summaries
    }

    fun showMessage(text: String) = viewModelScope.launch { emit(text) }
    fun acceptPrivacy() = viewModelScope.launch { c.preferences.acceptPrivacyGate() }
    fun completeOnboarding() = viewModelScope.launch { c.preferences.completeOnboarding() }

    fun login(email: String, password: String, onSuccess: () -> Unit = {}) = launchResult(
        block = { c.authRepository.login(email, password) },
        success = { onSuccess(); emit("Welcome back to Kotha Bolbo") }
    )

    fun resetPassword(email: String) = launchResult(
        block = { c.authRepository.sendPasswordReset(email) },
        success = { emit("Password reset email sent") }
    )

    fun signUp(onSuccess: () -> Unit = {}) {
        val draft = signupDraft.value
        val validation = validateDraft(draft, requirePassword = true)
        if (validation != null) { authError.value = validation; return }
        launchResult(
            block = { c.authRepository.signUp(draft) },
            success = { newUid ->
                c.preferences.setNewUser(newUid, true)
                draft.photoUri?.let { value ->
                    val uploaded = c.mediaRepository.uploadImage(Uri.parse(value), "avatars")
                    if (uploaded is AppResult.Success) c.userRepository.updateProfile(newUid, mapOf(
                        "photoURL" to uploaded.value.url, "photoFileId" to uploaded.value.fileId
                    ), false)
                }
                onSuccess(); emit("Your Kotha Bolbo account is ready")
            }
        )
    }

    fun handleGoogleAccount(account: GoogleSignInAccount, onSuccess: (Boolean) -> Unit) {
        val token = account.idToken
        if (token.isNullOrBlank()) { authError.value = "Google did not return an ID token. Verify the web client ID and SHA fingerprints."; return }
        launchResult(
            block = { c.authRepository.signInWithGoogle(token) },
            success = { (newUid, isNew) ->
                signupDraft.value = signupDraft.value.copy(
                    displayName = account.displayName.orEmpty().ifBlank { signupDraft.value.displayName },
                    email = account.email.orEmpty().ifBlank { signupDraft.value.email },
                    photoUri = null
                )
                if (isNew) {
                    c.preferences.setNewUser(newUid, true)
                    account.email?.let { c.preferences.bindDrive(newUid, it) }
                }
                onSuccess(isNew)
            }
        )
    }

    fun completeGoogleProfile(onSuccess: () -> Unit = {}) {
        val draft = signupDraft.value
        val validation = validateDraft(draft, requirePassword = false)
        if (validation != null) { authError.value = validation; return }
        val id = uid.value ?: return
        launchResult(
            block = {
                val phone = Validators.e164(draft.countryPrefix, draft.phoneLocal) ?: return@launchResult AppResult.Error("Enter a valid phone number")
                if (c.userRepository.phoneExists(phone, id)) return@launchResult AppResult.Error("This phone number is already registered")
                var image: MediaUpload? = null
                draft.photoUri?.let { uri ->
                    (c.mediaRepository.uploadImage(Uri.parse(uri), "avatars") as? AppResult.Success)?.let { image = it.value }
                }
                c.userRepository.updateProfile(id, mapOf(
                    "displayName" to draft.displayName.trim(), "phone" to phone, "gender" to draft.gender,
                    "birthDay" to draft.birthDay, "birthMonth" to draft.birthMonth, "birthYear" to draft.birthYear,
                    "photoURL" to (image?.url ?: currentUser.value?.avatar.orEmpty()),
                    "photoFileId" to (image?.fileId ?: currentUser.value?.photoFileId.orEmpty()), "googleLinked" to true
                ), false)
            },
            success = { onSuccess(); emit("Profile completed") }
        )
    }

    fun updateSignup(transform: (SignupDraft) -> SignupDraft) { signupDraft.value = transform(signupDraft.value); authError.value = null }

    private fun validateDraft(d: SignupDraft, requirePassword: Boolean): String? {
        Validators.displayName(d.displayName)?.let { return it }
        if (!Validators.validDate(d.birthDay, d.birthMonth, d.birthYear)) return "Choose a valid date of birth"
        if (d.gender.isBlank()) return "Gender is required"
        Validators.gmail(d.email)?.let { return it }
        if (Validators.e164(d.countryPrefix, d.phoneLocal) == null) return "Enter a valid E.164 phone number"
        if (requirePassword) {
            Validators.password(d.password)?.let { return it }
            if (d.password != d.confirmPassword) return "Passwords do not match"
        }
        if (!d.acceptedTerms) return "Accept the Terms and Privacy Policy to continue"
        return null
    }

    fun loadUser(otherUid: String) {
        viewModelScope.launch { selectedOther.value = c.userRepository.get(otherUid) }
    }

    fun updateProfileMedia(uri: Uri, cover: Boolean) = viewModelScope.launch {
        val id = uid.value ?: return@launch
        val old = currentUser.value
        busy.value = true
        when (val upload = c.mediaRepository.uploadImage(uri, if (cover) "covers" else "avatars")) {
            is AppResult.Success -> {
                val urlField = if (cover) "coverURL" else "photoURL"
                val idField = if (cover) "coverFileId" else "photoFileId"
                when (val result = c.userRepository.updateProfile(id, mapOf(urlField to upload.value.url, idField to upload.value.fileId), false)) {
                    is AppResult.Success -> {
                        val oldId = if (cover) old?.coverFileId else old?.photoFileId
                        oldId?.takeIf(String::isNotBlank)?.let { fileId ->
                            if (!c.mediaRepository.deleteNow(fileId)) {
                                c.dao.queueMedia(MediaDeleteEntity(fileId, id, System.currentTimeMillis(), if (cover) "cover" else "avatar"))
                            }
                        }
                        emit(if (cover) "Cover updated" else "Profile photo updated")
                    }
                    is AppResult.Error -> emit(result.message)
                    else -> Unit
                }
            }
            is AppResult.Error -> emit(upload.message)
            else -> Unit
        }
        busy.value = false
    }

    fun selectConversation(otherUid: String) {
        viewModelScope.launch {
            val me = uid.value ?: return@launch
            val other = c.userRepository.get(otherUid) ?: run { emit("User profile is unavailable"); return@launch }
            selectedOther.value = other
            c.messageRepository.reopen(me, other)
            NotificationHelperBridge.cancel(c, otherUid)
            presenceJob?.cancel(); typingReadJob?.cancel(); conversationReadJob?.cancel()
            conversationReadJob = launch {
                conversation(otherUid).collect { merged ->
                    val cloudIds = liveMessages.value.asSequence().map { it.id }.toHashSet()
                    c.messageRepository.markSeen(me, otherUid, merged.filter { it.id in cloudIds })
                }
            }
            presenceJob = launch { c.presenceRepository.presence(otherUid).collect { selectedPresence.value = it } }
            typingReadJob = launch {
                c.presenceRepository.typing(otherUid, me).collectLatest { state ->
                    val fresh = state.typing && System.currentTimeMillis() - state.updatedAt <= AppConstants.TYPING_STALE_MS
                    selectedTyping.value = fresh
                    if (fresh) {
                        delay(3_500)
                        selectedTyping.value = false
                    }
                }
            }
        }
    }

    fun conversation(otherUid: String): Flow<List<ChatMessage>> = uid.flatMapLatest { me ->
        if (me == null) flowOf(emptyList()) else c.messageRepository.conversation(me, otherUid)
    }

    fun draft(otherUid: String): Flow<String> = uid.flatMapLatest { me ->
        if (me == null) flowOf("") else c.messageRepository.draft(me, otherUid)
    }

    fun onComposerChanged(otherUid: String, text: String) {
        val me = uid.value ?: return
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(180)
            c.messageRepository.saveDraft(me, otherUid, text)
        }
        c.presenceRepository.setTyping(me, otherUid, text.isNotBlank())
        typingOffJob?.cancel()
        if (text.isNotBlank()) typingOffJob = viewModelScope.launch {
            delay(AppConstants.TYPING_WRITE_OFF_MS)
            c.presenceRepository.setTyping(me, otherUid, false)
        }
    }

    fun leaveConversation(otherUid: String) {
        val me = uid.value ?: return
        typingOffJob?.cancel(); presenceJob?.cancel(); typingReadJob?.cancel(); conversationReadJob?.cancel()
        c.presenceRepository.setTyping(me, otherUid, false)
        selectedTyping.value = false
        selectedPresence.value = null
    }

    fun sendText(otherUid: String, text: String, reply: ReplyReference? = null, onSent: () -> Unit = {}) {
        val me = currentUser.value ?: return
        viewModelScope.launch {
            val other = selectedOther.value?.takeIf { it.uid == otherUid } ?: c.userRepository.get(otherUid)
            if (other == null) { emit("Recipient profile is unavailable"); return@launch }
            when (val result = c.messageRepository.send(me, other, text, replyTo = reply)) {
                is AppResult.Success -> { c.messageRepository.clearDraft(me.uid, otherUid); c.presenceRepository.setTyping(me.uid, otherUid, false); onSent() }
                is AppResult.Error -> emit(result.message)
                else -> Unit
            }
        }
    }

    fun sendImage(otherUid: String, uri: Uri, caption: String = "", reply: ReplyReference? = null) = viewModelScope.launch {
        busy.value = true
        val me = currentUser.value
        val other = selectedOther.value?.takeIf { it.uid == otherUid } ?: c.userRepository.get(otherUid)
        if (me != null && other != null) {
            when (val upload = c.mediaRepository.uploadImage(uri, "chat_images")) {
                is AppResult.Success -> {
                    val sent = c.messageRepository.send(me, other, caption, "image", upload.value.url, upload.value.fileId, replyTo = reply)
                    if (sent is AppResult.Error) emit(sent.message)
                    else c.messageRepository.clearDraft(me.uid, otherUid)
                }
                is AppResult.Error -> emit(upload.message)
                else -> Unit
            }
        }
        busy.value = false
    }

    fun sendVoice(otherUid: String, uri: Uri, durationMs: Long) = viewModelScope.launch {
        busy.value = true
        val me = currentUser.value
        val other = selectedOther.value?.takeIf { it.uid == otherUid } ?: c.userRepository.get(otherUid)
        if (me != null && other != null) {
            when (val upload = c.mediaRepository.uploadFile(uri, "voice_messages", 15L * 1024 * 1024)) {
                is AppResult.Success -> {
                    val durationSeconds = ((durationMs + 999L) / 1_000L).coerceAtLeast(1L)
                    val sent = c.messageRepository.send(me, other, type = "voice", voiceUrl = upload.value.url, voiceFileId = upload.value.fileId, voiceDuration = durationSeconds)
                    if (sent is AppResult.Error) emit(sent.message)
                }
                is AppResult.Error -> emit(upload.message)
                else -> Unit
            }
        }
        busy.value = false
    }

    fun deleteForMe(message: ChatMessage) = resultMessage { c.messageRepository.deleteForMe(uid.value ?: return@resultMessage AppResult.Error("Signed out"), message) }
    fun deleteForEveryone(message: ChatMessage) = resultMessage { c.messageRepository.deleteForEveryone(uid.value ?: return@resultMessage AppResult.Error("Signed out"), message) }
    fun editMessage(message: ChatMessage, text: String) = resultMessage { c.messageRepository.edit(uid.value ?: return@resultMessage AppResult.Error("Signed out"), message, text) }
    fun clearConversation(otherUid: String) = resultMessage { c.messageRepository.clearConversation(uid.value ?: return@resultMessage AppResult.Error("Signed out"), otherUid) }
    fun removeChat(otherUid: String) = resultMessage { c.messageRepository.removeFromList(uid.value ?: return@resultMessage AppResult.Error("Signed out"), otherUid) }

    fun searchPeople(query: String) = viewModelScope.launch {
        val me = uid.value ?: return@launch
        peopleResults.value = when (val result = c.userRepository.search(query, me)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> { emit(result.message); emptyList() }
            else -> emptyList()
        }
    }

    fun createPost(text: String, image: Uri?, privacy: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        val author = currentUser.value ?: return@launch
        busy.value = true
        val upload = image?.let { c.mediaRepository.uploadImage(it, "feed") }
        if (upload is AppResult.Error) {
            if (text.isBlank()) { emit(upload.message); busy.value = false; return@launch }
            emit("Image upload failed; publishing the text-only post.")
        }
        val media = (upload as? AppResult.Success)?.value
        when (val result = c.feedRepository.create(author, text, media, privacy)) {
            is AppResult.Success -> { emit("Post published"); onDone() }
            is AppResult.Error -> emit(result.message)
            else -> Unit
        }
        busy.value = false
    }

    fun toggleLike(post: Post) = resultMessage(false) { c.feedRepository.toggleLike(post, uid.value ?: return@resultMessage AppResult.Error("Signed out")) }
    fun deletePost(post: Post) = resultMessage { c.feedRepository.delete(post, uid.value ?: return@resultMessage AppResult.Error("Signed out")) }

    fun createStory(type: String, text: String, caption: String, uri: Uri?, privacy: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        val author = currentUser.value ?: return@launch
        busy.value = true
        val upload: AppResult<MediaUpload>? = when (type) {
            "image" -> uri?.let { c.mediaRepository.uploadImage(it, "stories") }
            "video" -> uri?.let { c.mediaRepository.uploadFile(it, "stories", AppConstants.MAX_STORY_VIDEO_BYTES) }
            else -> null
        }
        if (type != "text" && upload == null) { emit("Select media for this story"); busy.value = false; return@launch }
        if (upload is AppResult.Error) { emit(upload.message); busy.value = false; return@launch }
        when (val result = c.storyRepository.create(author, type, text, caption, (upload as? AppResult.Success)?.value, privacy)) {
            is AppResult.Success -> { emit("Story published for 24 hours"); onDone() }
            is AppResult.Error -> emit(result.message)
            else -> Unit
        }
        busy.value = false
    }

    fun deleteStory(story: Story) = resultMessage { c.storyRepository.delete(story, uid.value ?: return@resultMessage AppResult.Error("Signed out")) }

    fun saveProfileDetails(
        name: String,
        phone: String,
        gender: String,
        birthDay: Int,
        birthMonth: Int,
        birthYear: Int,
        bio: String,
        onDone: () -> Unit = {}
    ) {
        val id = uid.value ?: return
        val old = currentUser.value ?: return
        val normalizedPhone = phone.replace(Regex("[\\s()-]"), "")
        val cooldownChanged = name.trim() != old.displayName || normalizedPhone != old.phone || gender != old.gender ||
            birthDay != old.birthDay || birthMonth != old.birthMonth || birthYear != old.birthYear
        launchResult(
            block = {
                Validators.displayName(name)?.let { return@launchResult AppResult.Error(it) }
                if (!normalizedPhone.matches(Regex("^\\+[1-9]\\d{7,14}$"))) return@launchResult AppResult.Error("Enter a valid E.164 phone number")
                if (!Validators.validDate(birthDay, birthMonth, birthYear)) return@launchResult AppResult.Error("Choose a valid birthday")
                if (bio.length > 500) return@launchResult AppResult.Error("Bio cannot exceed 500 characters")
                if (normalizedPhone != old.phone && c.userRepository.phoneExists(normalizedPhone, id)) return@launchResult AppResult.Error("This phone number is already registered")
                c.userRepository.updateProfile(id, mapOf(
                    "displayName" to name.trim(), "phone" to normalizedPhone, "gender" to gender,
                    "birthDay" to birthDay, "birthMonth" to birthMonth, "birthYear" to birthYear,
                    "bio" to bio.trim()
                ), cooldownChanged)
            },
            success = { emit("Profile updated"); onDone() }
        )
    }

    fun updateProfile(changes: Map<String, Any?>, cooldownFieldsChanged: Boolean, onDone: () -> Unit = {}) = launchResult(
        block = { c.userRepository.updateProfile(uid.value ?: return@launchResult AppResult.Error("Signed out"), changes, cooldownFieldsChanged) },
        success = { emit("Profile updated"); onDone() }
    )

    fun updatePrivacy(email: Boolean, phone: Boolean, day: Boolean, month: Boolean, year: Boolean) = launchResult(
        block = { c.userRepository.updatePrivacy(uid.value ?: return@launchResult AppResult.Error("Signed out"), email, phone, day, month, year) },
        success = { emit("Privacy settings saved") }
    )

    fun setAppearance(value: AppearanceMode) = viewModelScope.launch { c.preferences.setAppearance(value) }
    fun setAccent(value: AccentTheme) = viewModelScope.launch { c.preferences.setAccent(value) }
    fun setInactiveMonths(value: Int) = viewModelScope.launch { c.preferences.setInactiveMonths(value); emit("Preference saved. Automatic deletion is not enabled.") }
    fun clearNewUserFlag() = uid.value?.let { viewModelScope.launch { c.preferences.setNewUser(it, false) } }

    fun changePassword(current: String, next: String) = launchResult(
        block = { c.authRepository.changePassword(current, next) }, success = { emit("Password changed") }
    )
    fun setPassword(next: String) = launchResult(
        block = { c.authRepository.linkPassword(next) }, success = { emit("Password added to your Google account") }
    )
    fun hasPasswordProvider() = c.authRepository.hasPasswordProvider()

    fun logout(onDone: () -> Unit = {}) = viewModelScope.launch {
        busy.value = true
        val id = uid.value
        if (id != null) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                c.userRepository.removeFcmToken(id, token)
            }
        }
        c.presenceRepository.disconnect()
        runCatching {
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
                c.appContext,
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).signOut().await()
        }
        c.authRepository.signOut()
        busy.value = false
        emit("You’re signed out")
        onDone()
    }

    fun deleteAccount(phrase: String, onDone: () -> Unit = {}) = launchResult(
        block = { c.accountRepository.deleteAccount(phrase) },
        success = { emit("Account deleted"); onDone() }
    )

    fun dismissUpdate() { if (appUpdate.value?.force != true) appUpdate.value = null }

    fun backupDrive(account: Account) = viewModelScope.launch {
        val id = uid.value ?: return@launch
        val bound = preferences.value.driveEmail
        if (bound.isNotBlank() && !bound.equals(account.name, ignoreCase = true)) {
            driveStatus.value = "DRIVE_PERMISSION_ERROR: This Kotha Bolbo account is already bound to $bound."
            return@launch
        }
        busy.value = true
        if (bound.isBlank()) c.preferences.bindDrive(id, account.name)
        // Restore-before-backup prevents a fresh install from overwriting the only archive.
        val restore = c.driveRepository.restore(account, id)
        if (restore is AppResult.Error && restore.code !in setOf("DRIVE_BACKUP_NOT_FOUND", "DRIVE_BACKUP_EMPTY")) {
            driveStatus.value = "${restore.code}: ${restore.message}"; busy.value = false; return@launch
        }
        driveRestoredThisProcess = true
        when (val result = c.driveRepository.backup(account, id)) {
            is AppResult.Success -> {
                c.preferences.setDriveLast(id, result.value.createdAt)
                driveStatus.value = "Backup complete: ${result.value.messageCount} messages"
            }
            is AppResult.Error -> driveStatus.value = "${result.code}: ${result.message}"
            else -> Unit
        }
        busy.value = false
    }

    fun restoreDrive(account: Account) = viewModelScope.launch {
        val id = uid.value ?: return@launch
        val bound = preferences.value.driveEmail
        if (bound.isNotBlank() && !bound.equals(account.name, ignoreCase = true)) {
            driveStatus.value = "DRIVE_PERMISSION_ERROR: Choose the bound Gmail account ($bound)."; return@launch
        }
        busy.value = true
        if (bound.isBlank()) c.preferences.bindDrive(id, account.name)
        when (val result = c.driveRepository.restore(account, id)) {
            is AppResult.Success -> {
                driveRestoredThisProcess = true
                driveStatus.value = "Restored ${result.value.messageCount} archived messages"
            }
            is AppResult.Error -> driveStatus.value = "${result.code}: ${result.message}"
            else -> Unit
        }
        busy.value = false
    }

    fun deleteDriveBackup(account: Account) = viewModelScope.launch {
        val id = uid.value ?: return@launch
        val bound = preferences.value.driveEmail
        if (bound.isBlank() || !bound.equals(account.name, ignoreCase = true)) {
            driveStatus.value = "DRIVE_PERMISSION_ERROR: Choose the permanently bound Gmail account."
            return@launch
        }
        busy.value = true
        when (val result = c.driveRepository.deleteBackup(account)) {
            is AppResult.Success -> {
                c.preferences.clearDriveBinding(id)
                driveRestoredThisProcess = false
                driveStatus.value = "Backup deleted and local Drive binding cleared"
            }
            is AppResult.Error -> driveStatus.value = "${result.code}: ${result.message}"
            else -> Unit
        }
        busy.value = false
    }

    private fun <T> launchResult(block: suspend () -> AppResult<T>, success: suspend (T) -> Unit = {}) {
        viewModelScope.launch {
            busy.value = true; authError.value = null
            when (val result = block()) {
                is AppResult.Success -> success(result.value)
                is AppResult.Error -> { authError.value = result.message; emit(result.message) }
                else -> Unit
            }
            busy.value = false
        }
    }

    private fun resultMessage(showSuccess: Boolean = true, block: suspend () -> AppResult<Unit>) = viewModelScope.launch {
        when (val result = block()) {
            is AppResult.Success -> if (showSuccess) emit("Done")
            is AppResult.Error -> emit(result.message)
            else -> Unit
        }
    }

    private suspend fun emit(text: String) { _messages.emit(UiMessage(text)) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = KothaViewModel(container) as T
    }

    private data class ChatInputs(
        val me: String,
        val live: List<ChatMessage>,
        val archive: List<ChatMessage>,
        val contacts: List<ChatContact>,
        val online: Set<String>
    )
}

/** Keeps Android notification plumbing outside screen composables. */
private object NotificationHelperBridge {
    fun cancel(container: AppContainer, senderId: String) {
        com.amisayem.kothabolbo.service.NotificationHelper.cancel(container.appContext, senderId)
    }
}
