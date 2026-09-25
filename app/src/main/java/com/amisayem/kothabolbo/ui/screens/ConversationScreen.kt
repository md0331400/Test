package com.amisayem.kothabolbo.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.amisayem.kothabolbo.core.TimeUtils
import com.amisayem.kothabolbo.domain.model.ChatMessage
import com.amisayem.kothabolbo.domain.model.MessageStatus
import com.amisayem.kothabolbo.domain.model.ReplyReference
import com.amisayem.kothabolbo.ui.KothaViewModel
import com.amisayem.kothabolbo.ui.components.Avatar
import com.amisayem.kothabolbo.ui.components.ConfirmDialog
import com.amisayem.kothabolbo.ui.components.EmptyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(vm: KothaViewModel, otherUid: String, onBack: () -> Unit, openProfile: (String) -> Unit) {
    val me by vm.currentUser.collectAsStateWithLifecycle()
    val other by vm.selectedOther.collectAsStateWithLifecycle()
    val presence by vm.selectedPresence.collectAsStateWithLifecycle()
    val typing by vm.selectedTyping.collectAsStateWithLifecycle()
    val messages by vm.conversation(otherUid).collectAsStateWithLifecycle(emptyList())
    val cachedDraft by vm.draft(otherUid).collectAsStateWithLifecycle("")
    val busy by vm.busy.collectAsStateWithLifecycle()
    var composer by remember(otherUid) { mutableStateOf("") }
    var draftLoaded by remember(otherUid) { mutableStateOf(false) }
    var reply by remember { mutableStateOf<ChatMessage?>(null) }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<ChatMessage, Boolean>?>(null) }
    var zoomUrl by remember { mutableStateOf<String?>(null) }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingImage = uri
    }

    LaunchedEffect(otherUid) { vm.selectConversation(otherUid) }
    DisposableEffect(otherUid) { onDispose { vm.leaveConversation(otherUid) } }
    LaunchedEffect(cachedDraft) { if (!draftLoaded) { composer = cachedDraft; draftLoaded = true } }
    LaunchedEffect(messages.size) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: Int.MAX_VALUE
        if (messages.isNotEmpty() && (lastVisible >= messages.lastIndex - 2 || listState.layoutInfo.totalItemsCount == 0)) listState.animateScrollToItem(messages.lastIndex)
    }

    selected?.let { message ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Text("Message options", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SheetAction(Icons.Outlined.ArrowBack, "Reply") { reply = message; selected = null }
            if (message.senderId == me?.uid && message.type == "text") SheetAction(Icons.Outlined.Edit, "Edit") { editTarget = message; selected = null }
            SheetAction(Icons.Outlined.Delete, "Delete for me") { deleteTarget = message to false; selected = null }
            if (message.senderId == me?.uid) SheetAction(Icons.Outlined.Delete, "Delete for everyone") { deleteTarget = message to true; selected = null }
            Spacer(Modifier.height(24.dp))
        }
    }
    editTarget?.let { target -> EditMessageDialog(target.text, onSave = { vm.editMessage(target, it); editTarget = null }, onDismiss = { editTarget = null }) }
    pendingImage?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImage = null },
            title = { Text("Send image") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(uri, "Image preview", Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                    Text(if (composer.isBlank()) "No caption" else composer, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("Images are automatically compressed to 1 MB.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = {
                val quotedName = reply?.let { quoted -> if (quoted.senderId == me?.uid) me?.displayName.orEmpty() else other?.displayName.orEmpty() }.orEmpty()
                vm.sendImage(otherUid, uri, composer, reply?.asReply(quotedName))
                pendingImage = null; composer = ""; reply = null
            }) { Text("Send") } },
            dismissButton = { OutlinedButton(onClick = { pendingImage = null }) { Text("Cancel") } }
        )
    }
    zoomUrl?.let { url ->
        Dialog(onDismissRequest = { zoomUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(url, "Full-size shared image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = { zoomUrl = null }, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)) {
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White)
                }
            }
        }
    }
    deleteTarget?.let { (target, everyone) ->
        ConfirmDialog(
            if (everyone) "Delete for everyone?" else "Delete for me?",
            if (everyone) "This permanently removes the retained cloud message for both people." else "This message remains hidden for you after refresh and restart.",
            "Delete", true,
            onConfirm = { if (everyone) vm.deleteForEveryone(target) else vm.deleteForMe(target); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.clickableNoIndication { openProfile(otherUid) }, verticalAlignment = Alignment.CenterVertically) {
                        other?.let { Avatar(it, 40) }
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(other?.displayName ?: "Conversation", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    typing -> "typing…"
                                    presence?.state == "online" -> "Online"
                                    presence?.lastChanged ?: 0L > 0 -> "Last seen ${TimeUtils.relative(presence!!.lastChanged)}"
                                    else -> "Offline"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (typing || presence?.state == "online") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }
            )
        },
        bottomBar = {
            Column(Modifier.imePadding().background(MaterialTheme.colorScheme.surface)) {
                AnimatedVisibility(typing && other != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        other?.let { Avatar(it, 26) }
                        Text("${other?.displayName.orEmpty()} is typing…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                AnimatedVisibility(reply != null) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(reply?.let { if (it.senderId == me?.uid) "Replying to yourself" else "Replying to ${other?.displayName}" }.orEmpty(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(reply?.preview().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton({ reply = null }) { Icon(Icons.Outlined.Close, "Cancel reply") }
                    }
                }
                Composer(
                    text = composer,
                    onText = { composer = it; vm.onComposerChanged(otherUid, it) },
                    onImage = { imagePicker.launch("image/*") },
                    onSend = {
                        if (composer.isNotBlank()) {
                            val quotedName = reply?.let { quoted -> if (quoted.senderId == me?.uid) me?.displayName.orEmpty() else other?.displayName.orEmpty() }.orEmpty()
                            vm.sendText(otherUid, composer, reply?.asReply(quotedName)) { composer = ""; reply = null }
                        }
                    },
                    onVoice = { uri, duration -> vm.sendVoice(otherUid, uri, duration) },
                    onError = vm::showMessage,
                    busy = busy,
                    context = context
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Messages and temporary media are retained for up to 24 hours. Drive-restored archive items are local only.",
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)).padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (messages.isEmpty()) EmptyState("Start the conversation", "Say hello. Kotha Bolbo supports private one-to-one messages.")
                else LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(messages, key = { _, item -> item.id }) { index, message ->
                        if (index == 0 || TimeUtils.dateLabel(messages[index - 1].sortTime) != TimeUtils.dateLabel(message.sortTime)) DateDivider(TimeUtils.dateLabel(message.sortTime))
                        MessageBubble(
                            message = message,
                            mine = message.senderId == me?.uid,
                            onLongPress = { selected = message },
                            onReply = { reply = message },
                            onImageClick = { zoomUrl = message.imageUrl },
                            onQuoteClick = {
                                val target = messages.indexOfFirst { it.id == message.replyTo?.id }
                                if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                                else vm.showMessage("Original message not available")
                            }
                        )
                    }
                }
            }
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: messages.lastIndex
            AnimatedVisibility(messages.isNotEmpty() && lastVisible < messages.lastIndex - 2, Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                androidx.compose.material3.SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } }) { Icon(Icons.Filled.KeyboardArrowDown, "New messages") }
            }
        }
    }
}

@Composable
private fun DateDivider(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f)); Text(label, Modifier.padding(horizontal = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean, onLongPress: () -> Unit, onReply: () -> Unit, onImageClick: () -> Unit, onQuoteClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 170.dp.toPx() }
    var drag by remember { mutableStateOf(0f) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 310.dp)
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                        onDragEnd = {
                            val correctDirection = if (mine) drag < -threshold else drag > threshold
                            if (correctDirection) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onReply() }
                            drag = 0f
                        }
                    )
                }
                .combinedClickable(onClick = {}, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress() })
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(if (mine) 18.dp else 6.dp, 18.dp, if (mine) 6.dp else 18.dp, 18.dp)
                ).padding(10.dp)
        ) {
            message.replyTo?.let { quote ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .55f)).clickableNoIndication(onQuoteClick).padding(8.dp)) {
                    Text(quote.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(quote.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
            }
            when (message.type) {
                "image" -> {
                    AsyncImage(message.imageUrl, "Shared image", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).clickableNoIndication(onImageClick), contentScale = ContentScale.Crop)
                    if (message.text.isNotBlank()) Text(message.text, Modifier.padding(top = 7.dp))
                }
                "voice" -> VoiceMessage(message.voiceUrl, message.voiceDuration)
                else -> Text(message.text)
            }
            Row(Modifier.align(Alignment.End).padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (message.edited) Text("edited · ", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic)
                Text(TimeUtils.chatTime(message.sortTime), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (mine) Text(" · ${MessageStatus.of(message).label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun VoiceMessage(url: String, duration: Long) {
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(url) { onDispose { player?.release() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (playing) { player?.pause(); playing = false }
            else {
                val active = player ?: MediaPlayer().apply {
                    setDataSource(url); setOnPreparedListener { it.start(); playing = true }; setOnCompletionListener { playing = false; it.seekTo(0) }; prepareAsync()
                }.also { player = it }
                if (active.isPlaying.not() && active.currentPosition > 0) { active.start(); playing = true }
            }
        }) { Icon(if (playing) Icons.Outlined.Clear else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play voice message") }
        Column { Text("Voice message", fontWeight = FontWeight.Medium); Text(formatSeconds(duration), style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickableNoIndication(action).padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Text(label, Modifier.padding(start = 16.dp)) }
}

@Composable
private fun EditMessageDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit message") },
        text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 2) },
        confirmButton = { Button(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    onImage: () -> Unit,
    onSend: () -> Unit,
    onVoice: (Uri, Long) -> Unit,
    onError: (String) -> Unit,
    busy: Boolean,
    context: Context
) {
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { recorder.start(); recording = true; paused = false }
        else onError("Microphone permission is required to record a voice message.")
    }
    DisposableEffect(Unit) { onDispose { recorder.discard() } }
    LaunchedEffect(recording, paused) {
        while (recording) { elapsed = recorder.duration(); delay(250) }
    }
    if (recording) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Call, null, tint = MaterialTheme.colorScheme.error)
            Text(formatDuration(elapsed), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconButton({ if (paused) recorder.resume() else recorder.pause(); paused = !paused }) { Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Clear, if (paused) "Resume" else "Pause") }
            IconButton({ recorder.discard(); recording = false; elapsed = 0 }) { Icon(Icons.Outlined.Delete, "Discard") }
            IconButton({
                val result = recorder.stop(); recording = false; paused = false
                if (result != null && result.first.length() >= 1_000) onVoice(Uri.fromFile(result.first), result.second)
                else onError("Recording too short")
                elapsed = 0
            }) { Icon(Icons.Filled.Send, "Send voice message", tint = MaterialTheme.colorScheme.primary) }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onImage) { Icon(Icons.Outlined.AccountBox, "Send image") }
            OutlinedTextField(
                text, onText, Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 5,
                shape = RoundedCornerShape(22.dp)
            )
            if (text.isNotBlank()) IconButton(onSend, enabled = !busy) { Icon(Icons.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary) }
            else IconButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    recorder.start(); recording = true
                } else permission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(Icons.Filled.Call, "Record voice message") }
        }
    }
}

private class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var pausedAt = 0L
    private var pausedTotal = 0L

    fun start() {
        discard()
        file = File(context.cacheDir, "kb_voice_${System.currentTimeMillis()}.m4a")
        recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(24_000)
            setAudioSamplingRate(16_000)
            setOutputFile(file!!.absolutePath)
            prepare(); start()
        }
        startedAt = System.currentTimeMillis(); pausedTotal = 0; pausedAt = 0
    }

    fun pause() { runCatching { recorder?.pause(); pausedAt = System.currentTimeMillis() } }
    fun resume() { runCatching { recorder?.resume(); if (pausedAt > 0) pausedTotal += System.currentTimeMillis() - pausedAt; pausedAt = 0 } }
    fun duration(): Long = if (startedAt == 0L) 0 else ((if (pausedAt > 0) pausedAt else System.currentTimeMillis()) - startedAt - pausedTotal).coerceAtLeast(0)
    fun stop(): Pair<File, Long>? {
        val duration = duration()
        val output = file
        runCatching { recorder?.stop() }
        recorder?.release(); recorder = null; file = null; startedAt = 0
        return output?.let { it to duration }
    }
    fun discard() { runCatching { recorder?.stop() }; recorder?.release(); recorder = null; file?.delete(); file = null; startedAt = 0 }
}

private fun ChatMessage.preview(): String = when (type) { "image" -> "📷 Photo"; "voice" -> "🎤 Voice message"; else -> text }
private fun ChatMessage.asReply(currentName: String): ReplyReference = ReplyReference(id, preview().take(140), if (senderId.isNotBlank()) currentName else "")
private fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
private fun formatSeconds(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier = this.then(Modifier.combinedClickable(onClick = onClick))
