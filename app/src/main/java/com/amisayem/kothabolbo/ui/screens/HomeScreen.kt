package com.amisayem.kothabolbo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amisayem.kothabolbo.core.TimeUtils
import com.amisayem.kothabolbo.domain.model.ChatSummary
import com.amisayem.kothabolbo.domain.model.Story
import com.amisayem.kothabolbo.ui.KothaViewModel
import com.amisayem.kothabolbo.ui.components.Avatar
import com.amisayem.kothabolbo.ui.components.ConfirmDialog
import com.amisayem.kothabolbo.ui.components.EmptyState
import com.amisayem.kothabolbo.ui.components.OfflineBanner

private enum class HomeTab(val label: String) { CHAT("Chat"), FEED("Feed"), PROFILE("Profile"), SETTINGS("Settings") }

@Composable
fun HomeScreen(
    vm: KothaViewModel,
    openChat: (String) -> Unit,
    openPeople: () -> Unit,
    openProfile: (String) -> Unit,
    openCreatePost: () -> Unit,
    openCreateStory: () -> Unit,
    openStory: (String) -> Unit,
    openDrive: () -> Unit,
    openPolicy: (String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val connected by vm.connected.collectAsStateWithLifecycle()
    val chats by vm.chats.collectAsStateWithLifecycle()
    val unread = chats.sumOf { it.unread }.coerceAtMost(100)
    val tabs = HomeTab.entries
    BackHandler(enabled = tab != 0) { tab = 0 }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            BadgedBox(badge = {
                                if (item == HomeTab.CHAT && unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                            }) {
                                Icon(when (item) {
                                    HomeTab.CHAT -> Icons.Filled.Email
                                    HomeTab.FEED -> Icons.Outlined.List
                                    HomeTab.PROFILE -> Icons.Filled.Person
                                    HomeTab.SETTINGS -> Icons.Filled.Settings
                                }, item.label)
                            }
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            when (tabs[tab]) {
                HomeTab.CHAT -> FloatingActionButton(openPeople) { Icon(Icons.Filled.Add, "Find people") }
                HomeTab.FEED -> FloatingActionButton(openCreatePost) { Icon(Icons.Filled.Add, "Create post") }
                else -> Unit
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OfflineBanner(!connected)
            when (tabs[tab]) {
                HomeTab.CHAT -> ChatListScreen(vm, openChat, openPeople, openCreateStory, openStory)
                HomeTab.FEED -> FeedScreen(vm, openProfile, openCreatePost)
                HomeTab.PROFILE -> ProfileScreen(vm, uid = null, openChat = openChat, openCreatePost = openCreatePost, onBack = null)
                HomeTab.SETTINGS -> SettingsScreen(vm, openProfile, openDrive, openPolicy)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListScreen(
    vm: KothaViewModel,
    openChat: (String) -> Unit,
    openPeople: () -> Unit,
    openCreateStory: () -> Unit,
    openStory: (String) -> Unit
) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val stories by vm.stories.collectAsStateWithLifecycle()
    val activePeople by vm.activePeople.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<ChatSummary, Boolean>?>(null) }
    val filtered = chats.filter { it.user.displayName.contains(query, true) }
    pending?.let { (chat, clear) ->
        ConfirmDialog(
            title = if (clear) "Clear conversation?" else "Remove chat from list?",
            message = if (clear) "Messages are hidden for you. If both people clear the chat, retained cloud documents are deleted."
            else "This only removes the row. Messages are not deleted.",
            confirmLabel = if (clear) "Clear" else "Remove",
            onConfirm = { if (clear) vm.clearConversation(chat.user.uid) else vm.removeChat(chat.user.uid); pending = null },
            onDismiss = { pending = null }
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kotha Bolbo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Messages disappear after 24 hours", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(openPeople) { Icon(Icons.Outlined.Person, "People search") }
        }
        StoryBar(stories, vm.currentUser.value?.uid, openCreateStory, openStory)
        ActivePeopleBar(activePeople, vm.currentUser.value?.uid) { profile ->
            if (profile.uid != vm.currentUser.value?.uid) { vm.selectConversation(profile.uid); openChat(profile.uid) }
        }
        androidx.compose.material3.OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search chats") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp)
        )
        if (filtered.isEmpty()) {
            EmptyState("No conversations yet", "Find someone by name, masked email, or phone and start a one-to-one chat.", "Find people", openPeople)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(filtered, key = { it.user.uid }) { chat ->
                    SwipeChatRow(chat, onOpen = { vm.selectConversation(chat.user.uid); openChat(chat.user.uid) }, onRemove = { pending = chat to false }, onClear = { pending = chat to true })
                    HorizontalDivider(Modifier.padding(start = 80.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))
                }
            }
        }
    }
}

@Composable
private fun StoryBar(stories: List<Story>, myUid: String?, create: () -> Unit, open: (String) -> Unit) {
    val grouped = stories.groupBy { it.authorId }.values.mapNotNull { list -> list.maxByOrNull { it.createdAtMs } }
        .sortedWith(compareBy<Story> { it.authorId == myUid }.thenByDescending { it.createdAtMs })
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(grouped, key = { it.authorId }) { story ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { open(story.id) }) {
                val profile = com.amisayem.kothabolbo.domain.model.UserProfile(uid = story.authorId, displayName = story.authorName, photoURL = story.authorPhoto)
                Avatar(profile, 62, Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp))
                Text(if (story.authorId == myUid) "Your story" else story.authorName.substringBefore(' ').take(10), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = create)) {
                Box(Modifier.size(62.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AddCircle, "Create story", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
                Text("Create", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ActivePeopleBar(online: List<com.amisayem.kothabolbo.domain.model.UserProfile>, myUid: String?, open: (com.amisayem.kothabolbo.domain.model.UserProfile) -> Unit) {
    if (online.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Active now", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(online, key = { it.uid }) { profile ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { open(profile) }) {
                    Box {
                        Avatar(profile, 50)
                        Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFF4ADE80)).align(Alignment.BottomEnd))
                    }
                    Text(if (profile.uid == myUid) "You" else profile.displayName.substringBefore(' ').take(10), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeChatRow(chat: ChatSummary, onOpen: () -> Unit, onRemove: () -> Unit, onClear: () -> Unit) {
    val threshold = with(LocalDensity.current) { 60.dp.toPx() }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> onRemove()
            SwipeToDismissBoxValue.EndToStart -> onClear()
            else -> Unit
        }
        false
    }, positionalThreshold = { threshold })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error).padding(22.dp),
                horizontalArrangement = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Icons.Outlined.List else Icons.Outlined.Delete, null, tint = Color.White)
            }
        },
        content = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(chat.user, 52)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(chat.user.displayName, fontWeight = if (chat.unread > 0) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                        chat.lastMessage?.let { Text(TimeUtils.chatTime(it.sortTime), style = MaterialTheme.typography.labelSmall, color = if (chat.unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chat.fromArchive) Icon(Icons.Outlined.List, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (chat.lastMessage?.type) { "image" -> "📷 Photo"; "voice" -> "🎤 Voice message"; else -> chat.lastMessage?.text ?: "Start a conversation" },
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (chat.unread > 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (chat.unread > 0) Badge { Text(if (chat.unread > 99) "99+" else chat.unread.toString()) }
                    }
                }
            }
        }
    )
}
