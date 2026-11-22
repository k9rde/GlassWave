package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.AudioOutputRoute
import com.example.data.CloudRoom
import com.example.ui.components.ActivityStatsDialog
import com.example.ui.components.GlassModeControlButton
import com.example.ui.components.LiquidAuroraBackground
import com.example.ui.components.LiquidGlassPttOrb
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.LuxuryGlassIconBadge
import com.example.ui.components.MemberAvatarCircle
import com.example.ui.components.MemberStatusDialog
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.RoomQrShareDialog
import com.example.ui.theme.ElectricSapphire
import com.example.ui.theme.GlassThemeOption
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidCyan
import com.example.ui.theme.LocalGlassAccent
import com.example.ui.theme.LuminousEmerald
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RubyCrimson
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WalkieTalkieScreen(
    viewModel: WalkieTalkieViewModel,
    onSignOutClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showChatDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showQrShareDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var joinRoomCodeInput by remember { mutableStateOf("") }
    var joinPasscodeInput by remember { mutableStateOf("") }
    var txSeconds by remember { mutableIntStateOf(0) }

    val glassTheme = remember(state.settings.glassTheme) {
        GlassThemeOption.fromKey(state.settings.glassTheme)
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        viewModel.onMicPermissionChanged(micGranted)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.sendQuickLocation { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "يلزم إذن الموقع لإرسال إحداثياتك للأصدقاء", Toast.LENGTH_SHORT).show()
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.cycleAudioRoute()
        } else {
            Toast.makeText(context, "يرجى منح إذن البلوتوث للتبديل إلى سماعة البلوتوث", Toast.LENGTH_SHORT).show()
        }
    }

    // Automatically request all required permissions on first entry
    LaunchedEffect(Unit) {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onMicPermissionChanged(micGranted)

        val missing = requiredPermissions.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            multiPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    // Keep screen awake when enabled in Settings
    DisposableEffect(state.settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (state.settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-launch APK installer if user returns after granting unknown-sources permission
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hardware Volume Button & Floating Bubble PTT callbacks stay managed by RadioSessionManager
    // so the floating bubble continues working even when the Activity is closed or swiped away.

    // Live transmission duration timer
    LaunchedEffect(state.isTransmitting) {
        if (state.isTransmitting) {
            txSeconds = 0
            while (true) {
                delay(1000L)
                txSeconds++
            }
        } else {
            txSeconds = 0
        }
    }

    CompositionLocalProvider(LocalGlassAccent provides glassTheme) {
        if (showSettings) {
            SettingsScreen(
                state = state,
                viewModel = viewModel,
                onBack = { showSettings = false },
                onSignOutClick = onSignOutClick
            )
            return@CompositionLocalProvider
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LiquidAuroraBackground(
                mode = state.mode,
                isTransmitting = state.isTransmitting,
                isReceiving = state.isReceiving
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                val availableHeight = maxHeight
                val orbOuterSize = when {
                    availableHeight < 650.dp -> 182.dp
                    availableHeight < 730.dp -> 204.dp
                    else -> 222.dp
                }
                val orbInnerSize = when {
                    availableHeight < 650.dp -> 148.dp
                    availableHeight < 730.dp -> 164.dp
                    else -> 178.dp
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = availableHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                // Top Glass Header Bar
                TopGlassHeader(
                    refreshRateHz = state.refreshRateHz,
                    rogerBeepEnabled = state.rogerBeepEnabled,
                    audioRoute = state.audioRoute,
                    iconOpacity = state.settings.glassOpacity,
                    onCycleAudioRoute = {
                        val nextIsBt = state.audioRoute == AudioOutputRoute.EARPIECE
                        if (nextIsBt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        ) {
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            viewModel.cycleAudioRoute()
                        }
                    },
                    onToggleRogerBeep = {
                        if (state.settings.hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        viewModel.toggleRogerBeep()
                    },
                    onOpenSettings = { showSettings = true }
                )

                // Cloud Room Card (Works across any house / any network!)
                CloudRoomGlassCard(
                    activeRoom = state.activeRoom,
                    memberStatus = state.settings.memberStatus,
                    onCreateRoomClick = { showCreateDialog = true },
                    onJoinRoomClick = {
                        joinRoomCodeInput = ""
                        joinPasscodeInput = ""
                        showJoinDialog = true
                    },
                    onCopyCode = { code ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("GlassWave Room", code))
                        Toast.makeText(context, "تم نسخ رمز الغرفة: $code", Toast.LENGTH_SHORT).show()
                    },
                    onShareRoom = { room ->
                        val shareText = "انضم معي في غرفة اللاسلكي (${room.roomName}) على تطبيق GlassWave!\nرمز الغرفة: ${room.roomCode}\nرابط تحميل التطبيق: https://k9rde.github.io/GlassWave/"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة دعوة الغرفة"))
                    },
                    onShowQrShare = { showQrShareDialog = true },
                    onPickStatus = { showStatusDialog = true },
                    onShowMembers = { showMembersDialog = true }
                )

            // Quick Actions Row (Ping, Replay, Chat, Location, Stats)
            if (state.activeRoom != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickActionGlassChip(
                        icon = Icons.Rounded.Campaign,
                        label = "نداء",
                        accentColor = RubyCrimson,
                        testTag = "ping_room_button",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.sendPing()
                        Toast.makeText(context, "تم إرسال نداء عاجل للغرفة", Toast.LENGTH_SHORT).show()
                    }

                    QuickActionGlassChip(
                        icon = Icons.Rounded.Replay,
                        label = "إعادة",
                        accentColor = LiquidCyan,
                        enabled = state.canReplayVoice,
                        testTag = "replay_voice_button",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.replayLastVoiceMessage()
                        Toast.makeText(context, "إعادة صوت: ${state.lastVoiceSenderName}", Toast.LENGTH_SHORT).show()
                    }

                    val chatLabel = if (state.chatMessages.isNotEmpty()) "دردشة(${state.chatMessages.size})" else "دردشة"
                    QuickActionGlassChip(
                        icon = Icons.Rounded.Chat,
                        label = chatLabel,
                        accentColor = LuminousEmerald,
                        testTag = "open_chat_button",
                        modifier = Modifier.weight(1f)
                    ) {
                        showChatDialog = true
                    }

                    QuickActionGlassChip(
                        icon = Icons.Rounded.LocationOn,
                        label = "موقعي",
                        accentColor = glassTheme.primary,
                        testTag = "share_location_button",
                        modifier = Modifier.weight(1f)
                    ) {
                        val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasLoc) {
                            Toast.makeText(context, "جاري تحديد موقعك…", Toast.LENGTH_SHORT).show()
                            viewModel.sendQuickLocation { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }

                    QuickActionGlassChip(
                        icon = Icons.Rounded.BarChart,
                        label = "إحصاء",
                        accentColor = ElectricSapphire,
                        testTag = "open_stats_button",
                        modifier = Modifier.weight(1f)
                    ) {
                        showStatsDialog = true
                    }
                }
            }

            // Emergency Ping Banner
            AnimatedVisibility(
                visible = state.pingBannerMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    accentColor = RubyCrimson,
                    tintAlpha = 0.28f,
                    borderAlpha = 0.65f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = GlassWhite, modifier = Modifier.size(20.dp))
                        Text(
                            text = state.pingBannerMessage ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = GlassWhite
                        )
                    }
                }
            }

            // Error or Mic Permission Banner
            AnimatedVisibility(
                visible = !state.hasMicPermission || state.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (!state.hasMicPermission) {
                    MicPermissionGlassBanner(
                        onRequestPermission = {
                            multiPermissionLauncher.launch(requiredPermissions)
                        }
                    )
                } else if (state.errorMessage != null) {
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        accentColor = RubyCrimson,
                        tintAlpha = 0.18f,
                        borderAlpha = 0.45f
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassWhite,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearErrorMessage() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "إغلاق التنبيه",
                                    tint = GlassWhite.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Centerpiece Status + Giant Liquid Glass PTT Orb
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveStatusPill(state = state, txSeconds = txSeconds)

                LiquidGlassPttOrb(
                    mode = state.mode,
                    isTransmitting = state.isTransmitting,
                    isReceiving = state.isReceiving,
                    micLevel = state.micLevel,
                    enabled = state.hasMicPermission && state.activeRoom != null,
                    outerSize = orbOuterSize,
                    orbSize = orbInnerSize,
                    onPressStart = {
                        if (state.settings.hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        viewModel.onPttPressStart()
                    },
                    onPressEnd = {
                        if (state.settings.hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        viewModel.onPttPressEnd()
                    }
                )

                Text(
                    text = when {
                        state.activeRoom == null -> "أنشئ غرفة أو انضم برمز صديقك للبدء"
                        !state.hasMicPermission -> stringResource(R.string.permission_title)
                        state.mode == RadioMode.ALWAYS_OFF -> stringResource(R.string.ptt_locked_off_hint)
                        state.mode == RadioMode.ALWAYS_ON -> stringResource(R.string.ptt_locked_on_hint)
                        state.isTransmitting -> stringResource(R.string.ptt_live_hint)
                        else -> stringResource(R.string.ptt_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Dual Luxurious Glass Controls: Always-On & Always-Off
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassModeControlButton(
                    title = stringResource(R.string.btn_always_on),
                    subtitle = if (state.mode == RadioMode.ALWAYS_ON) "مُفعّل الآن" else "بث مستمر",
                    icon = Icons.Rounded.Podcasts,
                    accentColor = LuminousEmerald,
                    isActive = state.mode == RadioMode.ALWAYS_ON,
                    testTag = "always_on_button",
                    onClick = {
                        if (!state.hasMicPermission) {
                            multiPermissionLauncher.launch(requiredPermissions)
                        } else if (state.activeRoom == null) {
                            showCreateDialog = true
                        } else {
                            if (state.settings.hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            viewModel.toggleAlwaysOn()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                GlassModeControlButton(
                    title = stringResource(R.string.btn_always_off),
                    subtitle = if (state.mode == RadioMode.ALWAYS_OFF) "مُغلق بالكامل" else "إيقاف كل الاتصال",
                    icon = Icons.Rounded.PowerSettingsNew,
                    accentColor = RubyCrimson,
                    isActive = state.mode == RadioMode.ALWAYS_OFF,
                    testTag = "always_off_button",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleAlwaysOff()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            }
            }
        }

    if (showCreateDialog) {
        var roomName by remember { mutableStateOf("") }
        var passcode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("إنشاء غرفة لاسلكي محمية", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it.take(40) },
                        label = { Text("اسم الغرفة (مثال: الأصدقاء)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = { passcode = it.take(24) },
                        label = { Text("رمز دخول الغرفة (4 أحرف/أرقام على الأقل)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createRoom(roomName, passcode)
                        showCreateDialog = false
                    },
                    enabled = passcode.trim().length >= 4,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiquidCyan,
                        contentColor = ObsidianDeep
                    )
                ) {
                    Text("إنشاء", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("الانضمام إلى غرفة صديق", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = joinRoomCodeInput,
                        onValueChange = { joinRoomCodeInput = it.uppercase().take(12) },
                        label = { Text("رمز الغرفة (مثال: WAVE42)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = joinPasscodeInput,
                        onValueChange = { joinPasscodeInput = it.take(24) },
                        label = { Text("رمز دخول الغرفة (كلمة المرور)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (joinRoomCodeInput.trim().isNotEmpty() && joinPasscodeInput.trim().length < 4) {
                        Text(
                            text = "يجب إدخال رمز الدخول (4 أحرف أو أرقام على الأقل) مع رمز الغرفة",
                            style = MaterialTheme.typography.labelSmall,
                            color = RubyCrimson
                        )
                    }
                    OutlinedButton(
                        onClick = { showQrScannerDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = glassTheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("مسح رمز QR بالكاميرا", color = glassTheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.joinRoomByCode(joinRoomCodeInput, joinPasscodeInput)
                        showJoinDialog = false
                    },
                    enabled = joinRoomCodeInput.trim().length >= 4 && joinPasscodeInput.trim().length >= 4,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LuminousEmerald,
                        contentColor = ObsidianDeep
                    )
                ) {
                    Text("انضمام", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }

    val updateInfo = state.availableUpdate
    if (state.showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isDownloadingUpdate) viewModel.dismissUpdateDialog()
            },
            properties = DialogProperties(
                dismissOnBackPress = !state.isDownloadingUpdate,
                dismissOnClickOutside = !state.isDownloadingUpdate
            ),
            containerColor = ObsidianSurface,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = "تحديث جديد",
                    tint = LiquidCyan,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "يتوفر تحديث جديد (v${updateInfo.latestVersion})",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "الميزات والإضافات الجديدة:",
                        color = LiquidCyan,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = updateInfo.updateMessage.ifBlank { "تحسينات جديدة في الأداء والاستقرار." },
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.isDownloadingUpdate) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { (state.updateDownloadProgress / 100f).coerceIn(0f, 1f) },
                                color = LuminousEmerald,
                                trackColor = GlassWhite.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "جاري التنزيل داخل التطبيق... ${state.updateDownloadProgress}%",
                                    color = LiquidCyan,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                                if (state.updateDownloadDetail.isNotBlank()) {
                                    Text(
                                        text = state.updateDownloadDetail,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily
                                    )
                                }
                            }
                        }
                    } else if (state.downloadedUpdateFilePath != null) {
                        Text(
                            text = "اكتمل التنزيل (100%) — جاهز للتثبيت!",
                            color = LuminousEmerald,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    state.updateDownloadError?.let { errMsg ->
                        Text(
                            text = errMsg,
                            color = RubyCrimson,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (state.downloadedUpdateFilePath != null) {
                            viewModel.installDownloadedUpdate()
                        } else {
                            viewModel.startInAppUpdateDownload()
                        }
                    },
                    enabled = !state.isDownloadingUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LuminousEmerald,
                        contentColor = ObsidianDeep
                    )
                ) {
                    Text(
                        text = when {
                            state.isDownloadingUpdate -> "جاري التنزيل ${state.updateDownloadProgress}%"
                            state.downloadedUpdateFilePath != null -> "تثبيت التحديث الآن"
                            else -> "تنزيل وتثبيت الآن"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                if (!state.isDownloadingUpdate) {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text("لاحقاً", color = TextSecondary)
                    }
                }
            }
        )
    }

    if (showMembersDialog && state.activeRoom != null) {
        val room = state.activeRoom!!
        val amIOwner = room.ownerId == state.currentUserId
        var previewMemberUid by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showMembersDialog = false },
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "أعضاء الغرفة (${room.memberUids.size})",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    room.memberUids.forEachIndexed { idx, memberUid ->
                        val isMe = memberUid == state.currentUserId
                        val isOwner = memberUid == room.ownerId
                        val isMuted = memberUid in room.mutedUids
                        val isSpeaking = memberUid == room.activeSpeakerUid && room.activeSpeakerUid.isNotBlank()
                        val displayName = when {
                            isMe -> state.settings.displayName.ifBlank { "أنت" }
                            else -> room.memberNames[memberUid]?.takeIf { it.isNotBlank() } ?: "عضو ${idx + 1}"
                        }
                        val localAvatarPath = if (isMe) state.settings.avatarUri else null
                        val thumbAvatarB64 = if (isMe) {
                            state.settings.avatarThumbBase64.ifBlank { room.memberAvatars[memberUid] ?: "" }
                        } else {
                            room.memberAvatars[memberUid] ?: ""
                        }
                        val statusColor = when {
                            isMuted -> RubyCrimson
                            isSpeaking -> LuminousEmerald
                            else -> glassTheme.primary
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    when {
                                        isMuted -> RubyCrimson.copy(alpha = 0.15f)
                                        isSpeaking -> LuminousEmerald.copy(alpha = 0.18f)
                                        else -> GlassWhite.copy(alpha = 0.06f)
                                    }
                                )
                                .clickable { previewMemberUid = memberUid }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MemberAvatarCircle(
                                    name = displayName,
                                    localPath = localAvatarPath,
                                    base64Thumb = thumbAvatarB64,
                                    size = 44.dp,
                                    borderColor = statusColor,
                                    statusDotColor = statusColor
                                )
                                val memberStatus = if (isMe) {
                                    state.settings.memberStatus
                                } else {
                                    room.memberStatuses[memberUid]?.takeIf { it.isNotBlank() } ?: "متاح"
                                }
                                Column {
                                    Text(
                                        text = buildString {
                                            append(displayName)
                                            if (isMe) append(" (أنت)")
                                        },
                                        color = TextPrimary,
                                        fontWeight = if (isMe || isSpeaking) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when {
                                            isMuted -> "مكتوم"
                                            isSpeaking -> "يتحدث الآن"
                                            isOwner -> "المالك • $memberStatus"
                                            else -> memberStatus
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isMuted -> RubyCrimson
                                            isSpeaking -> LuminousEmerald
                                            isOwner -> glassTheme.primary
                                            else -> TextSecondary
                                        }
                                    )
                                }
                            }
                            if (amIOwner && !isOwner) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.toggleMuteMember(memberUid) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                                            contentDescription = if (isMuted) "فك الكتم" else "كتم العضو",
                                            tint = if (isMuted) LuminousEmerald else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.kickMember(memberUid) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonRemove,
                                            contentDescription = "طرد العضو",
                                            tint = RubyCrimson,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMembersDialog = false }) {
                    Text("إغلاق", color = glassTheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )

        previewMemberUid?.let { targetUid ->
            val isMe = targetUid == state.currentUserId
            val isOwner = targetUid == room.ownerId
            val name = if (isMe) {
                state.settings.displayName.ifBlank { "أنت" }
            } else {
                room.memberNames[targetUid]?.takeIf { it.isNotBlank() } ?: "عضو"
            }
            val status = if (isMe) {
                state.settings.memberStatus
            } else {
                room.memberStatuses[targetUid]?.takeIf { it.isNotBlank() } ?: "متاح"
            }
            val txCount = room.memberTxCounts[targetUid] ?: 0
            val localPath = if (isMe) state.settings.avatarUri else null
            val b64 = if (isMe) {
                state.settings.avatarThumbBase64.ifBlank { room.memberAvatars[targetUid] ?: "" }
            } else {
                room.memberAvatars[targetUid] ?: ""
            }
            AlertDialog(
                onDismissRequest = { previewMemberUid = null },
                containerColor = ObsidianSurface,
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MemberAvatarCircle(
                            name = name,
                            localPath = localPath,
                            base64Thumb = b64,
                            size = 116.dp,
                            borderColor = glassTheme.primary
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isOwner) "مالك الغرفة • $status" else "عضو • $status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LuminousEmerald
                        )
                        Text(
                            text = "عدد مشاركات الإرسال: $txCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { previewMemberUid = null }) {
                        Text("تم", color = glassTheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }

    if (showChatDialog && state.activeRoom != null) {
        var chatInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChatDialog = false },
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "دردشة ${state.activeRoom!!.roomName}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.chatMessages.isEmpty()) {
                        Text(
                            text = "لا توجد رسائل بعد — ابدأ المحادثة أو اختر رداً سريعاً!",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.chatMessages, key = { it.id.ifBlank { it.text + it.senderUid } }) { msg ->
                                val isMine = msg.senderUid == state.currentUserId
                                val mapsUrl = remember(msg.text) {
                                    Regex("https://maps\\.google\\.com/\\?q=[-0-9.,]+").find(msg.text)?.value
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isMine) glassTheme.primary.copy(alpha = 0.16f)
                                            else GlassWhite.copy(alpha = 0.07f)
                                        )
                                        .let { mod ->
                                            if (mapsUrl != null) {
                                                mod.clickable {
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)))
                                                    }
                                                }
                                            } else mod
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (isMine) "أنت" else msg.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMine) glassTheme.primary else LuminousEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (mapsUrl != null) glassTheme.primary else TextPrimary,
                                        textDecoration = if (mapsUrl != null) TextDecoration.Underline else TextDecoration.None
                                    )
                                }
                            }
                        }
                    }

                    // One-Tap Quick Replies (#8)
                    val quickReplies = remember {
                        listOf("أنا في الطريق", "أسمعك بوضوح", "دقيقة وأتصل", "تم")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickReplies.forEach { reply ->
                            LiquidGlassSurface(
                                modifier = Modifier.clickable { viewModel.sendChatMessage(reply) },
                                shape = RoundedCornerShape(50),
                                accentColor = glassTheme.primary,
                                tintAlpha = 0.14f,
                                borderAlpha = 0.35f
                            ) {
                                Text(
                                    text = reply,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GlassWhite,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it.take(400) },
                            placeholder = { Text("اكتب رسالة سريعة...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    viewModel.sendChatMessage(chatInput)
                                    chatInput = ""
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "إرسال",
                                tint = LuminousEmerald
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChatDialog = false }) {
                    Text("إغلاق", color = glassTheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showStatusDialog) {
        MemberStatusDialog(
            currentStatus = state.settings.memberStatus,
            onSelectStatus = { status ->
                viewModel.setMemberStatus(status)
                Toast.makeText(context, "حالتك الآن: $status", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showStatusDialog = false }
        )
    }

    if (showStatsDialog) {
        ActivityStatsDialog(
            totalTxCount = state.settings.totalTxCount,
            totalTxSeconds = state.settings.totalTxSeconds,
            room = state.activeRoom,
            currentUserId = state.currentUserId,
            currentDisplayName = state.settings.displayName,
            onDismiss = { showStatsDialog = false }
        )
    }

    if (showQrShareDialog && state.activeRoom != null) {
        RoomQrShareDialog(
            room = state.activeRoom!!,
            onDismiss = { showQrShareDialog = false }
        )
    }

    if (showQrScannerDialog) {
        QrScannerDialog(
            onScanned = { code, passcode ->
                showQrScannerDialog = false
                if (code.isNotBlank() && passcode.isNotBlank()) {
                    showJoinDialog = false
                    viewModel.joinRoomByCode(code, passcode)
                    Toast.makeText(context, "جاري الانضمام للغرفة $code…", Toast.LENGTH_SHORT).show()
                } else if (code.isNotBlank()) {
                    joinRoomCodeInput = code
                    joinPasscodeInput = passcode
                    Toast.makeText(context, "تم مسح رمز الغرفة: $code", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showQrScannerDialog = false }
        )
    }
    }
}

@Composable
private fun QuickActionGlassChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .testTag(testTag)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        accentColor = accentColor,
        tintAlpha = 0.14f,
        borderAlpha = 0.35f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = GlassWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TopGlassHeader(
    refreshRateHz: Int,
    rogerBeepEnabled: Boolean,
    audioRoute: AudioOutputRoute,
    iconOpacity: Float,
    onCycleAudioRoute: () -> Unit,
    onToggleRogerBeep: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val themeColor = LocalGlassAccent.current.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LuxuryGlassIconBadge(
                icon = Icons.Rounded.Radio,
                contentDescription = stringResource(R.string.app_name),
                accentColor = themeColor,
                isActive = true,
                size = 42.dp,
                iconSize = 22.dp,
                modifier = Modifier.alpha(iconOpacity)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(10.dp),
                        accentColor = themeColor,
                        tintAlpha = 0.14f,
                        borderAlpha = 0.35f,
                        modifier = Modifier.alpha(iconOpacity)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "معدل التحديث",
                                tint = themeColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${refreshRateHz}Hz",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = GlassWhite
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = LuminousEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "سحابي • ${audioRoute.labelAr}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LuxuryGlassIconBadge(
                icon = when (audioRoute) {
                    AudioOutputRoute.SPEAKER -> Icons.Rounded.VolumeUp
                    AudioOutputRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
                    AudioOutputRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                },
                contentDescription = audioRoute.labelAr,
                accentColor = themeColor,
                isActive = true,
                size = 40.dp,
                iconSize = 19.dp,
                onClick = onCycleAudioRoute,
                modifier = Modifier
                    .alpha(iconOpacity)
                    .testTag("audio_route_toggle")
            )

            LuxuryGlassIconBadge(
                icon = if (rogerBeepEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                contentDescription = stringResource(R.string.roger_beep_on),
                accentColor = ElectricSapphire,
                isActive = rogerBeepEnabled,
                size = 40.dp,
                iconSize = 19.dp,
                onClick = onToggleRogerBeep,
                modifier = Modifier
                    .alpha(iconOpacity)
                    .testTag("roger_beep_toggle")
            )

            LuxuryGlassIconBadge(
                icon = Icons.Rounded.Settings,
                contentDescription = "الإعدادات",
                accentColor = themeColor,
                isActive = true,
                size = 40.dp,
                iconSize = 19.dp,
                onClick = onOpenSettings,
                modifier = Modifier
                    .alpha(iconOpacity)
                    .testTag("open_settings_button")
            )
        }
    }
}

@Composable
private fun CloudRoomGlassCard(
    activeRoom: CloudRoom?,
    memberStatus: String,
    onCreateRoomClick: () -> Unit,
    onJoinRoomClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareRoom: (CloudRoom) -> Unit,
    onShowQrShare: () -> Unit,
    onPickStatus: () -> Unit,
    onShowMembers: () -> Unit
) {
    val themeColor = LocalGlassAccent.current.primary
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        accentColor = themeColor,
        tintAlpha = 0.09f,
        borderAlpha = 0.30f
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeRoom != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = activeRoom.roomName,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            if (activeRoom.channelLockEnabled) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "البث مقفول بأولوية المتحدث",
                                    tint = RubyCrimson,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable { onCopyCode(activeRoom.roomCode) }
                            ) {
                                Text(
                                    text = activeRoom.roomCode,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = GlassWhite
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "نسخ الرمز",
                                    tint = themeColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "مشاركة دعوة الغرفة",
                                tint = LuminousEmerald,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onShareRoom(activeRoom) }
                            )
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = "رمز QR للغرفة",
                                tint = themeColor,
                                modifier = Modifier
                                    .size(21.dp)
                                    .clickable { onShowQrShare() }
                            )
                        }
                    }

                    LiquidGlassSurface(
                        modifier = Modifier
                            .testTag("room_members_button")
                            .clickable(onClick = onShowMembers),
                        shape = RoundedCornerShape(50),
                        accentColor = LuminousEmerald,
                        tintAlpha = 0.18f,
                        borderAlpha = 0.32f
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(LuminousEmerald)
                            )
                            Text(
                                text = "${activeRoom.memberUids.size} أعضاء",
                                style = MaterialTheme.typography.labelMedium,
                                color = GlassWhite
                            )
                        }
                    }
                }

                // My Status strip
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_status_button")
                        .clickable(onClick = onPickStatus),
                    shape = RoundedCornerShape(14.dp),
                    accentColor = LuminousEmerald,
                    tintAlpha = 0.14f,
                    borderAlpha = 0.32f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(LuminousEmerald)
                        )
                        Spacer(modifier = Modifier.size(5.dp))
                        Text(
                            text = "الحالة: $memberStatus",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GlassWhite,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Text(
                    text = "لا توجد غرفة نشطة — أنشئ غرفة جديدة أو انضم برمز صديقك",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_room_button")
                        .clickable(onClick = onCreateRoomClick),
                    shape = RoundedCornerShape(16.dp),
                    accentColor = themeColor,
                    tintAlpha = 0.16f,
                    borderAlpha = 0.36f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "غرفة جديدة",
                            style = MaterialTheme.typography.labelLarge,
                            color = GlassWhite
                        )
                    }
                }

                LiquidGlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("join_room_button")
                        .clickable(onClick = onJoinRoomClick),
                    shape = RoundedCornerShape(16.dp),
                    accentColor = LuminousEmerald,
                    tintAlpha = 0.16f,
                    borderAlpha = 0.36f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GroupAdd,
                            contentDescription = null,
                            tint = LuminousEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "انضمام برمز",
                            style = MaterialTheme.typography.labelLarge,
                            color = GlassWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStatusPill(state: WalkieTalkieUiState, txSeconds: Int) {
    val themeColor = LocalGlassAccent.current.primary
    val remoteSpeaker = state.activeRoom?.activeSpeakerName?.takeIf {
        it.isNotBlank() && state.isReceiving
    }
    val dotColor = when {
        state.mode == RadioMode.ALWAYS_OFF -> RubyCrimson
        state.isTransmitting -> LuminousEmerald
        state.isReceiving -> themeColor
        else -> LocalGlassAccent.current.secondary
    }
    val timerText = if (state.isTransmitting) {
        val mins = txSeconds / 60
        val secs = txSeconds % 60
        val autoTxt = if (state.autoMuteRemainingSec > 0) " • إيقاف خلال ${state.autoMuteRemainingSec}ث" else ""
        " • %02d:%02d%s".format(mins, secs, autoTxt)
    } else ""

    val statusText = when {
        state.mode == RadioMode.ALWAYS_OFF -> stringResource(R.string.status_always_off)
        state.mode == RadioMode.ALWAYS_ON -> stringResource(R.string.status_always_on) + timerText
        state.isTransmitting -> stringResource(R.string.status_transmitting) + timerText
        remoteSpeaker != null -> "$remoteSpeaker يتحدث الآن…"
        state.isReceiving -> stringResource(R.string.status_receiving)
        else -> stringResource(R.string.status_ready)
    }

    LiquidGlassSurface(
        shape = RoundedCornerShape(50),
        accentColor = dotColor,
        tintAlpha = 0.16f,
        borderAlpha = 0.42f
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.mode == RadioMode.ALWAYS_OFF) {
                Icon(
                    imageVector = Icons.Rounded.VolumeOff,
                    contentDescription = null,
                    tint = RubyCrimson,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = GlassWhite
            )
        }
    }
}

@Composable
private fun MicPermissionGlassBanner(onRequestPermission: () -> Unit) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        accentColor = ElectricSapphire,
        tintAlpha = 0.16f,
        borderAlpha = 0.45f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassWhite
                )
                Text(
                    text = stringResource(R.string.permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiquidCyan,
                    contentColor = ObsidianDeep
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("grant_mic_permission_button")
            ) {
                Text(
                    text = stringResource(R.string.permission_grant),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
