package com.amisayem.kothabolbo.bridge

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.JavascriptInterface
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import com.amisayem.kothabolbo.MainActivity
import com.amisayem.kothabolbo.NotificationHelper
import com.amisayem.kothabolbo.R
import org.json.JSONObject
import java.io.File

class AndroidBridge(private val activity: MainActivity, private val permissionRequest: ActivityResultLauncher<Array<String>>) {
    private var nativeRecorder: MediaRecorder? = null
    private var nativeVoiceFile: File? = null
    private fun run(block: () -> Unit) = activity.runOnUiThread(block)
    private fun json(vararg p: Pair<String, Any?>) = JSONObject().apply { p.forEach { put(it.first, it.second) } }.toString()
    @JavascriptInterface fun toast(message: String) = run { android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show() }
    @JavascriptInterface fun snackbar(message: String) = toast(message)
    @JavascriptInterface fun copyText(text: String) = run { (activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Kotha Bolbo", text)); toast("Copied") }
    @JavascriptInterface fun pasteText(): String = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString() ?: ""
    @JavascriptInterface fun share(text: String) = run { activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share with")) }
    @JavascriptInterface fun openUrl(url: String) = run { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    @JavascriptInterface fun closeApp() = run { activity.finish() }
    @JavascriptInterface fun exitApp() = run { activity.finishAndRemoveTask() }
    @JavascriptInterface fun getVersionName() = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "1.0.0"
    @JavascriptInterface fun getVersionCode() = if (Build.VERSION.SDK_INT >= 28) activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode.toString() else "1"
    @JavascriptInterface fun getDeviceInfo() = json("manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "sdk" to Build.VERSION.SDK_INT)
    @JavascriptInterface fun getAndroidVersion() = Build.VERSION.RELEASE
    @JavascriptInterface fun getStatusBarInset(): Int = ViewCompat.getRootWindowInsets(activity.binding.webView)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    @JavascriptInterface fun getNavigationBarInset(): Int = ViewCompat.getRootWindowInsets(activity.binding.webView)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    @JavascriptInterface fun getBattery(): Int { val i=activity.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)); return i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1 }
    @JavascriptInterface fun isOnline() = activity.isOnline()
    @JavascriptInterface fun isAppForeground() = com.amisayem.kothabolbo.KothaBolboApplication.instance.isAppInForeground
    @JavascriptInterface fun isDarkMode() = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    @JavascriptInterface fun permissionStatus(permission: String) = if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
    @JavascriptInterface fun requestPermission(permission: String) = run { permissionRequest.launch(arrayOf(permission)) }
    @JavascriptInterface fun camera() = run { permissionRequest.launch(arrayOf(Manifest.permission.CAMERA)) }
    @JavascriptInterface fun gallery() = run { activity.pickImage() }
    @JavascriptInterface fun filePicker() = run { activity.pickFile() }
    @JavascriptInterface fun imagePicker() = gallery()
    @JavascriptInterface fun downloadFile(url: String, filename: String) = run { android.webkit.URLUtil.guessFileName(url, null, null).also { activity.download(url, filename.ifBlank { it }) } }
    @JavascriptInterface fun openFile() = filePicker()
    @JavascriptInterface fun saveFile(filename: String, content: String) = run { activity.saveFile(filename, content) }
    @JavascriptInterface fun hapticFeedback() = run { activity.binding.webView.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
    @JavascriptInterface fun vibrate(ms: Long) = run { (activity.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).let { if(Build.VERSION.SDK_INT>=26) it.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") it.vibrate(ms) } }
    @JavascriptInterface fun keyboard(show: Boolean) = run { activity.setKeyboard(show) }
    @JavascriptInterface fun setStatusBar(color: String) = run { activity.window.statusBarColor=Color.parseColor(color) }
    @JavascriptInterface fun setNavigationBar(color: String) = run { activity.window.navigationBarColor=Color.parseColor(color) }
    @JavascriptInterface fun immersive(enabled: Boolean) = run { activity.setImmersiveMode(enabled) }
    @JavascriptInterface fun restart() = run { activity.recreate() }
    @JavascriptInterface fun getFcmToken(): String = activity.getSharedPreferences("native", Context.MODE_PRIVATE).getString("fcm", "") ?: ""
    @JavascriptInterface fun googleSignIn() = run { activity.startGoogleSignIn() }
    @JavascriptInterface fun googleSignOut() = run { activity.googleSignOut() }
    @JavascriptInterface fun changeGoogleDriveAccount() = run { activity.changeGoogleDriveAccount() }
    @JavascriptInterface fun isGoogleConnected(): Boolean = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(activity) != null
    @JavascriptInterface fun backupToGoogleDrive(json: String) = activity.backupToGoogleDrive(json)
    @JavascriptInterface fun deleteGoogleDriveBackup() = activity.deleteGoogleDriveBackup()
    @JavascriptInterface fun restoreGoogleDriveBackup() = activity.restoreGoogleDriveBackup()
    @JavascriptInterface fun saveFirebaseSession(uid: String, idToken: String, refreshToken: String, displayName: String, photoUrl: String) {
        activity.getSharedPreferences("native_session", Context.MODE_PRIVATE).edit()
            .putString("uid", uid).putString("idToken", idToken).putString("refreshToken", refreshToken)
            .putString("displayName", displayName).putString("photoUrl", photoUrl).apply()
    }
    @JavascriptInterface fun analyticsEvent(name: String, paramsJson: String) { activity.logNativeEvent(name, paramsJson) }
    @JavascriptInterface fun log(message: String) { android.util.Log.d("KothaBolbo", message) }
    @JavascriptInterface fun nativeDialog(title: String, message: String) = run { AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("OK", null).show() }
    @JavascriptInterface fun datePicker() = run { val c=java.util.Calendar.getInstance(); DatePickerDialog(activity, { _,y,m,d -> activity.js("window.onNativeDateSelected&&window.onNativeDateSelected('$y-${m+1}-$d')") },c.get(1),c.get(2),c.get(5)).show() }
    @JavascriptInterface fun timePicker() = run { val c=java.util.Calendar.getInstance(); TimePickerDialog(activity, { _,h,m -> activity.js("window.onNativeTimeSelected&&window.onNativeTimeSelected('$h:$m')") },c.get(11),c.get(12),true).show() }
    @JavascriptInterface fun openSettings() = run { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))) }
    @JavascriptInterface fun openNotificationSettings() = run { activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName)) }
    @JavascriptInterface fun isAppInstalled(packageName: String) = try { activity.packageManager.getPackageInfo(packageName,0); true } catch(_:Exception) { false }
    @JavascriptInterface fun startNativeVoiceRecording(): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            cancelNativeVoiceRecording()
            nativeVoiceFile = File(activity.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            nativeRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(nativeVoiceFile!!.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(24000)
                setAudioSamplingRate(16000)
                prepare(); start()
            }
            true
        } catch (_: Exception) { cancelNativeVoiceRecording(); false }
    }
    @JavascriptInterface fun stopNativeVoiceRecording(): String = try {
        nativeRecorder?.stop(); nativeRecorder?.release(); nativeRecorder = null
        val file = nativeVoiceFile?.takeIf { it.exists() && it.length() > 0 }
        if (file == null) "" else {
            val durationMs = try { MediaMetadataRetriever().use { r -> r.setDataSource(file.absolutePath); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } } catch (_: Exception) { 0L }
            JSONObject().put("audio", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)).put("durationMs", durationMs).toString()
        }
    } catch (_: Exception) { "" } finally { nativeVoiceFile?.delete(); nativeVoiceFile = null }
    @JavascriptInterface fun cancelNativeVoiceRecording() { try { nativeRecorder?.reset(); nativeRecorder?.release() } catch (_: Exception) {}; nativeRecorder = null; nativeVoiceFile?.delete(); nativeVoiceFile = null }
    @JavascriptInterface fun playSound(soundName: String) = run { try { val soundRes = when { soundName.contains("notification") -> R.raw.notification_sound; soundName.contains("message_sound") -> R.raw.message_sound; else -> R.raw.in_app_message_sound }; MediaPlayer.create(activity, soundRes).apply { setOnCompletionListener { it.release() }; start() } } catch(_:Exception) { try { android.media.RingtoneManager.getRingtone(activity, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)).play() } catch(_:Exception) { activity.haptic() } } }
    @JavascriptInterface fun cancelNotificationForChat(chatId: String) = run { androidx.core.app.NotificationManagerCompat.from(activity).cancel(chatId.hashCode()) }
    @JavascriptInterface fun notification(title:String, body:String, dataJson:String) = run { val o=try { JSONObject(dataJson) } catch(_:Exception){JSONObject()}; NotificationHelper.show(activity, mapOf("title" to title,"body" to body,"chat" to o.optString("chat"))) }
    @JavascriptInterface fun biometricAuthentication() = run { val executor=ContextCompat.getMainExecutor(activity); BiometricPrompt(activity,executor,object:BiometricPrompt.AuthenticationCallback(){override fun onAuthenticationSucceeded(r:BiometricPrompt.AuthenticationResult){activity.js("window.onNativeBiometricResult&&window.onNativeBiometricResult(true)")}}).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Verify your identity").setNegativeButtonText("Cancel").build()) }
}
