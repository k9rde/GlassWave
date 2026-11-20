package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HardwarePttBridge
import com.example.ui.WalkieTalkieScreen
import com.example.ui.WalkieTalkieViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.theme.GlassWaveTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val maxRefreshRate = requestHighestRefreshRate()

        setContent {
            GlassWaveTheme {
                AppContent(maxRefreshRate = maxRefreshRate)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (HardwarePttBridge.isEnabled &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        HardwarePttBridge.onVolumeDownPress?.invoke()
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    HardwarePttBridge.onVolumeDownRelease?.invoke()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun requestHighestRefreshRate(): Int {
        return try {
            val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val highestMode = activeDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
            if (highestMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = highestMode.modeId
                window.attributes = params
                highestMode.refreshRate.roundToInt()
            } else {
                60
            }
        } catch (_: Exception) {
            60
        }
    }
}

@Composable
private fun AppContent(maxRefreshRate: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    val startupPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val missing = requiredPermissions.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            startupPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    if (currentUser == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val uid = currentUser!!.uid
        key(uid) {
            val vm: WalkieTalkieViewModel = viewModel(
                key = uid,
                factory = WalkieTalkieViewModel.Factory(
                    application = context.applicationContext as Application,
                    currentUserId = uid
                )
            )
            LaunchedEffect(maxRefreshRate) {
                vm.updateRefreshRate(maxRefreshRate)
            }
            WalkieTalkieScreen(
                viewModel = vm,
                onSignOutClick = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}
