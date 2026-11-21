package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NoiseControlOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.components.AvatarEditorDialog
import com.example.ui.components.LiquidAuroraBackground
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.LuxuryGlassIconBadge
import com.example.ui.components.loadOrientedBitmap
import com.example.ui.theme.ElectricSapphire
import com.example.ui.theme.GlassThemeOption
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidCyan
import com.example.ui.theme.LocalGlassAccent
import com.example.ui.theme.LuminousEmerald
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.RubyCrimson
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: WalkieTalkieUiState,
    viewModel: WalkieTalkieViewModel,
    onBack: () -> Unit,
    onSignOutClick: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var nameInput by remember(state.settings.displayName) {
        mutableStateOf(state.settings.displayName)
    }
    var pendingEditorBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val loaded = loadOrientedBitmap(context, uri, maxDim = 1024)
            if (loaded != null) {
                pendingEditorBitmap = loaded
            } else {
                Toast.makeText(context, "تعذر قراءة الصورة المختارة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val avatarBitmap = remember(state.settings.avatarUri) {
        state.settings.avatarUri?.let { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else {
                    context.contentResolver.openInputStream(Uri.parse(path))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    pendingEditorBitmap?.let { editorSource ->
        AvatarEditorDialog(
            sourceBitmap = editorSource,
            onSave = { croppedBitmap, thumbBase64 ->
                try {
                    val avatarFile = java.io.File(
                        context.filesDir,
                        "avatar_${state.currentUserId}_${System.currentTimeMillis()}.jpg"
                    )
                    java.io.FileOutputStream(avatarFile).use { out ->
                        croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    viewModel.saveProfileAvatar(avatarFile.absolutePath, thumbBase64)
                    Toast.makeText(context, "تم تعديل وحفظ الصورة الشخصية", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "تعذر حفظ الصورة الشخصية", Toast.LENGTH_SHORT).show()
                } finally {
                    pendingEditorBitmap = null
                }
            },
            onDismiss = {
                pendingEditorBitmap = null
            }
        )
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
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LuxuryGlassIconBadge(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "رجوع",
                    accentColor = LiquidCyan,
                    isActive = true,
                    size = 44.dp,
                    iconSize = 22.dp,
                    onClick = onBack,
                    modifier = Modifier
                        .alpha(state.settings.glassOpacity)
                        .testTag("settings_back_button")
                )
                Column {
                    Text(
                        text = "الإعدادات",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "تخصيص المظهر، الملف الشخصي، الحساب والغرف",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // 1. Profile Section (Name + Avatar Photo)
            SettingsSectionCard(title = "الملف الشخصي", accentColor = LiquidCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(LiquidCyan.copy(alpha = 0.35f), ElectricSapphire.copy(alpha = 0.18f))
                                )
                            )
                            .border(1.5.dp, GlassWhite.copy(alpha = 0.55f), CircleShape)
                            .testTag("pick_avatar_button")
                            .clickable {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap,
                                contentDescription = "الصورة الشخصية",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "الصورة الشخصية",
                                tint = GlassWhite,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(LiquidCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "تغيير الصورة",
                                tint = ObsidianDeep,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it.take(40) },
                            label = { Text("الاسم الظاهر للأصدقاء") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                viewModel.updateSettings { it.copy(displayName = nameInput.trim().ifBlank { "صديق" }) }
                                Toast.makeText(context, "تم حفظ الملف الشخصي", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiquidCyan,
                                contentColor = ObsidianDeep
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حفظ الملف الشخصي", fontWeight = FontWeight.Bold)
                        }
                        if (!state.settings.avatarUri.isNullOrBlank()) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    val path = state.settings.avatarUri
                                    val existing = runCatching {
                                        val file = java.io.File(path)
                                        if (file.exists()) {
                                            BitmapFactory.decodeFile(file.absolutePath)
                                        } else {
                                            loadOrientedBitmap(context, Uri.parse(path), maxDim = 1024)
                                        }
                                    }.getOrNull()
                                    if (existing != null) {
                                        pendingEditorBitmap = existing
                                    } else {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("تعديل وقص الصورة الحالية", color = LiquidCyan, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 2. Icon Glass Opacity & Theme Colors (#6)
            SettingsSectionCard(title = "مظهر الزجاج والألوان", accentColor = LocalGlassAccent.current.secondary) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = LocalGlassAccent.current.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("ثيم الزجاج السائل", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassThemeOption.entries.forEach { option ->
                        val selected = state.settings.glassTheme == option.key
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) option.primary.copy(alpha = 0.22f)
                                    else GlassWhite.copy(alpha = 0.06f)
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) option.primary else GlassWhite.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    viewModel.updateSettings { it.copy(glassTheme = option.key) }
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(option.primary, option.secondary)))
                                    .border(1.dp, GlassWhite.copy(alpha = 0.7f), CircleShape)
                            )
                            Text(
                                text = option.labelAr,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) GlassWhite else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Opacity,
                            contentDescription = null,
                            tint = LiquidCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("شفافية الأيقونات", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "${(state.settings.glassOpacity * 100).roundToInt()}%",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = LiquidCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = state.settings.glassOpacity,
                    onValueChange = { value ->
                        viewModel.updateSettings { it.copy(glassOpacity = value) }
                    },
                    valueRange = 0.25f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = LiquidCyan,
                        activeTrackColor = LiquidCyan
                    ),
                    modifier = Modifier.testTag("glass_opacity_slider")
                )
            }

            // 3. Room Settings (Switch, Copy Code, Leave, Delete)
            SettingsSectionCard(title = "إعدادات الغرفة", accentColor = LuminousEmerald) {
                val room = state.activeRoom
                if (room != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(room.roomName, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                text = "الرمز: ${room.roomCode} • ${room.memberUids.size} أعضاء",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            if (room.ownerId == state.currentUserId && room.roomPasscode.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = LuminousEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "رمز الدخول: ${room.roomPasscode}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = LuminousEmerald
                                    )
                                }
                            }
                        }
                        LuxuryGlassIconBadge(
                            icon = Icons.Rounded.ContentCopy,
                            contentDescription = "نسخ رمز الغرفة",
                            accentColor = LiquidCyan,
                            isActive = true,
                            size = 40.dp,
                            iconSize = 18.dp,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("GlassWave Room", room.roomCode))
                                Toast.makeText(context, "تم نسخ الرمز: ${room.roomCode}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    if (room.ownerId == state.currentUserId) {
                        var newPasscode by remember(room.roomPasscode) { mutableStateOf(room.roomPasscode) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newPasscode,
                                onValueChange = { newPasscode = it.take(24) },
                                label = { Text("تعديل رمز الدخول") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    viewModel.updateRoomPasscode(newPasscode)
                                    Toast.makeText(context, "تم تحديث رمز الدخول", Toast.LENGTH_SHORT).show()
                                },
                                enabled = newPasscode.trim().length >= 4 && newPasscode.trim() != room.roomPasscode,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LuminousEmerald,
                                    contentColor = ObsidianDeep
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("حفظ", fontWeight = FontWeight.Bold)
                            }
                        }

                        SettingsToggleRow(
                            icon = Icons.Rounded.RecordVoiceOver,
                            title = "أولوية المتحدث (قفل القناة)",
                            subtitle = "منع مقاطعة المتحدث الحالي أثناء الإرسال",
                            checked = room.channelLockEnabled,
                            onCheckedChange = { _ ->
                                viewModel.toggleChannelLock()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.leaveActiveRoom()
                                Toast.makeText(context, "تم الخروج من الغرفة", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RubyCrimson.copy(alpha = 0.22f),
                                contentColor = GlassWhite
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("leave_room_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("خروج من الغرفة", fontWeight = FontWeight.Bold)
                        }

                        if (room.ownerId == state.currentUserId) {
                            Button(
                                onClick = {
                                    viewModel.deleteActiveRoom()
                                    Toast.makeText(context, "تم حذف الغرفة", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RubyCrimson,
                                    contentColor = GlassWhite
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("delete_room_button")
                            ) {
                                Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("حذف الغرفة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (state.myRooms.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("التبديل بين غرفك:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        state.myRooms.forEach { item ->
                            val selected = item.id == room.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) LiquidCyan.copy(alpha = 0.18f) else GlassWhite.copy(alpha = 0.05f))
                                    .clickable { viewModel.selectRoom(item) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Radio, contentDescription = null, tint = if (selected) LiquidCyan else TextSecondary, modifier = Modifier.size(18.dp))
                                    Text(item.roomName, color = TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                                Text(item.roomCode, fontFamily = JetBrainsMonoFontFamily, color = LiquidCyan, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "لست منضماً إلى أي غرفة حالياً.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // 4. Pro Audio & Device Features
            SettingsSectionCard(title = "مزايا الصوت والأداء", accentColor = LiquidCyan) {
                SettingsToggleRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "نغمة الإرسال (Roger Beep)",
                    subtitle = "إصدار نغمة لاسلكي عند بدء وإنهاء الكلام",
                    checked = state.settings.rogerBeepEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(rogerBeepEnabled = checked) }
                    }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Vibration,
                    title = "الاهتزاز اللمسي",
                    subtitle = "اهتزاز خفيف عند الضغط على أزرار البث",
                    checked = state.settings.hapticEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(hapticEnabled = checked) }
                    }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "إبقاء الشاشة مضاءة",
                    subtitle = "منع انطفاء الشاشة أثناء استخدام اللاسلكي",
                    checked = state.settings.keepScreenOn,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(keepScreenOn = checked) }
                    }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Headset,
                    title = "الاستماع بالخلفية",
                    subtitle = "إبقاء الاستماع نشطاً مع إشعار دائم عند إغلاق الشاشة",
                    checked = state.settings.backgroundListeningEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(backgroundListeningEnabled = checked) }
                    }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.VolumeUp,
                    title = "التحدث بزر الصوت",
                    subtitle = "الضغط المطول على زر رفع/خفض الصوت للتحدث",
                    checked = state.settings.volumeKeyPttEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(volumeKeyPttEnabled = checked) }
                    }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Radio,
                    title = "فلتر صوت اللاسلكي (Radio FX)",
                    subtitle = "إضافة طابع صوت أجهزة اللاسلكي الحقيقية",
                    checked = state.settings.radioFxEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(radioFxEnabled = checked) }
                    }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.NoiseControlOff,
                    title = "عزل الضوضاء الذكي",
                    subtitle = "تنقية صوت الميكروفون وإزالة الصدى وضجيج الهواء",
                    checked = state.settings.noiseSuppressionEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(noiseSuppressionEnabled = checked) }
                    }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "تشويش الراديو الواقعي (Squelch)",
                    subtitle = "نبرة تشويش خفيفة عند بدء وإنهاء البث",
                    checked = state.settings.squelchEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(squelchEnabled = checked) }
                    }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.BubbleChart,
                    title = "فقاعة التحدث العائمة فوق التطبيقات",
                    subtitle = "زر عائم للتحدث السريع أثناء فتح تطبيقات أخرى",
                    checked = state.settings.floatingBubbleEnabled,
                    onCheckedChange = { checked ->
                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            runCatching { context.startActivity(intent) }
                            Toast.makeText(context, "اسمح بالظهور فوق التطبيقات ثم أعد تفعيل الخيار", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.updateSettings { it.copy(floatingBubbleEnabled = checked) }
                        }
                    }
                )

                // Auto-Mute Timer Selector (#14)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = LiquidCyan, modifier = Modifier.size(18.dp))
                    Text("مؤقت الإيقاف التلقائي للبث المستمر", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0 to "معطّل", 30 to "30ث", 60 to "60ث", 120 to "120ث").forEach { (secs, label) ->
                        val selected = state.settings.autoMuteSeconds == secs
                        Button(
                            onClick = { viewModel.updateSettings { it.copy(autoMuteSeconds = secs) } },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) LiquidCyan else GlassWhite.copy(alpha = 0.08f),
                                contentColor = if (selected) ObsidianDeep else TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }

                // Roger Beep Style Selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = LiquidCyan, modifier = Modifier.size(18.dp))
                    Text("نمط نغمة الإرسال (Roger Beep)", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CLASSIC" to "كلاسيكي", "POLICE" to "شرطة", "MODERN" to "حديث").forEach { (key, label) ->
                        val selected = state.settings.rogerBeepStyle == key
                        Button(
                            onClick = { viewModel.updateSettings { it.copy(rogerBeepStyle = key) } },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) LiquidCyan else GlassWhite.copy(alpha = 0.08f),
                                contentColor = if (selected) ObsidianDeep else TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = LuminousEmerald, modifier = Modifier.size(20.dp))
                        Text("تضخيم حساسية الميكروفون", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        text = "${String.format("%.1f", state.settings.micGain)}x",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = LuminousEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = state.settings.micGain,
                    onValueChange = { value ->
                        viewModel.updateSettings { it.copy(micGain = value) }
                    },
                    valueRange = 1.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = LuminousEmerald,
                        activeTrackColor = LuminousEmerald
                    )
                )
            }

            // 4.5 App Updates
            SettingsSectionCard(title = "التحقق من التحديثات", accentColor = ElectricSapphire) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = LiquidCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "الإصدار الحالي: v${BuildConfig.VERSION_NAME}",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (state.updateCheckMessage != null) {
                    Text(
                        text = state.updateCheckMessage,
                        color = if (state.availableUpdate != null) LuminousEmerald else TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = { viewModel.checkForUpdates(manual = true) },
                    enabled = !state.isCheckingUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiquidCyan,
                        contentColor = ObsidianDeep
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_updates_button")
                ) {
                    Text(
                        text = if (state.isCheckingUpdate) "جاري الفحص..." else "فحص التحديثات الآن",
                        fontWeight = FontWeight.Bold
                    )
                }
                state.availableUpdate?.let { update ->
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
                                    text = "جاري التنزيل... ${state.updateDownloadProgress}%",
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
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_update_button")
                    ) {
                        Text(
                            text = when {
                                state.isDownloadingUpdate -> "جاري التنزيل ${state.updateDownloadProgress}%"
                                state.downloadedUpdateFilePath != null -> "تثبيت التحديث الآن (v${update.latestVersion})"
                                else -> "تنزيل وتثبيت التحديث (v${update.latestVersion})"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 5. Developer Contact (Instagram & WhatsApp)
            SettingsSectionCard(title = "للتواصل مع المطور", accentColor = LiquidCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassSurface(
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("developer_instagram_button")
                            .clickable {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/0nusn/"))
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(context, "تعذر فتح إنستغرام", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(22.dp),
                        accentColor = Color(0xFFE1306C),
                        tintAlpha = 0.22f,
                        borderAlpha = 0.52f
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            InstagramGlyph(modifier = Modifier.size(38.dp))
                        }
                    }

                    LiquidGlassSurface(
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("developer_whatsapp_button")
                            .clickable {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/9647746088000"))
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(context, "تعذر فتح واتساب", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(22.dp),
                        accentColor = Color(0xFF25D366),
                        tintAlpha = 0.22f,
                        borderAlpha = 0.52f
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            WhatsAppGlyph(modifier = Modifier.size(38.dp))
                        }
                    }
                }
            }

            // 6. Account Control
            SettingsSectionCard(title = "إدارة الحساب", accentColor = RubyCrimson) {
                if (state.currentUserEmail.isNotBlank()) {
                    Text(
                        text = "الحساب المسجّل: ${state.currentUserEmail}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "المعرّف: ${state.currentUserId.take(16)}…",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onSignOutClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RubyCrimson,
                        contentColor = GlassWhite
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("تسجيل الخروج / تبديل الحساب", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    accentColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        accentColor = accentColor,
        tintAlpha = 0.09f,
        borderAlpha = 0.30f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = LiquidCyan, modifier = Modifier.size(20.dp))
            Column {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianDeep,
                checkedTrackColor = LiquidCyan
            )
        )
    }
}

@Composable
private fun InstagramGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = w * 0.08f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF))
            ),
            topLeft = Offset(pad, pad),
            size = Size(w - pad * 2f, h - pad * 2f),
            cornerRadius = CornerRadius(w * 0.28f, w * 0.28f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            style = Stroke(width = w * 0.075f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.14f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = w * 0.075f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.045f,
            center = Offset(w * 0.66f, h * 0.34f)
        )
    }
}

@Composable
private fun WhatsAppGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF25D366), Color(0xFF128C7E))
            ),
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f)
        )
        val bubble = Path().apply {
            moveTo(w * 0.25f, h * 0.74f)
            lineTo(w * 0.31f, h * 0.60f)
            lineTo(w * 0.39f, h * 0.68f)
            close()
        }
        drawPath(path = bubble, color = Color.White)
        drawCircle(
            color = Color.White,
            radius = w * 0.24f,
            center = Offset(w * 0.51f, h * 0.49f),
            style = Stroke(width = w * 0.07f)
        )
        val phoneArc = Path().apply {
            moveTo(w * 0.40f, h * 0.39f)
            quadraticTo(w * 0.42f, h * 0.56f, w * 0.61f, h * 0.58f)
        }
        drawPath(
            path = phoneArc,
            color = Color.White,
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
