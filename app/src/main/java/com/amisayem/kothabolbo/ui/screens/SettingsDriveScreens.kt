package com.amisayem.kothabolbo.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amisayem.kothabolbo.BuildConfig
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.TimeUtils
import com.amisayem.kothabolbo.domain.model.AccentTheme
import com.amisayem.kothabolbo.domain.model.AppearanceMode
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.amisayem.kothabolbo.ui.KothaViewModel
import com.amisayem.kothabolbo.ui.components.Avatar
import com.amisayem.kothabolbo.ui.components.ConfirmDialog
import com.amisayem.kothabolbo.ui.components.DeleteAccountDialog
import com.amisayem.kothabolbo.ui.components.GlassCard
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

@Composable
fun SettingsScreen(
    vm: KothaViewModel,
    openProfile: (String) -> Unit,
    openDrive: () -> Unit,
    openPolicy: (String) -> Unit
) {
    val profile by vm.currentUser.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var privacy by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf(false) }
    var deleteExplain by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var inactiveMenu by remember { mutableStateOf(false) }
    var photoPreview by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.updateProfileMedia(it, false) } }

    if (privacy && profile != null) PrivacyDialog(profile!!, onSave = { email, phone, day, month, year -> vm.updatePrivacy(email, phone, day, month, year); privacy = false }, onDismiss = { privacy = false })
    if (password) PasswordDialog(vm.hasPasswordProvider(), onSave = { old, next -> if (vm.hasPasswordProvider()) vm.changePassword(old, next) else vm.setPassword(next); password = false }, onDismiss = { password = false })
    if (deleteExplain) ConfirmDialog(
        title = "Delete your account?",
        message = "This permanently removes your sent and received messages, chat contacts, profile media, profile document, presence, and Firebase account. This cannot be undone.",
        confirmLabel = "Continue",
        destructive = true,
        onConfirm = { deleteExplain = false; deleteAccount = true },
        onDismiss = { deleteExplain = false }
    )
    if (deleteAccount) DeleteAccountDialog(onDelete = { vm.deleteAccount(it); deleteAccount = false }, onDismiss = { deleteAccount = false })
    if (logout) ConfirmDialog("Sign out?", "Presence is set offline and this device’s FCM token is detached from your profile.", "Sign out", onConfirm = { vm.logout(); logout = false }, onDismiss = { logout = false })
    if (photoPreview && profile != null) AlertDialog(onDismissRequest = { photoPreview = false }, confirmButton = { TextButton({ photoPreview = false }) { Text("Close") } }, text = { AsyncImage(profile!!.avatar, "Profile photo", Modifier.fillMaxWidth().height(320.dp), contentScale = ContentScale.Fit) })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        Text("Settings", Modifier.padding(20.dp, 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        profile?.let { user ->
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).combinedClickable(onClick = { picker.launch("image/*") }, onLongClick = { photoPreview = true })) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user, 64)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Tap to change photo · hold to view", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.Outlined.KeyboardArrowRight, null)
                }
            }
        }
        SettingsSection("Account") {
            SettingsRow(Icons.Outlined.AccountCircle, "My profile", "View and edit your profile") { profile?.uid?.let(openProfile) }
            SettingsRow(Icons.Outlined.Lock, if (vm.hasPasswordProvider()) "Change password" else "Set up password", if (vm.hasPasswordProvider()) "Re-authentication is required" else "Add email/password sign-in to your Google account") { password = true }
            SettingsRow(Icons.Outlined.Lock, "Privacy", "Email, phone, and birthday visibility") { privacy = true }
            Box {
                SettingsRow(Icons.Outlined.Person, "Inactive account", "${prefs.inactiveMonths} months · preference only, not enforced") { inactiveMenu = true }
                DropdownMenu(inactiveMenu, { inactiveMenu = false }) {
                    listOf(1, 3, 6, 12, 18, 24).forEach { months -> DropdownMenuItem(text = { Text("$months month${if (months == 1) "" else "s"}") }, trailingIcon = { if (prefs.inactiveMonths == months) Icon(Icons.Outlined.Check, null) }, onClick = { vm.setInactiveMonths(months); inactiveMenu = false }) }
                }
            }
            SettingsRow(Icons.Outlined.Delete, "Delete account", "Permanent and irreversible", danger = true) { deleteExplain = true }
        }
        SettingsSection("Appearance") {
            Text("Mode", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { AppearanceMode.entries.forEach { mode -> FilterChip(prefs.appearance == mode, { vm.setAppearance(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            Text("Accent", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { AccentTheme.entries.forEach { accent -> FilterChip(prefs.accent == accent, { vm.setAccent(accent) }, label = { Text(accent.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
        }
        SettingsSection("App & data") {
            SettingsRow(Icons.Outlined.Refresh, "Google Drive backup", if (prefs.driveEmail.isBlank()) "Not connected" else "Bound to ${prefs.driveEmail}") { openDrive() }
            SettingsRow(Icons.Outlined.Lock, "Privacy Policy", "How Kotha Bolbo handles data") { openPolicy(AppConstants.PRIVACY_POLICY_URL) }
            SettingsRow(Icons.Outlined.Info, "Account deletion information", "Public deletion instructions") { openPolicy(AppConstants.ACCOUNT_DELETION_URL) }
        }
        SettingsSection("About") {
            SettingsRow(Icons.Outlined.Info, "Kotha Bolbo", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") {}
            SettingsRow(Icons.Outlined.AccountCircle, "Developer", AppConstants.DEVELOPER) {}
            SettingsRow(Icons.Outlined.Email, "Support", AppConstants.SUPPORT_EMAIL) { openPolicy("mailto:${AppConstants.SUPPORT_EMAIL}") }
        }
        OutlinedButton(onClick = { logout = true }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Icon(Icons.Outlined.ExitToApp, null); Text("  Sign out") }
        Text("Inactive-account duration is stored as a preference only. Kotha Bolbo does not automatically delete inactive accounts.", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(title, Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Column { content() } }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, danger: Boolean = false, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = action).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
}

@Composable
private fun PrivacyDialog(profile: UserProfile, onSave: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf(profile.emailPublic) }; var phone by remember { mutableStateOf(profile.phonePublic) }
    var day by remember { mutableStateOf(profile.dobPublicDay) }; var month by remember { mutableStateOf(profile.dobPublicMonth) }; var year by remember { mutableStateOf(profile.dobPublicYear) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy controls") },
        text = { Column { PrivacyToggle("Show full email", email) { email = it }; PrivacyToggle("Show full phone", phone) { phone = it }; PrivacyToggle("Show birth day", day) { day = it }; PrivacyToggle("Show birth month", month) { month = it }; PrivacyToggle("Show birth year", year) { year = it }; Text("Private values use deterministic masking across profile and people search.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        confirmButton = { Button({ onSave(email, phone, day, month, year) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PrivacyToggle(label: String, checked: Boolean, update: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, update) } }

@Composable
private fun PasswordDialog(hasPassword: Boolean, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPassword) "Change password" else "Set up password") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { if (hasPassword) OutlinedTextField(current, { current = it }, label = { Text("Current password") }, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(next, { next = it }, label = { Text("New password (6+ characters)") }, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm new password") }, visualTransformation = PasswordVisualTransformation()) } },
        confirmButton = { Button(enabled = next.length >= 6 && next == confirm && (!hasPassword || current.isNotBlank()), onClick = { onSave(current, next) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveBackupScreen(vm: KothaViewModel, onBack: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val status by vm.driveStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var action by remember { mutableStateOf("backup") }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = Scope(AppConstants.DRIVE_SCOPE)
    val options = remember { GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestProfile().requestScopes(scope).build() }
    val client = remember { GoogleSignIn.getClient(context, options) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val signed = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
                signed.account?.let { account ->
                    when (action) {
                        "restore" -> vm.restoreDrive(account)
                        "delete" -> vm.deleteDriveBackup(account)
                        else -> vm.backupDrive(account)
                    }
                }
            } catch (e: ApiException) { vm.driveStatus.value = if (e.statusCode == 10) "DRIVE_PERMISSION_ERROR: OAuth SHA configuration error (10)." else "DRIVE_PERMISSION_ERROR: Google account selection failed (${e.statusCode})." }
        }
    }
    fun run(which: String) {
        action = which
        val last = GoogleSignIn.getLastSignedInAccount(context)
        if (last?.account != null && GoogleSignIn.hasPermissions(last, scope) && (prefs.driveEmail.isBlank() || prefs.driveEmail.equals(last.email, true))) {
            when (which) {
                "restore" -> vm.restoreDrive(last.account!!)
                "delete" -> vm.deleteDriveBackup(last.account!!)
                else -> vm.backupDrive(last.account!!)
            }
        } else launcher.launch(client.signInIntent)
    }
    if (confirmDelete) ConfirmDialog(
        title = "Delete Drive backup?",
        message = "This permanently deletes kothabolbo_backup.json and clears the local Gmail binding. Cloud messages are not deleted.",
        confirmLabel = "Delete backup",
        destructive = true,
        onConfirm = { confirmDelete = false; run("delete") },
        onDismiss = { confirmDelete = false }
    )
    Scaffold(topBar = { TopAppBar(title = { Text("Google Drive backup") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Refresh, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Private app-data backup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Kotha Bolbo stores kothabolbo_backup.json in Google Drive’s hidden appDataFolder. Other apps cannot browse it. Media binaries are not included.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gmail lock", fontWeight = FontWeight.Bold)
                Text(if (prefs.driveEmail.isBlank()) "No Gmail account is bound yet. The first successful selection becomes the locked Drive account for this Kotha Bolbo uid." else prefs.driveEmail, color = MaterialTheme.colorScheme.primary)
                Text("One Gmail account per Kotha Bolbo uid · comparison is case-insensitive · backup uid must match on restore", style = MaterialTheme.typography.bodySmall)
            } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic backup is ON when bound", fontWeight = FontWeight.SemiBold)
                    Text("Silent 20-second debounce after chat activity", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.Check, "Enabled", tint = MaterialTheme.colorScheme.primary)
            }
            status?.let { Text(it, color = if (it.contains("ERROR")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            Button({ run("backup") }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.Refresh, null); Text("  Restore first, then back up") } }
            OutlinedButton({ run("restore") }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Outlined.Refresh, null); Text("  Restore archive manually") }
            if (prefs.driveEmail.isNotBlank()) OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Icon(Icons.Outlined.Delete, null); Text("  Delete Drive backup") }
            Text("Restore merges by message ID into a Room archive and never uploads restored rows back into Firestore. This prevents old messages from reappearing as live cloud messages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            prefs.driveLastBackup.takeIf { it > 0 }?.let { Text("Last backup: ${TimeUtils.relative(it)}") }
        }
    }
}
