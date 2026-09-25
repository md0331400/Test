package com.amisayem.kothabolbo.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amisayem.kothabolbo.domain.model.AccentTheme
import com.amisayem.kothabolbo.domain.model.AppearanceMode
import com.amisayem.kothabolbo.domain.model.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kotha_preferences")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("kb_onboarding_done_v1")
        val privacyGate = booleanPreferencesKey("kb_privacy_gate_v1")
        val appearance = stringPreferencesKey("kb_appearance_mode")
        val accent = stringPreferencesKey("kb_theme")
        val inactiveMonths = intPreferencesKey("kb_inactive_months")
        fun driveEmail(uid: String) = stringPreferencesKey("kb_drive_email_$uid")
        fun driveBound(uid: String) = longPreferencesKey("kb_drive_bound_at_$uid")
        fun driveLast(uid: String) = longPreferencesKey("kb_drive_last_$uid")
        fun autoBackup(uid: String) = booleanPreferencesKey("kb_auto_backup_$uid")
        fun newUser(uid: String) = booleanPreferencesKey("kb_new_user_$uid")
        fun restoredSession(uid: String) = booleanPreferencesKey("kb_drive_restored_session_$uid")
    }

    fun preferences(uid: String? = null): Flow<AppPreferences> = context.dataStore.data.map { p ->
        AppPreferences(
            onboardingDone = p[Keys.onboarding] ?: false,
            privacyGateAccepted = p[Keys.privacyGate] ?: false,
            appearance = enumValueOrDefault(p[Keys.appearance], AppearanceMode.DARK),
            accent = enumValueOrDefault(p[Keys.accent], AccentTheme.CYAN),
            inactiveMonths = p[Keys.inactiveMonths] ?: 12,
            driveEmail = uid?.let { p[Keys.driveEmail(it)] }.orEmpty(),
            driveBoundAt = uid?.let { p[Keys.driveBound(it)] } ?: 0,
            driveLastBackup = uid?.let { p[Keys.driveLast(it)] } ?: 0,
            autoBackup = uid?.let { p[Keys.autoBackup(it)] } ?: true,
            newUser = uid?.let { p[Keys.newUser(it)] } ?: false
        )
    }

    suspend fun acceptPrivacyGate() = context.dataStore.edit { it[Keys.privacyGate] = true }
    suspend fun completeOnboarding() = context.dataStore.edit { it[Keys.onboarding] = true }
    suspend fun setAppearance(value: AppearanceMode) = context.dataStore.edit { it[Keys.appearance] = value.name }
    suspend fun setAccent(value: AccentTheme) = context.dataStore.edit { it[Keys.accent] = value.name }
    suspend fun setInactiveMonths(months: Int) = context.dataStore.edit { it[Keys.inactiveMonths] = months }
    suspend fun setNewUser(uid: String, value: Boolean) = context.dataStore.edit { it[Keys.newUser(uid)] = value }
    suspend fun bindDrive(uid: String, email: String) = context.dataStore.edit {
        it[Keys.driveEmail(uid)] = email.trim().lowercase()
        it[Keys.driveBound(uid)] = System.currentTimeMillis()
        it[Keys.autoBackup(uid)] = true
    }
    suspend fun setDriveLast(uid: String, at: Long) = context.dataStore.edit { it[Keys.driveLast(uid)] = at }
    suspend fun setAutoBackup(uid: String, enabled: Boolean) = context.dataStore.edit { it[Keys.autoBackup(uid)] = enabled }
    suspend fun markRestoredThisSession(uid: String, value: Boolean) = context.dataStore.edit { it[Keys.restoredSession(uid)] = value }
    fun restoredThisSession(uid: String): Flow<Boolean> = context.dataStore.data.map { it[Keys.restoredSession(uid)] ?: false }
    suspend fun clearDriveBinding(uid: String) = context.dataStore.edit {
        it.remove(Keys.driveEmail(uid)); it.remove(Keys.driveBound(uid)); it.remove(Keys.driveLast(uid));
        it.remove(Keys.autoBackup(uid)); it.remove(Keys.restoredSession(uid))
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
