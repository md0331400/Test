package com.amisayem.kothabolbo.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amisayem.kothabolbo.R
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.ui.components.BrandBackground
import com.amisayem.kothabolbo.ui.components.GlassCard

@Composable
fun PrivacyGateScreen(onAccept: () -> Unit) {
    val context = LocalContext.current
    BrandBackground {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.kb_app_icon), "Kotha Bolbo", Modifier.size(112.dp))
            Spacer(Modifier.height(20.dp))
            Text("Your conversations, your control", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Before continuing, review how Kotha Bolbo handles your data.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GateRow(Icons.Outlined.DateRange, "Messages expire after 24 hours", "Drive-restored copies stay only in your private local archive.")
                    GateRow(Icons.Outlined.Lock, "Private by default", "Email, phone, and date-of-birth parts are masked unless you make them public.")
                    GateRow(Icons.Outlined.Refresh, "Drive backup is optional", "A backup is locked to one Gmail account per Kotha Bolbo user.")
                    GateRow(Icons.Outlined.MailOutline, "One-to-one messaging", "No groups, forwarding, public comments, friend requests, or blocking.")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                androidx.compose.material3.TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.PRIVACY_POLICY_URL))) }) { Text("Privacy Policy") }
                androidx.compose.material3.TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.ACCOUNT_DELETION_URL))) }) { Text("Data Deletion") }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Accept / Continue") }
            Spacer(Modifier.height(10.dp))
            Text("You can review privacy controls later in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GateRow(icon: ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class OnboardPage(val icon: ImageVector, val title: String, val detail: String)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardPage(Icons.Outlined.MailOutline, "Chat with friends", "Send text, photos, and voice messages in real time."),
            OnboardPage(Icons.Outlined.DateRange, "Find people", "Use People Search from Chats to find another registered user."),
            OnboardPage(Icons.Outlined.Lock, "Instant notifications", "Receive message alerts and reply or send a Like without opening the app."),
            OnboardPage(Icons.Outlined.Refresh, "Your data, your control", "Manage profile privacy and optional Google Drive backup from Settings.")
        )
    }
    var page by remember { mutableIntStateOf(0) }
    val item = pages[page]
    BrandBackground {
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page < pages.lastIndex) OutlinedButton(onClick = onDone) { Text("Skip") }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(136.dp).background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondary.copy(alpha = .25f))),
                        CircleShape
                    ), contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(34.dp))
                Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(item.detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.indices.forEach { index ->
                        Box(Modifier.size(if (index == page) 24.dp else 8.dp, 8.dp).background(
                            if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape
                        ))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > 0) OutlinedButton({ page-- }, Modifier.weight(1f).height(52.dp)) { Text("Back") }
                Button(
                    onClick = { if (page == pages.lastIndex) onDone() else page++ },
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text(if (page == pages.lastIndex) "Get started" else "Next") }
            }
        }
    }
}
