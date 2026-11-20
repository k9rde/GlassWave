package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CloudRoom
import com.example.ui.components.GlassModeControlButton
import com.example.ui.components.LiquidAuroraBackground
import com.example.ui.components.LiquidGlassPttOrb
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.LuxuryGlassIconBadge
import com.example.ui.theme.ElectricSapphire
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidCyan
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
    var txSeconds by remember { mutableIntStateOf(0) }

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

    if (showSettings) {
        SettingsScreen(
            state = state,
            viewModel = viewModel,
            onBack = { showSettings = false },
            onSignOutClick = onSignOutClick
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidAuroraBackground(
            mode = state.mode,
            isTransmitting = state.isTransmitting,
            isReceiving = state.isReceiving
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Glass Header Bar
            TopGlassHeader(
                refreshRateHz = state.refreshRateHz,
                rogerBeepEnabled = state.rogerBeepEnabled,
                iconOpacity = state.settings.glassOpacity,
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
                onCreateRoomClick = { showCreateDialog = true },
                onJoinRoomClick = { showJoinDialog = true },
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
                }
            )

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
                        Text(
                            text = state.errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassWhite,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Dual Luxurious Glass Controls: Always-On & Always-Off
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    subtitle = if (state.mode == RadioMode.ALWAYS_OFF) "مُغلق بالكامل" else "كتم شامل",
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

    if (showCreateDialog) {
        var roomName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("إنشاء غرفة لاسلكي", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it.take(40) },
                    label = { Text("اسم الغرفة (مثال: الأصدقاء)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createRoom(roomName)
                        showCreateDialog = false
                    },
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
        var roomCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("الانضمام إلى غرفة صديق", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { roomCode = it.uppercase().take(12) },
                    label = { Text("أدخل رمز الغرفة (مثال: WAVE42)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.joinRoomByCode(roomCode)
                        showJoinDialog = false
                    },
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
}

@Composable
private fun TopGlassHeader(
    refreshRateHz: Int,
    rogerBeepEnabled: Boolean,
    iconOpacity: Float,
    onToggleRogerBeep: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LuxuryGlassIconBadge(
                icon = Icons.Rounded.Radio,
                contentDescription = stringResource(R.string.app_name),
                accentColor = LiquidCyan,
                isActive = true,
                size = 46.dp,
                iconSize = 24.dp,
                modifier = Modifier.alpha(iconOpacity)
            )
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = LuminousEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "بث سحابي • أي شبكة",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(14.dp),
                accentColor = LiquidCyan,
                tintAlpha = 0.12f,
                borderAlpha = 0.35f,
                modifier = Modifier.alpha(iconOpacity)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "معدل التحديث",
                        tint = LiquidCyan,
                        modifier = Modifier.size(14.dp)
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

            LuxuryGlassIconBadge(
                icon = if (rogerBeepEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                contentDescription = stringResource(R.string.roger_beep_on),
                accentColor = ElectricSapphire,
                isActive = rogerBeepEnabled,
                size = 42.dp,
                iconSize = 20.dp,
                onClick = onToggleRogerBeep,
                modifier = Modifier
                    .alpha(iconOpacity)
                    .testTag("roger_beep_toggle")
            )

            LuxuryGlassIconBadge(
                icon = Icons.Rounded.Settings,
                contentDescription = "الإعدادات",
                accentColor = LiquidCyan,
                isActive = true,
                size = 42.dp,
                iconSize = 20.dp,
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
    onCreateRoomClick: () -> Unit,
    onJoinRoomClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareRoom: (CloudRoom) -> Unit
) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        accentColor = LiquidCyan,
        tintAlpha = 0.09f,
        borderAlpha = 0.30f
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeRoom != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeRoom.roomName,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
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
                                    tint = LiquidCyan,
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
                        }
                    }

                    LiquidGlassSurface(
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
                    accentColor = LiquidCyan,
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
                            tint = LiquidCyan,
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
    val remoteSpeaker = state.activeRoom?.activeSpeakerName?.takeIf {
        it.isNotBlank() && state.isReceiving
    }
    val dotColor = when {
        state.mode == RadioMode.ALWAYS_OFF -> RubyCrimson
        state.isTransmitting -> LuminousEmerald
        state.isReceiving -> LiquidCyan
        else -> ElectricSapphire
    }
    val timerText = if (state.isTransmitting) {
        val mins = txSeconds / 60
        val secs = txSeconds % 60
        " • %02d:%02d".format(mins, secs)
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
