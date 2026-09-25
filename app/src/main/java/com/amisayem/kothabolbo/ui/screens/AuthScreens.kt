package com.amisayem.kothabolbo.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amisayem.kothabolbo.R
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.Validators
import com.amisayem.kothabolbo.domain.model.SignupDraft
import com.amisayem.kothabolbo.ui.KothaViewModel
import com.amisayem.kothabolbo.ui.components.BrandBackground
import com.amisayem.kothabolbo.ui.components.GlassCard
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

@Composable
fun LoginScreen(
    vm: KothaViewModel,
    onSignUp: () -> Unit,
    onGoogleNeedsCompletion: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.authError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val googleOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestProfile()
            .requestIdToken(AppConstants.GOOGLE_WEB_CLIENT_ID)
            .requestScopes(Scope(AppConstants.DRIVE_SCOPE))
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, googleOptions) }
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
                vm.handleGoogleAccount(account) { isNew -> if (isNew) onGoogleNeedsCompletion() }
            } catch (e: ApiException) {
                vm.authError.value = if (e.statusCode == 10) {
                    "Google sign-in configuration error (10). Add this build’s SHA-1/SHA-256 in Firebase, then download an updated google-services.json."
                } else "Google sign-in was not completed (${e.statusCode})."
            }
        }
    }

    if (forgot) ForgotPasswordDialog(email, onEmail = { email = it }, onSend = { vm.resetPassword(it); forgot = false }, onDismiss = { forgot = false })

    BrandBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.kb_app_icon), "Kotha Bolbo", Modifier.size(104.dp))
            Text("Kotha Bolbo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("Speak freely. Stay close.", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(28.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Welcome back", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        email, { email = it }, Modifier.fillMaxWidth(),
                        label = { Text("Gmail address") }, leadingIcon = { Icon(Icons.Outlined.Email, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true
                    )
                    OutlinedTextField(
                        password, { password = it }, Modifier.fillMaxWidth(),
                        label = { Text("Password") }, leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Outlined.Clear else Icons.Outlined.Face, null) } },
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { forgot = true }) { Text("Forgot password?") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = { vm.login(email, password) },
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Sign in") }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f)); Text("  or  ", color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(Modifier.weight(1f))
                    }
                    OutlinedButton(
                        onClick = { googleClient.signOut().addOnCompleteListener { googleLauncher.launch(googleClient.signInIntent) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("G", fontWeight = FontWeight.Black); Spacer(Modifier.size(12.dp)); Text("Continue with Google") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New to Kotha Bolbo?")
                TextButton(onClick = onSignUp) { Text("Create account") }
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AppConstants.SUPPORT_EMAIL}"))) }) {
                Text("Need help? ${AppConstants.SUPPORT_EMAIL}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ForgotPasswordDialog(email: String, onEmail: (String) -> Unit, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Firebase will send a reset link to your Gmail address.")
                OutlinedTextField(email, onEmail, label = { Text("Gmail address") }, singleLine = true)
            }
        },
        confirmButton = { Button(enabled = Validators.gmail(email) == null, onClick = { onSend(email) }) { Text("Send link") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    vm: KothaViewModel,
    googleCompletion: Boolean,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val draft by vm.signupDraft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val remoteError by vm.authError.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(if (googleCompletion) 0 else 0) }
    var localError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.updateSignup { d -> d.copy(photoUri = it.toString()) } }
    }
    val titles = listOf("Your name", "Birthday & gender", "Gmail address", "Phone number", "Secure password", "Profile photo", "Review & agree")

    fun validateStep(): String? = when (step) {
        0 -> Validators.displayName(draft.displayName)
        1 -> when {
            !Validators.validDate(draft.birthDay, draft.birthMonth, draft.birthYear) -> "Choose a valid date from 1900 to today"
            draft.gender.isBlank() -> "Select your gender"
            else -> null
        }
        2 -> Validators.gmail(draft.email)
        3 -> if (Validators.e164(draft.countryPrefix, draft.phoneLocal) == null) "Enter a valid E.164 phone number" else null
        4 -> if (googleCompletion) null else Validators.password(draft.password) ?: if (draft.password != draft.confirmPassword) "Passwords do not match" else null
        6 -> if (!draft.acceptedTerms) "Accept the Terms and Privacy Policy" else null
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(if (googleCompletion) "Complete Google profile" else "Create account"); Text("Step ${step + 1} of 7", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } },
                navigationIcon = { IconButton(onClick = { if (step > 0) step-- else onBack() }) { Icon(Icons.Outlined.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        BrandBackground(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SignupProgress(step)
                Text(titles[step], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stepSubtitle(step, googleCompletion), color = MaterialTheme.colorScheme.onSurfaceVariant)
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (step) {
                            0 -> NameStep(draft) { value -> vm.updateSignup { it.copy(displayName = value) } }
                            1 -> DobGenderStep(draft) { vm.updateSignup(it) }
                            2 -> EmailStep(draft, googleCompletion) { value -> vm.updateSignup { it.copy(email = value.trim()) } }
                            3 -> PhoneStep(draft) { vm.updateSignup(it) }
                            4 -> PasswordStep(draft, googleCompletion) { vm.updateSignup(it) }
                            5 -> PhotoStep(draft, onPick = { photoPicker.launch("image/*") }, onRemove = { vm.updateSignup { it.copy(photoUri = null) } })
                            6 -> ReviewStep(draft, googleCompletion, checked = draft.acceptedTerms, onChecked = { checked -> vm.updateSignup { it.copy(acceptedTerms = checked) } }, openPrivacy = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.PRIVACY_POLICY_URL)))
                            })
                        }
                    }
                }
                (localError ?: remoteError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f, fill = true))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) OutlinedButton({ step--; localError = null }, Modifier.weight(1f).height(52.dp)) { Text("Back") }
                    Button(
                        onClick = {
                            localError = validateStep()
                            if (localError == null) {
                                if (step < 6) step++
                                else if (googleCompletion) vm.completeGoogleProfile(onComplete)
                                else vm.signUp(onComplete)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Text(if (step == 6) "Create account" else "Continue")
                    }
                }
            }
        }
    }
}

@Composable
private fun SignupProgress(step: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(7) { index ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(CircleShape)
                    .background(if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

private fun stepSubtitle(step: Int, google: Boolean) = when (step) {
    0 -> "Use the name people know you by."
    1 -> "Both are required. Birthday visibility starts private."
    2 -> if (google) "Your selected Google Gmail address is locked." else "Only @gmail.com addresses can register."
    3 -> "Choose the country prefix, then enter the local number."
    4 -> if (google) "Google securely manages this sign-in. You can add a password later." else "Use at least 6 characters and keep it private."
    5 -> "Optional. Images are automatically compressed to the 1 MB limit."
    else -> "Confirm your details before creating the account."
}

@Composable
private fun NameStep(d: SignupDraft, update: (String) -> Unit) {
    OutlinedTextField(d.displayName, update, Modifier.fillMaxWidth(), label = { Text("Full name") }, leadingIcon = { Icon(Icons.Outlined.Person, null) }, singleLine = true)
}

@Composable
private fun DobGenderStep(d: SignupDraft, update: ((SignupDraft) -> SignupDraft) -> Unit) {
    Text("Date of birth", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Day", d.birthDay, 2, Modifier.weight(1f)) { value -> update { it.copy(birthDay = value) } }
        NumberField("Month", d.birthMonth, 2, Modifier.weight(1f)) { value -> update { it.copy(birthMonth = value) } }
        NumberField("Year", d.birthYear, 4, Modifier.weight(1.4f)) { value -> update { it.copy(birthYear = value) } }
    }
    Text("Gender", fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Male", "Female", "Other").forEach { value ->
            FilterChip(selected = d.gender == value, onClick = { update { it.copy(gender = value) } }, label = { Text(value) })
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, length: Int, modifier: Modifier, update: (Int) -> Unit) {
    OutlinedTextField(
        value.toString().takeIf { value > 0 }.orEmpty(),
        { raw -> if (raw.length <= length) update(raw.filter(Char::isDigit).toIntOrNull() ?: 0) },
        modifier, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
    )
}

@Composable
private fun EmailStep(d: SignupDraft, locked: Boolean, update: (String) -> Unit) {
    OutlinedTextField(d.email, update, Modifier.fillMaxWidth(), enabled = !locked, label = { Text("Gmail address") }, leadingIcon = { Icon(Icons.Outlined.Email, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
    Text("We pre-check this address before Firebase creates the account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneStep(d: SignupDraft, update: ((SignupDraft) -> SignupDraft) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            value = AppConstants.COUNTRY_CODES.firstOrNull { it.prefix == d.countryPrefix }?.let { "${it.name} (${it.prefix})" } ?: d.countryPrefix,
            onValueChange = {}, readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(),
            label = { Text("Country / prefix") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            AppConstants.COUNTRY_CODES.forEach { country ->
                DropdownMenuItem(text = { Text("${country.name}  ${country.prefix}") }, onClick = { update { it.copy(countryPrefix = country.prefix) }; expanded = false })
            }
        }
    }
    OutlinedTextField(
        d.phoneLocal, { value -> update { it.copy(phoneLocal = value.filter { ch -> ch.isDigit() }.take(15)) } }, Modifier.fillMaxWidth(),
        label = { Text("Phone without country prefix") }, leadingIcon = { Icon(Icons.Outlined.Phone, null) },
        prefix = { Text(d.countryPrefix) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true
    )
}

@Composable
private fun PasswordStep(d: SignupDraft, google: Boolean, update: ((SignupDraft) -> SignupDraft) -> Unit) {
    if (google) {
        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp))
        Text("Protected by Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("No Kotha Bolbo password is needed now. Settings offers “Set up password” separately from password changes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        d.password, { value -> update { it.copy(password = value) } }, Modifier.fillMaxWidth(), label = { Text("Password") },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Outlined.Clear else Icons.Outlined.Face, null) } }, singleLine = true
    )
    OutlinedTextField(
        d.confirmPassword, { value -> update { it.copy(confirmPassword = value) } }, Modifier.fillMaxWidth(), label = { Text("Confirm password") },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true
    )
}

@Composable
private fun PhotoStep(d: SignupDraft, onPick: () -> Unit, onRemove: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (d.photoUri != null) AsyncImage(Uri.parse(d.photoUri), "Selected profile photo", Modifier.size(150.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        else Box(Modifier.size(150.dp).clip(CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AccountCircle, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary) }
    }
    Button(onPick, Modifier.fillMaxWidth()) { Text(if (d.photoUri == null) "Choose photo" else "Choose another") }
    if (d.photoUri != null) TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Use generated avatar") }
}

@Composable
private fun ReviewStep(d: SignupDraft, google: Boolean, checked: Boolean, onChecked: (Boolean) -> Unit, openPrivacy: () -> Unit) {
    ReviewRow("Name", d.displayName)
    ReviewRow("Birthday", "%02d/%02d/%04d".format(d.birthDay, d.birthMonth, d.birthYear))
    ReviewRow("Gender", d.gender)
    ReviewRow("Email", d.email)
    ReviewRow("Phone", d.countryPrefix + d.phoneLocal)
    ReviewRow("Sign-in", if (google) "Google" else "Gmail + password")
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChecked)
        Text("I accept the Terms and Privacy Policy", Modifier.weight(1f))
    }
    TextButton(onClick = openPrivacy) { Text("Read Privacy Policy") }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}
