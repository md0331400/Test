package com.amisayem.kothabolbo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.ui.KothaApp
import com.amisayem.kothabolbo.ui.KothaViewModel
import com.amisayem.kothabolbo.ui.theme.KothaBolboTheme

class MainActivity : ComponentActivity() {
    private val vm: KothaViewModel by viewModels {
        KothaViewModel.Factory((application as KothaBolboApplication).container)
    }
    private var pendingChat by mutableStateOf<String?>(null)
    private var pendingText by mutableStateOf<String?>(null)
    private var pendingActionId by mutableStateOf<String?>(null)
    private val consumedActionIds = LinkedHashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent {
            val prefs by vm.preferences.collectAsStateWithLifecycle()
            KothaBolboTheme(prefs.appearance, prefs.accent) {
                KothaApp(
                    vm = vm,
                    pendingChat = pendingChat,
                    pendingText = pendingText,
                    pendingActionId = pendingActionId,
                    consumePendingIntent = {
                        pendingActionId?.let { id ->
                            consumedActionIds.add(id)
                            while (consumedActionIds.size > 50) consumedActionIds.remove(consumedActionIds.first())
                        }
                        pendingChat = null; pendingText = null; pendingActionId = null
                        intent.removeExtra(AppConstants.EXTRA_CHAT)
                        intent.removeExtra("pendingText")
                        intent.removeExtra("pendingActionId")
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(source: Intent?) {
        source ?: return
        val chat = source.getStringExtra(AppConstants.EXTRA_CHAT)
            ?: source.data?.getQueryParameter("chat")
            ?: source.getStringExtra("chat")
        val actionId = source.getStringExtra("pendingActionId")
        if (actionId != null && actionId in consumedActionIds) return
        if (!chat.isNullOrBlank()) {
            pendingChat = chat
            pendingText = source.getStringExtra("pendingText")
            pendingActionId = actionId
        }
    }
}
