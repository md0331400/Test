package com.amisayem.kothabolbo.domain.model

enum class AppearanceMode { SYSTEM, DARK, LIGHT }
enum class AccentTheme { CYAN, PURPLE, OCEAN, ROSE }

data class AppPreferences(
    val onboardingDone: Boolean = false,
    val privacyGateAccepted: Boolean = false,
    val appearance: AppearanceMode = AppearanceMode.DARK,
    val accent: AccentTheme = AccentTheme.CYAN,
    val inactiveMonths: Int = 12,
    val driveEmail: String = "",
    val driveBoundAt: Long = 0,
    val driveLastBackup: Long = 0,
    val autoBackup: Boolean = true,
    val newUser: Boolean = false
)

sealed interface AuthState {
    data object Checking : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val uid: String) : AuthState
}

data class AppUpdate(
    val versionName: String,
    val updateUrl: String,
    val force: Boolean,
    val message: String
)

data class UiMessage(val text: String, val id: Long = System.nanoTime())
