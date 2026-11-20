package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.components.LiquidAuroraBackground
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.LuxuryGlassIconBadge
import com.example.ui.theme.ElectricSapphire
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidCyan
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

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val decoded = inputStream?.use { BitmapFactory.decodeStream(it) }
                if (decoded != null) {
                    val avatarFile = java.io.File(context.filesDir, "avatar_${state.currentUserId}_${System.currentTimeMillis()}.jpg")
                    java.io.FileOutputStream(avatarFile).use { out ->
                        decoded.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                    }
                    viewModel.updateSettings { it.copy(avatarUri = avatarFile.absolutePath) }
                    Toast.makeText(context, "تم حفظ الصورة الشخصية بنجاح", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تعذر قراءة الصورة المختارة", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "تعذر حفظ الصورة الشخصية", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "تم حفظ الاسم", Toast.LENGTH_SHORT).show()
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
                    }
                }
            }

            // 2. Icon Glass Opacity & Visuals
            SettingsSectionCard(title = "مظهر الأيقونات الزجاجية", accentColor = ElectricSapphire) {
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
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                            context.startActivity(intent)
                        },
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
                            text = "تحميل التحديث (v${update.latestVersion})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 5. Account Control
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
