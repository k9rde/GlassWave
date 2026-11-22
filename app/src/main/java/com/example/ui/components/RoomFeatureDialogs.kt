package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CloudRoom
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalGlassAccent
import com.example.ui.theme.LuminousEmerald
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RubyCrimson
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

data class StatusOption(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun MemberStatusDialog(
    currentStatus: String,
    onSelectStatus: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalGlassAccent.current.primary
    val options = remember {
        listOf(
            StatusOption("متاح", Icons.Rounded.CheckCircle, LuminousEmerald),
            StatusOption("مشغول", Icons.Rounded.DoNotDisturbOn, Color(0xFFF59E0B)),
            StatusOption("أقود السيارة", Icons.Rounded.DirectionsCar, Color(0xFF38BDF8)),
            StatusOption("صامت", Icons.Rounded.VolumeOff, RubyCrimson)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text("حالتك الحالية للأصدقاء", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val selected = currentStatus == option.label
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) option.color.copy(alpha = 0.22f)
                                else GlassWhite.copy(alpha = 0.06f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) option.color.copy(alpha = 0.7f) else GlassWhite.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                onSelectStatus(option.label)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(22.dp))
                        Text(
                            text = option.label,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = option.color, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ActivityStatsDialog(
    totalTxCount: Int,
    totalTxSeconds: Long,
    room: CloudRoom?,
    currentUserId: String,
    currentDisplayName: String,
    onDismiss: () -> Unit
) {
    val accent = LocalGlassAccent.current.primary
    val mins = totalTxSeconds / 60
    val secs = totalTxSeconds % 60

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        icon = {
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text("إحصائيات التحدث والنشاط", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassSurface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        accentColor = accent,
                        tintAlpha = 0.14f,
                        borderAlpha = 0.35f
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$totalTxCount",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            Text("مرات الإرسال", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }

                    LiquidGlassSurface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        accentColor = LuminousEmerald,
                        tintAlpha = 0.14f,
                        borderAlpha = 0.35f
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "%02d:%02d".format(mins, secs),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = LuminousEmerald
                            )
                            Text("مدة البث الكلية", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }

                if (room != null) {
                    Text(
                        text = "الأكثر تفاعلاً في ${room.roomName}:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    val sortedMembers = room.memberUids
                        .map { uid ->
                            val name = if (uid == currentUserId) {
                                currentDisplayName.ifBlank { "أنت" }
                            } else {
                                room.memberNames[uid]?.takeIf { it.isNotBlank() } ?: "عضو"
                            }
                            val count = room.memberTxCounts[uid] ?: 0
                            Triple(uid, name, count)
                        }
                        .sortedByDescending { it.third }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sortedMembers.forEachIndexed { idx, (_, name, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GlassWhite.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. $name",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = "$count إرسال",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (idx == 0) LuminousEmerald else accent
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}
