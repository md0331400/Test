@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amisayem.kothabolbo.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.TimeUtils
import com.amisayem.kothabolbo.domain.model.Post
import com.amisayem.kothabolbo.domain.model.Story
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.amisayem.kothabolbo.ui.KothaViewModel
import com.amisayem.kothabolbo.ui.components.Avatar
import com.amisayem.kothabolbo.ui.components.ConfirmDialog
import com.amisayem.kothabolbo.ui.components.EmptyState
import com.amisayem.kothabolbo.ui.components.GlassCard

@Composable
fun FeedScreen(vm: KothaViewModel, openProfile: (String) -> Unit, openCreatePost: () -> Unit) {
    val posts by vm.posts.collectAsStateWithLifecycle()
    val me by vm.currentUser.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val filtered = posts.filter { it.text.contains(query, true) || it.authorName.contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Feed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text("Public visibility is filtered on this device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search posts or authors") }, singleLine = true)
        if (filtered.isEmpty()) EmptyState("Nothing in your feed", "Create a post or check again when other people have shared something.", "Create Post", openCreatePost)
        else LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { post -> PostCard(post, me, vm, openProfile) }
        }
    }
}

@Composable
private fun PostCard(post: Post, me: UserProfile?, vm: KothaViewModel, openProfile: (String) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) ConfirmDialog("Delete post?", "The post is removed permanently. Its ImageKit file is deleted best-effort.", "Delete", true, onConfirm = { vm.deletePost(post); confirmDelete = false }, onDismiss = { confirmDelete = false })
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { openProfile(post.authorId) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(UserProfile(uid = post.authorId, displayName = post.authorName, photoURL = post.authorPhoto), 44)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(post.authorName, fontWeight = FontWeight.Bold)
                    Text("${TimeUtils.relative(post.createdAtMs)} · ${post.privacy.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (post.authorId == me?.uid) Box {
                    IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Post options") }
                    DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Delete post") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; confirmDelete = true }) }
                }
            }
            if (post.text.isNotBlank()) Text(post.text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (post.imageUrl.isNotBlank()) AsyncImage(post.imageUrl, "Post image", Modifier.fillMaxWidth().height(320.dp), contentScale = ContentScale.Crop)
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                val liked = me?.uid in post.likes
                androidx.compose.material3.TextButton(onClick = { vm.toggleLike(post) }) {
                    Icon(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" ${post.likeCount.coerceAtLeast(0)}")
                }
                androidx.compose.material3.TextButton(onClick = {
                    val shared = buildString { if (post.text.isNotBlank()) append(post.text); if (post.imageUrl.isNotBlank()) append("\n${post.imageUrl}"); append("\n — via Kotha Bolbo") }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shared) }, "Share post"))
                }) { Icon(Icons.Outlined.Share, null); Text(" Share") }
            }
        }
    }
}

@Composable
fun CreatePostScreen(vm: KothaViewModel, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Uri?>(null) }
    var privacy by remember { mutableStateOf("public") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image = it }
    Scaffold(topBar = { TopAppBar(title = { Text("Create Post") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val me by vm.currentUser.collectAsStateWithLifecycle()
            me?.let { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(it); Text(it.displayName, Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold) } }
            OutlinedTextField(text, { if (it.length <= AppConstants.MAX_TEXT_LENGTH) text = it }, Modifier.fillMaxWidth(), label = { Text("What would you like to share?") }, minLines = 6, supportingText = { Text("${text.length}/5,000") })
            image?.let { AsyncImage(it, "Selected image", Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ picker.launch("image/*") }) { Icon(Icons.Outlined.AccountBox, null); Text(if (image == null) " Add image" else " Replace") }
                if (image != null) OutlinedButton({ image = null }) { Text("Remove") }
            }
            Text("Visibility", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("public", "private").forEach { value -> FilterChip(privacy == value, { privacy = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) }) } }
            Text("Privacy is currently enforced by client filtering; proposed backend rules are documented separately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ vm.createPost(text, image, privacy, onBack) }, enabled = !busy && (text.isNotBlank() || image != null), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Publish post")
            }
        }
    }
}

@Composable
fun CreateStoryScreen(vm: KothaViewModel, onBack: () -> Unit) {
    var type by remember { mutableStateOf("text") }
    var text by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var media by remember { mutableStateOf<Uri?>(null) }
    var privacy by remember { mutableStateOf("public") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { media = it }
    Scaffold(topBar = { TopAppBar(title = { Text("Create Story") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Stories disappear after 24 hours", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("text" to Icons.Outlined.Create, "image" to Icons.Outlined.AccountBox, "video" to Icons.Outlined.PlayArrow).forEach { (value, icon) ->
                    FilterChip(type == value, { type = value; media = null }, label = { Text(value.replaceFirstChar { it.uppercase() }) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
                }
            }
            if (type == "text") OutlinedTextField(text, { if (it.length <= 5_000) text = it }, Modifier.fillMaxWidth(), label = { Text("Story text") }, minLines = 8, supportingText = { Text("${text.length}/5,000") })
            else {
                if (media == null) OutlinedButton({ picker.launch(if (type == "image") "image/*" else "video/*") }, Modifier.fillMaxWidth().height(100.dp)) { Text("Choose ${type}") }
                else {
                    if (type == "image") AsyncImage(media, "Selected story image", Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Fit)
                    else Text("Video selected: ${media?.lastPathSegment.orEmpty()}")
                    OutlinedButton({ picker.launch(if (type == "image") "image/*" else "video/*") }) { Text("Replace") }
                }
                OutlinedTextField(caption, { if (it.length <= 5_000) caption = it }, Modifier.fillMaxWidth(), label = { Text("Caption (optional)") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("public", "private").forEach { value -> FilterChip(privacy == value, { privacy = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) }) } }
            Button({ vm.createStory(type, text, caption, media, privacy, onBack) }, enabled = !busy && (type == "text" && text.isNotBlank() || type != "text" && media != null), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Share for 24 hours")
            }
        }
    }
}

@Composable
fun StoryViewerScreen(vm: KothaViewModel, storyId: String, onBack: () -> Unit) {
    val stories by vm.stories.collectAsStateWithLifecycle()
    val story = stories.firstOrNull { it.id == storyId }
    val me by vm.currentUser.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (story?.type) {
            "image" -> AsyncImage(story.mediaUrl, story.caption, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            "video" -> AndroidView(
                factory = { context -> VideoView(context).apply { val controls = MediaController(context); controls.setAnchorView(this); setMediaController(controls) } },
                update = { view -> view.setVideoURI(Uri.parse(story.mediaUrl)); view.setOnPreparedListener { it.isLooping = false; view.start() } },
                modifier = Modifier.fillMaxSize()
            )
            "text" -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text(story.text, color = Color.White, style = MaterialTheme.typography.headlineMedium) }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha = .45f)).padding(top = 26.dp, start = 8.dp, end = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Close", tint = Color.White) }
                if (story != null) {
                    Text(story.authorName, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${TimeUtils.relative(story.createdAtMs)} · ${((story.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0) / 3_600_000)}h left", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
                    if (story.authorId == me?.uid) IconButton({ vm.deleteStory(story); onBack() }) { Icon(Icons.Outlined.Delete, "Delete story", tint = Color.White) }
                }
            }
        }
        story?.caption?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(20.dp)) }
    }
}
