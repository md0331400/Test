package com.amisayem.kothabolbo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.domain.model.AuthState
import com.amisayem.kothabolbo.ui.components.LoadingScreen
import com.amisayem.kothabolbo.ui.screens.ConversationScreen
import com.amisayem.kothabolbo.ui.screens.CreatePostScreen
import com.amisayem.kothabolbo.ui.screens.CreateStoryScreen
import com.amisayem.kothabolbo.ui.screens.DriveBackupScreen
import com.amisayem.kothabolbo.ui.screens.HomeScreen
import com.amisayem.kothabolbo.ui.screens.LoginScreen
import com.amisayem.kothabolbo.ui.screens.OnboardingScreen
import com.amisayem.kothabolbo.ui.screens.PeopleSearchScreen
import com.amisayem.kothabolbo.ui.screens.PrivacyGateScreen
import com.amisayem.kothabolbo.ui.screens.ProfileScreen
import com.amisayem.kothabolbo.ui.screens.SignupScreen
import com.amisayem.kothabolbo.ui.screens.StoryViewerScreen
import kotlinx.coroutines.delay

private object Route {
    const val LOADING = "loading"
    const val PRIVACY = "privacy"
    const val ONBOARD = "onboarding"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val GOOGLE_COMPLETE = "signup/google"
    const val HOME = "home"
    const val PEOPLE = "people"
    const val CREATE_POST = "post/create"
    const val CREATE_STORY = "story/create"
    const val DRIVE = "drive"
    const val CHAT = "chat/{uid}"
    const val PROFILE = "profile/{uid}"
    const val STORY = "story/{id}"
}

@Composable
fun KothaApp(
    vm: KothaViewModel,
    pendingChat: String?,
    pendingText: String?,
    pendingActionId: String?,
    consumePendingIntent: () -> Unit
) {
    val nav = rememberNavController()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val current by vm.currentUser.collectAsStateWithLifecycle()
    val needsCompletion by vm.needsProfileCompletion.collectAsStateWithLifecycle()
    val update by vm.appUpdate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var newUserPrompt by remember { mutableStateOf(false) }
    var promptCount by remember { mutableIntStateOf(0) }
    var notificationAsked by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun external(url: String) {
        if (url.startsWith("mailto:")) context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(url)))
        else runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
            .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    fun resetTo(route: String) {
        if (nav.currentDestination?.route != route) nav.navigate(route) {
            popUpTo(nav.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(auth, prefs.privacyGateAccepted, prefs.onboardingDone, current, needsCompletion) {
        val route = nav.currentDestination?.route
        when {
            auth is AuthState.Checking -> Unit
            !prefs.privacyGateAccepted -> resetTo(Route.PRIVACY)
            !prefs.onboardingDone -> resetTo(Route.ONBOARD)
            auth is AuthState.SignedOut -> resetTo(Route.LOGIN)
            auth is AuthState.SignedIn && current == null -> if (route != Route.LOADING) resetTo(Route.LOADING)
            auth is AuthState.SignedIn && needsCompletion -> if (route != Route.GOOGLE_COMPLETE) resetTo(Route.GOOGLE_COMPLETE)
            auth is AuthState.SignedIn && route in setOf(Route.LOADING, Route.LOGIN, Route.SIGNUP, Route.GOOGLE_COMPLETE, Route.PRIVACY, Route.ONBOARD) -> resetTo(Route.HOME)
        }
    }

    LaunchedEffect(pendingChat, auth, current) {
        val other = pendingChat
        if (other != null && auth is AuthState.SignedIn && current != null) {
            if (other != current!!.uid) {
                vm.selectConversation(other)
                nav.navigate("chat/$other") { launchSingleTop = true }
                val fallback = pendingText?.takeIf { it.isNotBlank() }
                if (fallback != null && pendingActionId != null) {
                    // Notification-action fallback: one pending action id is consumed once by
                    // MainActivity, then retried after profile/chat state has had 350 ms to load.
                    delay(350)
                    vm.sendText(other, fallback)
                } else fallback?.let { vm.onComposerChanged(other, it) }
            }
            consumePendingIntent()
        }
    }

    LaunchedEffect(auth, current) {
        if (!notificationAsked && auth is AuthState.SignedIn && current != null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(prefs.newUser, auth) {
        if (prefs.newUser && auth is AuthState.SignedIn && !needsCompletion) newUserPrompt = true
    }

    LaunchedEffect(Unit) { vm.uiMessages.collect { snackbar.showSnackbar(it.text) } }

    if (newUserPrompt) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Keep your conversations safe") },
            text = { Text("Your profile is complete. Connect one Gmail account to restore before backing up your local archive. Drive backup is optional and uses the hidden appDataFolder.") },
            confirmButton = { Button(onClick = { newUserPrompt = false; vm.clearNewUserFlag(); nav.navigate(Route.DRIVE) }) { Text("Set up Drive") } },
            dismissButton = { OutlinedButton(onClick = {
                newUserPrompt = false
                if (promptCount == 0) promptCount++ else vm.clearNewUserFlag()
            }) { Text(if (promptCount == 0) "Not now" else "Don’t remind again") } }
        )
    }
    LaunchedEffect(promptCount, newUserPrompt) {
        if (promptCount == 1 && !newUserPrompt && prefs.newUser) { delay(8_000); newUserPrompt = true }
    }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!info.force) vm.dismissUpdate() },
            title = { Text("Update to ${info.versionName}") },
            text = { Text(info.message) },
            confirmButton = { Button(onClick = { if (info.updateUrl.isNotBlank()) external(info.updateUrl) }) { Text("Update Now") } },
            dismissButton = if (info.force) null else ({ OutlinedButton(onClick = vm::dismissUpdate) { Text("Later") } })
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outer ->
        NavHost(nav, startDestination = Route.LOADING, modifier = Modifier.padding(outer)) {
            composable(Route.LOADING) { LoadingWithTimeout() }
            composable(Route.PRIVACY) { PrivacyGateScreen { vm.acceptPrivacy() } }
            composable(Route.ONBOARD) { OnboardingScreen { vm.completeOnboarding() } }
            composable(Route.LOGIN) {
                LoginScreen(vm, onSignUp = { nav.navigate(Route.SIGNUP) }, onGoogleNeedsCompletion = { nav.navigate(Route.GOOGLE_COMPLETE) })
            }
            composable(Route.SIGNUP) { SignupScreen(vm, false, onBack = { nav.popBackStack() }, onComplete = { resetTo(Route.HOME) }) }
            composable(Route.GOOGLE_COMPLETE) { SignupScreen(vm, true, onBack = { vm.logout() }, onComplete = { resetTo(Route.HOME) }) }
            composable(Route.HOME) {
                HomeScreen(
                    vm,
                    openChat = { nav.navigate("chat/$it") },
                    openPeople = { nav.navigate(Route.PEOPLE) },
                    openProfile = { nav.navigate("profile/$it") },
                    openCreatePost = { nav.navigate(Route.CREATE_POST) },
                    openCreateStory = { nav.navigate(Route.CREATE_STORY) },
                    openStory = { nav.navigate("story/$it") },
                    openDrive = { nav.navigate(Route.DRIVE) },
                    openPolicy =(::external)
                )
            }
            composable(Route.PEOPLE) { PeopleSearchScreen(vm, { nav.popBackStack() }, { nav.navigate("profile/$it") }, { nav.navigate("chat/$it") }) }
            composable(Route.CREATE_POST) { CreatePostScreen(vm) { nav.popBackStack() } }
            composable(Route.CREATE_STORY) { CreateStoryScreen(vm) { nav.popBackStack() } }
            composable(Route.DRIVE) { DriveBackupScreen(vm) { nav.popBackStack() } }
            composable(Route.CHAT, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("uid").orEmpty()
                ConversationScreen(vm, id, { nav.popBackStack() }, { nav.navigate("profile/$it") })
            }
            composable(Route.PROFILE, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("uid").orEmpty()
                ProfileScreen(vm, id, { nav.navigate("chat/$it") }, { nav.navigate(Route.CREATE_POST) }, { nav.popBackStack() })
            }
            composable(Route.STORY, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                StoryViewerScreen(vm, entry.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
            }
        }
    }
}

@Composable
private fun LoadingWithTimeout() {
    var slow by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { delay(12_000); slow = true }
    Box(Modifier.fillMaxSize()) {
        LoadingScreen(if (slow) "Still loading — cached data will appear when available" else "Loading Kotha Bolbo…")
        if (slow) OutlinedButton(
            onClick = { (context as? android.app.Activity)?.recreate() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(40.dp)
        ) { Text("Retry") }
    }
}
