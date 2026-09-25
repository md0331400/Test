@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amisayem.kothabolbo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.TimeUtils
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.amisayem.kothabolbo.ui.KothaViewModel
import com.amisayem.kothabolbo.ui.components.Avatar
import com.amisayem.kothabolbo.ui.components.EmptyState
import com.amisayem.kothabolbo.ui.components.GlassCard

@Composable
fun PeopleSearchScreen(vm: KothaViewModel, onBack: () -> Unit, openProfile: (String) -> Unit, openChat: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by vm.peopleResults.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Find people") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                query, { value -> query = value; if (value.trim().length >= 2) vm.searchPeople(value) else vm.peopleResults.value = emptyList() },
                Modifier.fillMaxWidth().padding(16.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) },
                label = { Text("Name, email, or phone") }, supportingText = { Text("Email and phone remain masked unless the user made them public") }, singleLine = true
            )
            if (query.trim().length < 2) EmptyState("Find someone", "Enter at least two characters. Kotha Bolbo supports one-to-one messaging only.")
            else if (results.isEmpty()) EmptyState("No people found", "Try another name or exact contact detail.")
            else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(results, key = { it.uid }) { profile ->
                    Row(Modifier.fillMaxWidth().clickable { openProfile(profile.uid) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(profile, 54)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(profile.displayName, fontWeight = FontWeight.Bold)
                            Text(profile.shownEmail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(profile.shownPhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.selectConversation(profile.uid); openChat(profile.uid) }) { Icon(Icons.Outlined.Send, "Message") }
                    }
                    HorizontalDivider(Modifier.padding(start = 82.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    vm: KothaViewModel,
    uid: String?,
    openChat: (String) -> Unit,
    openCreatePost: () -> Unit,
    onBack: (() -> Unit)?
) {
    val me by vm.currentUser.collectAsStateWithLifecycle()
    val selected by vm.selectedOther.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val isOwn = uid == null || uid == me?.uid
    val profile = if (isOwn) me else selected?.takeIf { it.uid == uid }
    val posts by vm.posts.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.updateProfileMedia(it, false) } }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.updateProfileMedia(it, true) } }
    LaunchedEffect(uid) { if (!isOwn && uid != null) vm.loadUser(uid) }

    if (edit && profile != null) EditProfileDialog(profile, onSave = { name, phone, gender, day, month, year, bio ->
        vm.saveProfileDetails(name, phone, gender, day, month, year, bio) { edit = false }
    }, onDismiss = { edit = false })

    if (profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Scaffold(
        topBar = {
            if (onBack != null) TopAppBar(
                title = { Text(profile.displayName) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    if (profile.coverURL.isNotBlank()) AsyncImage(profile.coverURL, "Cover photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
                    Avatar(profile, 108, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
                    if (isOwn) IconButton(onClick = { coverPicker.launch("image/*") }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .8f), CircleShape)) {
                        Icon(Icons.Outlined.AccountCircle, "Change cover")
                    }
                }
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    if (profile.gender.isNotBlank()) Text(profile.gender, modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary)
                    if (profile.bio.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(profile.bio, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(16.dp))
                    if (isOwn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = openCreatePost, modifier = Modifier.weight(1f)) { Text("Create Post") }
                            OutlinedButton(onClick = { edit = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Edit, null); Text(" Edit Profile") }
                        }
                        TextButton(onClick = { photoPicker.launch("image/*") }) { Text("Change profile photo") }
                    } else {
                        Button(onClick = { vm.selectConversation(profile.uid); openChat(profile.uid) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Send, null); Text("  Send Message") }
                    }
                }
            }
            item { ProfileDetails(profile) }
            item { Text(if (isOwn) "Your posts" else "Posts", Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            val ownPosts = posts.filter { it.authorId == profile.uid }
            if (ownPosts.isEmpty()) item { EmptyState("No posts", if (isOwn) "Share something from Create Post." else "This person has no visible posts.") }
            else items(ownPosts, key = { it.id }) { post -> CompactPost(post.text, post.imageUrl, TimeUtils.relative(post.createdAtMs)) }
        }
    }
}

@Composable
private fun ProfileDetails(profile: UserProfile) {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Details & contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DetailRow(Icons.Outlined.Email, "Email", profile.shownEmail)
            DetailRow(Icons.Outlined.Phone, "Phone", profile.shownPhone)
            DetailRow(Icons.Outlined.DateRange, "Birthday", profile.birthDateLabel)
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 12.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun CompactPost(text: String, image: String, time: String) {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (text.isNotBlank()) Text(text)
            if (image.isNotBlank()) { Spacer(Modifier.height(10.dp)); AsyncImage(image, "Post image", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) }
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun EditProfileDialog(profile: UserProfile, onSave: (String, String, String, Int, Int, Int, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(profile.uid) { mutableStateOf(profile.displayName) }
    var phone by remember(profile.uid) { mutableStateOf(profile.phone) }
    var gender by remember(profile.uid) { mutableStateOf(profile.gender) }
    var day by remember(profile.uid) { mutableStateOf(profile.birthDay.toString()) }
    var month by remember(profile.uid) { mutableStateOf(profile.birthMonth.toString()) }
    var year by remember(profile.uid) { mutableStateOf(profile.birthYear.toString()) }
    var bio by remember(profile.uid) { mutableStateOf(profile.bio) }
    val last = profile.lastProfileEdit?.toDate()?.time ?: 0
    val remaining = (AppConstants.PROFILE_EDIT_COOLDOWN_MS - (System.currentTimeMillis() - last)).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (remaining > 0) Text("Name, phone, gender, and birthday can be changed again in ${kotlin.math.ceil(remaining / 86_400_000.0).toInt()} day(s). Bio is always editable.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone in E.164 format") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Male", "Female", "Other").forEach { value -> FilterChip(gender == value, { gender = value }, label = { Text(value) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("Day") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(month, { month = it.filter(Char::isDigit).take(2) }, label = { Text("Month") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, modifier = Modifier.weight(1.4f))
                }
                OutlinedTextField(bio, { if (it.length <= 500) bio = it }, label = { Text("Bio") }, supportingText = { Text("${bio.length}/500") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onSave(name, phone, gender, day.toIntOrNull() ?: 0, month.toIntOrNull() ?: 0, year.toIntOrNull() ?: 0, bio) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
