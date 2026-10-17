// =================================================================
//                     ANDROID NATIVE BRIDGE & UI                      
//                  File: MainActivity.kt (Finalized)                 
// =================================================================
package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

  private var tts: TextToSpeech? = null
  private var isTtsReady = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    tts = TextToSpeech(this, this)
    setContent {
      NovaAiScreen(tts = tts, isTtsReady = isTtsReady)
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      tts?.language = Locale.US
      isTtsReady = true
    }
  }

  override fun onDestroy() {
    tts?.stop()
    tts?.shutdown()
    super.onDestroy()
  }
}

class AndroidBridge(
  private val context: Context,
  private val tts: TextToSpeech?,
  private val isTtsReady: () -> Boolean
) {
  @JavascriptInterface
  fun vibrate(milliseconds: Long) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        @Suppress("DEPRECATION")
        vibrator?.vibrate(milliseconds)
      }
    } catch (_: Exception) {}
  }

  @JavascriptInterface
  fun speak(text: String) {
    try {
      if (isTtsReady()) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova_tts_${System.currentTimeMillis()}")
      }
    } catch (_: Exception) {}
  }

  @JavascriptInterface
  fun getBatteryLevel(): Int {
    return try {
      val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
      bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 88
    } catch (_: Exception) { 88 }
  }

  @JavascriptInterface
  fun toggleFlashlight(state: Boolean) {
    try {
      val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
      val cameraId = cameraManager?.cameraIdList?.firstOrNull()
      if (cameraId != null) {
        cameraManager.setTorchMode(cameraId, state)
      }
    } catch (_: Exception) {}
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NovaAiScreen(tts: TextToSpeech?, isTtsReady: Boolean) {
  val context = LocalContext.current
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { _ -> }

  LaunchedEffect(Unit) {
    val neededPermissions = mutableListOf<String>()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      neededPermissions.add(Manifest.permission.RECORD_AUDIO)
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      neededPermissions.add(Manifest.permission.CAMERA)
    }
    if (neededPermissions.isNotEmpty()) {
      permissionLauncher.launch(neededPermissions.toTypedArray())
    }
  }

  Scaffold(modifier = Modifier.fillMaxSize().background(Color(0xFF060913))) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
          WebView(ctx).apply {
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              mediaPlaybackRequiresUserGesture = false
              allowFileAccess = true
              allowContentAccess = true
            }
            setBackgroundColor(android.graphics.Color.parseColor("#060913"))
            webChromeClient = object : WebChromeClient() {
              override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
              }
            }
            webViewClient = WebViewClient()
            addJavascriptInterface(AndroidBridge(ctx, tts, { isTtsReady }), "AndroidBridge")
            loadUrl("file:///android_asset/index.html")
          }
        }
      )
    }
  }
}

