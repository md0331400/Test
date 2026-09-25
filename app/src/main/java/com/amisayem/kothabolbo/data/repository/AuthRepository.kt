package com.amisayem.kothabolbo.data.repository

import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import com.amisayem.kothabolbo.core.Validators
import com.amisayem.kothabolbo.domain.model.AuthState
import com.amisayem.kothabolbo.domain.model.SignupDraft
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale

class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String? get() = auth.currentUser?.email

    val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid?.let(AuthState::SignedIn) ?: AuthState.SignedOut)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid?.let(AuthState::SignedIn) ?: AuthState.SignedOut)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun login(email: String, password: String): AppResult<String> = runCatching {
        val normalized = email.trim().lowercase(Locale.ROOT)
        val exists = firestore.collection(AppConstants.USERS)
            .whereEqualTo("email", normalized).limit(1).get().await()
        if (exists.isEmpty) return AppResult.Error("No account found with this Gmail address.", "user-not-found")
        auth.signInWithEmailAndPassword(normalized, password).await().user?.uid
            ?: error("Firebase did not return an account")
    }.fold({ AppResult.Success(it) }, { friendlyError(it) })

    suspend fun signUp(draft: SignupDraft, photoUrl: String = "", photoFileId: String = ""): AppResult<String> {
        val email = draft.email.trim().lowercase(Locale.ROOT)
        val phone = Validators.e164(draft.countryPrefix, draft.phoneLocal)
            ?: return AppResult.Error("Enter a valid E.164 phone number.")
        if (Validators.gmail(email) != null) return AppResult.Error(Validators.gmail(email)!!)
        if (Validators.password(draft.password) != null) return AppResult.Error(Validators.password(draft.password)!!)
        if (draft.password != draft.confirmPassword) return AppResult.Error("Passwords do not match")

        return runCatching {
            val duplicateEmail = firestore.collection(AppConstants.USERS)
                .whereEqualTo("email", email).limit(1).get().await()
            if (!duplicateEmail.isEmpty) error("EMAIL_ALREADY_EXISTS")
            val duplicatePhone = firestore.collection(AppConstants.USERS)
                .whereEqualTo("phone", phone).limit(1).get().await()
            if (!duplicatePhone.isEmpty) error("PHONE_ALREADY_EXISTS")

            val user = auth.createUserWithEmailAndPassword(email, draft.password).await().user
                ?: error("Account creation failed")
            val avatar = photoUrl.ifBlank { UserProfile.diceBearUrl(draft.displayName, draft.gender) }
            val data = hashMapOf<String, Any?>(
                "uid" to user.uid,
                "displayName" to draft.displayName.trim(),
                "email" to email,
                "phone" to phone,
                "gender" to draft.gender,
                "birthDay" to draft.birthDay,
                "birthMonth" to draft.birthMonth,
                "birthYear" to draft.birthYear,
                "dobPublicDay" to false,
                "dobPublicMonth" to false,
                "dobPublicYear" to false,
                "emailPublic" to false,
                "phonePublic" to false,
                "bio" to "",
                "photoURL" to avatar,
                "photoFileId" to photoFileId,
                "coverURL" to "",
                "coverFileId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "online" to false,
                "lastProfileEdit" to null,
                "fcmToken" to "",
                "fcmTokens" to emptyList<String>(),
                "googleLinked" to false
            )
            firestore.collection(AppConstants.USERS).document(user.uid).set(data).await()
            user.uid
        }.fold({ AppResult.Success(it) }, {
            if (it.message == "EMAIL_ALREADY_EXISTS") AppResult.Error("This Gmail address is already registered.", "email-already-in-use")
            else if (it.message == "PHONE_ALREADY_EXISTS") AppResult.Error("This phone number is already registered.", "phone-already-in-use")
            else friendlyError(it)
        })
    }

    suspend fun signInWithGoogle(idToken: String): AppResult<Pair<String, Boolean>> = runCatching {
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        val user = result.user ?: error("Google sign-in returned no account")
        val ref = firestore.collection(AppConstants.USERS).document(user.uid)
        val snapshot = ref.get().await()
        val isNew = !snapshot.exists()
        if (isNew) {
            val email = user.email.orEmpty().lowercase(Locale.ROOT)
            ref.set(mapOf(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: email.substringBefore('@')),
                "email" to email,
                "phone" to "",
                "gender" to "",
                "birthDay" to 0,
                "birthMonth" to 0,
                "birthYear" to 0,
                "dobPublicDay" to false,
                "dobPublicMonth" to false,
                "dobPublicYear" to false,
                "emailPublic" to false,
                "phonePublic" to false,
                "bio" to "",
                "photoURL" to (user.photoUrl?.toString() ?: UserProfile.diceBearUrl(user.displayName.orEmpty())),
                "photoFileId" to "",
                "coverURL" to "",
                "coverFileId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "online" to false,
                "lastProfileEdit" to null,
                "fcmTokens" to emptyList<String>(),
                "googleLinked" to true
            )).await()
        } else ref.update("googleLinked", true).await()
        user.uid to isNew
    }.fold({ AppResult.Success(it) }, { friendlyError(it) })

    suspend fun sendPasswordReset(email: String): AppResult<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim().lowercase(Locale.ROOT)).await(); Unit
    }.fold({ AppResult.Success(Unit) }, { friendlyError(it) })

    suspend fun linkPassword(password: String): AppResult<Unit> = runCatching {
        val user = auth.currentUser ?: error("Please sign in again")
        val email = user.email ?: error("This account has no email")
        user.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await()
        Unit
    }.fold({ AppResult.Success(Unit) }, { friendlyError(it) })

    suspend fun changePassword(current: String, next: String): AppResult<Unit> = runCatching {
        val user = auth.currentUser ?: error("Please sign in again")
        val email = user.email ?: error("This account has no email")
        user.reauthenticate(EmailAuthProvider.getCredential(email, current)).await()
        user.updatePassword(next).await(); Unit
    }.fold({ AppResult.Success(Unit) }, { friendlyError(it) })

    suspend fun idToken(): String? = auth.currentUser?.getIdToken(false)?.await()?.token

    fun hasPasswordProvider(): Boolean = auth.currentUser?.providerData?.any { it.providerId == "password" } == true

    fun signOut() = auth.signOut()

    suspend fun deleteAuthenticatedUser(): AppResult<Unit> = runCatching {
        auth.currentUser?.delete()?.await() ?: error("No signed-in account"); Unit
    }.fold({ AppResult.Success(Unit) }, { friendlyError(it) })

    private fun friendlyError(t: Throwable): AppResult.Error {
        val code = (t as? FirebaseAuthException)?.errorCode
        val message = when {
            t is FirebaseAuthInvalidCredentialsException -> "The email or password is incorrect."
            t is FirebaseAuthInvalidUserException -> "No account found, or this account is disabled."
            code == "ERROR_EMAIL_ALREADY_IN_USE" -> "This Gmail address is already registered."
            code == "ERROR_INVALID_EMAIL" -> "Enter a valid Gmail address."
            code == "ERROR_WEAK_PASSWORD" -> "Password must be at least 6 characters."
            code == "ERROR_USER_DISABLED" -> "This account has been disabled."
            code == "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please wait and try again."
            t.message?.contains("network", true) == true -> "Network unavailable. Check your connection."
            else -> t.message ?: "Authentication failed. Please try again."
        }
        return AppResult.Error(message, code, t)
    }
}
