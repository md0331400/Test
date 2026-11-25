package com.amisayem.kothabolbo

import android.Manifest
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.amisayem.kothabolbo.bridge.AndroidBridge
import com.amisayem.kothabolbo.databinding.ActivityMainBinding
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private var pendingChooser: ValueCallback<Array<Uri>>? = null
    private var pendingMediaPermission: PermissionRequest? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) { js("window.onNativeConnectivityChanged&&window.onNativeConnectivityChanged(true)") }
        override fun onLost(network: android.net.Network) { js("window.onNativeConnectivityChanged&&window.onNativeConnectivityChanged(false)") }
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val document = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { js("window.onNativeFileSelected&&window.onNativeFileSelected(${JSONObject.quote(it.toString())})") } }
    private val image = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { js("window.onNativeFileSelected&&window.onNativeFileSelected(${JSONObject.quote(it.toString())})") } }
    private val create = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(pendingSave.toByteArray()) } } }
    private var pendingSave = ""
    private var pendingDriveBackupJson: String? = null
    private var pendingDriveRestore = false
    private val driveScope = Scope("https://www.googleapis.com/auth/drive.appdata")
    private val googleClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken("343828860232-n7korusids9jcse961go8tqne8abob59.apps.googleusercontent.com")
            .requestScopes(driveScope)
            .build()
        GoogleSignIn.getClient(this, options)
    }
    override fun onCreate(state: Bundle?) {
        installSplashScreen(); super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding=ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        // The local HTML applies these system-bar insets through the Android bridge.
        // Do not pad WebView here: CSS fixed screens otherwise receive the inset twice.
        setupWebView(); loadApp(); fetchNativeFcmToken(); deliverNotificationAction(intent)
        (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).registerDefaultNetworkCallback(networkCallback)
    }
    fun startGoogleSignIn() { startActivityForResult(googleClient.signInIntent, 702) }
    fun googleSignOut() { googleClient.signOut() }
    /** Wait for the old Google session to sign out before opening the account picker. */
    fun changeGoogleDriveAccount() {
        googleClient.signOut().addOnCompleteListener { startGoogleSignIn() }
    }
    fun backupToGoogleDrive(json: String) {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            pendingDriveBackupJson = json
            startGoogleSignIn()
            return
        }
        if (!GoogleSignIn.hasPermissions(account, driveScope)) {
            pendingDriveBackupJson = json
            GoogleSignIn.requestPermissions(this, 703, account, driveScope)
            return
        }
        uploadDriveBackup(json)
    }

    fun restoreGoogleDriveBackup() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) { pendingDriveRestore = true; startGoogleSignIn(); return }
        if (!GoogleSignIn.hasPermissions(account, driveScope)) { pendingDriveRestore = true; GoogleSignIn.requestPermissions(this, 703, account, driveScope); return }
        downloadDriveBackup()
    }

    private fun downloadDriveBackup() {
        pendingDriveRestore = false
        DriveBackupManager.download(this) { ok, payload ->
            js("window.onNativeDriveRestoreData&&window.onNativeDriveRestoreData($ok,${JSONObject.quote(payload)})")
        }
    }

    fun deleteGoogleDriveBackup() {
        DriveBackupManager.delete(this) { ok, message ->
            js("window.onNativeDriveDeleteResult&&window.onNativeDriveDeleteResult($ok,${JSONObject.quote(message)})")
        }
    }

    private fun uploadDriveBackup(json: String) {
        DriveBackupManager.upload(this, json) { ok, message ->
            js("window.onNativeDriveBackupResult&&window.onNativeDriveBackupResult($ok,${JSONObject.quote(message)})")
        }
    }
    private fun fetchNativeFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            getSharedPreferences("native", MODE_PRIVATE).edit().putString("fcm", token).apply()
            js("window.onNativeFcmToken&&window.onNativeFcmToken(${JSONObject.quote(token)})")
        }.addOnFailureListener { android.util.Log.e("KothaBolbo", "FCM token could not be created", it) }
    }
    private fun setupWebView() = binding.webView.apply {
        setBackgroundColor(Color.rgb(10,14,26)); isVerticalScrollBarEnabled=false; overScrollMode=android.view.View.OVER_SCROLL_NEVER
        settings.apply { javaScriptEnabled=true; domStorageEnabled=true; databaseEnabled=true; mediaPlaybackRequiresUserGesture=false; allowFileAccess=false; allowContentAccess=true; setSupportZoom(false); builtInZoomControls=false; displayZoomControls=false; cacheMode=if (isOnline()) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK; mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW; userAgentString += " KothaBolboAndroid/1.0" }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled=true
        addJavascriptInterface(AndroidBridge(this@MainActivity,permissions),"AndroidBridge")
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                pendingChooser?.onReceiveValue(null); pendingChooser = cb
                val accepts = params.acceptTypes.orEmpty().filter { it.isNotBlank() }
                // Chat attachments and the profile avatar both accept only images.
                val wantsImage = accepts.isEmpty() || accepts.any { it.equals("image/*", true) || it.startsWith("image/", true) }
                val wantsVideo = accepts.any { it.startsWith("video/", true) }
                if (wantsImage) {
                    // Image-only: never fall back to the file browser. Android 13+ uses
                    // the system Photo Picker (gallery, no storage permission). Older
                    // devices use a gallery-first GET_CONTENT intent so we do NOT land
                    // in Files/File Manager.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        startActivityForResult(Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"), 900)
                    } else {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*"))
                        }
                        startActivityForResult(Intent.createChooser(intent, "Select image"), 900)
                    }
                    return true
                }
                if (wantsVideo) {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "video/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*"))
                    }
                    startActivityForResult(Intent.createChooser(intent, "Select video"), 900)
                    return true
                }
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = accepts.firstOrNull() ?: "*/*"
                }
                startActivityForResult(Intent.createChooser(intent, "Select file"), 900)
                return true
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val audio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    val video = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    val required = buildList { if (audio) add(Manifest.permission.RECORD_AUDIO); if (video) add(Manifest.permission.CAMERA) }.toTypedArray()
                    if (required.isEmpty()) { request.deny(); return@runOnUiThread }
                    if (required.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) request.grant(request.resources)
                    else { pendingMediaPermission?.deny(); pendingMediaPermission = request; requestPermissions(required, 451) }
                }
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) { if (pendingMediaPermission == request) pendingMediaPermission = null }
        }
        webViewClient=object:WebViewClient() { override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean { if(!r.isForMainFrame) return false; val u=r.url; return if(u.host=="kotha-bolbo-aso.firebaseapp.com" || u.host=="kothabolbo.vercel.app") false else { startActivity(Intent(Intent.ACTION_VIEW,u)); true } } }
    }
    private fun loadApp() { val source=assets.open("index.html").bufferedReader().use { it.readText() }; binding.webView.loadDataWithBaseURL("https://kothabolbo.vercel.app/",source,"text/html","UTF-8",null) }
    override fun onActivityResult(r:Int,c:Int,d:Intent?) {
        super.onActivityResult(r,c,d)
        if (r == 900) { pendingChooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c,d)); pendingChooser=null }
        if (r == 702) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(d)
            try {
                val account = task.getResult(Exception::class.java)
                val json = JSONObject().put("idToken", account.idToken ?: "").put("email", account.email ?: "").put("name", account.displayName ?: "").put("photo", account.photoUrl?.toString() ?: "").toString()
                val pendingBackup = pendingDriveBackupJson
                if (pendingBackup != null || pendingDriveRestore) {
                    // This login was opened only for Drive; never route it into Firebase account login/linking.
                    js("window.onNativeDriveBackupAccount&&window.onNativeDriveBackupAccount(${JSONObject.quote(account.email ?: "")})")
                    if (!GoogleSignIn.hasPermissions(account, driveScope)) GoogleSignIn.requestPermissions(this, 703, account, driveScope)
                    else if (pendingBackup != null) { pendingDriveBackupJson = null; uploadDriveBackup(pendingBackup) }
                    else downloadDriveBackup()
                } else {
                    js("window.onNativeGoogleAccount&&window.onNativeGoogleAccount(${JSONObject.quote(json)})")
                }
            } catch (e: Exception) { pendingDriveBackupJson = null; js("window.onNativeGoogleError&&window.onNativeGoogleError(${JSONObject.quote(e.message ?: "Google sign-in failed")})") }
        }
        if (r == 703) {
            val pending = pendingDriveBackupJson
            val restoring = pendingDriveRestore
            pendingDriveBackupJson = null
            pendingDriveRestore = false
            if (c == RESULT_OK && pending != null) uploadDriveBackup(pending)
            else if (c == RESULT_OK && restoring) downloadDriveBackup()
            else if (restoring) js("window.onNativeDriveRestoreData&&window.onNativeDriveRestoreData(false,'DRIVE_PERMISSION_ERROR')")
            else js("window.onNativeDriveBackupResult&&window.onNativeDriveBackupResult(false,'Google Drive permission was not granted')")
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 451) {
            val request = pendingMediaPermission; pendingMediaPermission = null
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) request?.grant(request.resources) else request?.deny()
        }
    }
    override fun onDestroy() { try { (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}; super.onDestroy() }
    override fun onResume() { super.onResume(); (application as KothaBolboApplication).setForeground(true) }
    override fun onPause() { (application as KothaBolboApplication).setForeground(false); super.onPause() }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); deliverNotificationAction(intent) }
    private fun deliverNotificationAction(intent: Intent) {
        val chat = intent.getStringExtra("chat") ?: return
        val action = intent.getStringExtra("notificationAction") ?: "open"
        val reply = intent.getStringExtra("notificationReply") ?: ""
        val call = "window.handleNativeNotificationAction&&window.handleNativeNotificationAction(${JSONObject.quote(action)},${JSONObject.quote(chat)},${JSONObject.quote(reply)})"
        // Deliver exactly once. If the module has not loaded yet, retry inside that same page.
        val script = "(function attemptNativeAction(){if(window.handleNativeNotificationAction){$call}else{setTimeout(attemptNativeAction,400)}})()"
        binding.webView.postDelayed({ js(script) }, 450)
    }
    fun js(script:String)=runOnUiThread { binding.webView.evaluateJavascript(script,null) }
    fun isOnline():Boolean { val n=(getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork ?: return false; return (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true }
    fun pickImage()=image.launch("image/*")
    fun pickFile()=document.launch(arrayOf("*/*"))
    fun saveFile(name:String,content:String) { pendingSave=content; create.launch(name) }
    fun download(url:String,name:String) { (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(DownloadManager.Request(Uri.parse(url)).setTitle(name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)) }
    fun setKeyboard(show:Boolean) { val imm=getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager; if(show) { binding.webView.requestFocus(); imm.showSoftInput(binding.webView,InputMethodManager.SHOW_IMPLICIT) } else imm.hideSoftInputFromWindow(binding.webView.windowToken,0) }
    fun setImmersiveMode(on:Boolean) { if(on) window.insetsController?.hide(WindowInsets.Type.systemBars()) else window.insetsController?.show(WindowInsets.Type.systemBars()) }
    fun haptic()=binding.webView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
    fun logNativeEvent(name:String,json:String) { try { val b=Bundle(); JSONObject(json).keys().forEach { k -> b.putString(k,JSONObject(json).optString(k)) }; FirebaseAnalytics.getInstance(this).logEvent(name,b) } catch(_:Exception){} }
    override fun onBackPressed() { binding.webView.evaluateJavascript("(document.getElementById('conv-screen')?.classList.contains('open')||document.getElementById('profile-page')?.classList.contains('open'))") { open -> if(open=="true") js("window.history.back()") else super.onBackPressed() } }
}
