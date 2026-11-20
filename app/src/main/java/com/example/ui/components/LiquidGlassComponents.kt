package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.RadioMode
import com.example.ui.theme.ElectricSapphire
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.LiquidCyan
import com.example.ui.theme.LuminousEmerald
import com.example.ui.theme.MintGlow
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianMid
import com.example.ui.theme.RoseGlow
import com.example.ui.theme.RubyCrimson
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated spatial aurora background that smoothly shifts mood based on radio state.
 */
@Composable
fun LiquidAuroraBackground(
    mode: RadioMode,
    isTransmitting: Boolean,
    isReceiving: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_motion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_phase"
    )

    val primaryGlow by animateColorAsState(
        targetValue = when {
            mode == RadioMode.ALWAYS_OFF -> RubyCrimson.copy(alpha = 0.28f)
            isTransmitting -> LuminousEmerald.copy(alpha = 0.34f)
            isReceiving -> LiquidCyan.copy(alpha = 0.32f)
            else -> ElectricSapphire.copy(alpha = 0.26f)
        },
        animationSpec = tween(550),
        label = "primary_glow"
    )

    val secondaryGlow by animateColorAsState(
        targetValue = when {
            mode == RadioMode.ALWAYS_OFF -> RoseGlow.copy(alpha = 0.16f)
            isTransmitting -> LiquidCyan.copy(alpha = 0.26f)
            else -> LiquidCyan.copy(alpha = 0.18f)
        },
        animationSpec = tween(550),
        label = "secondary_glow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(ObsidianDeep, ObsidianMid, ObsidianDeep)
            )
        )

        val orb1X = size.width * (0.28f + 0.14f * cos(phase))
        val orb1Y = size.height * (0.22f + 0.08f * sin(phase))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryGlow, Color.Transparent),
                center = Offset(orb1X, orb1Y),
                radius = size.minDimension * 0.85f
            ),
            radius = size.minDimension * 0.85f,
            center = Offset(orb1X, orb1Y)
        )

        val orb2X = size.width * (0.76f + 0.12f * sin(phase * 0.8f))
        val orb2Y = size.height * (0.72f + 0.08f * cos(phase * 0.8f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryGlow, Color.Transparent),
                center = Offset(orb2X, orb2Y),
                radius = size.minDimension * 0.78f
            ),
            radius = size.minDimension * 0.78f,
            center = Offset(orb2X, orb2Y)
        )
    }
}

/**
 * Reusable iOS 26 Liquid Glass container with frosted translucency and specular rim lighting.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    accentColor: Color = GlassWhite,
    tintAlpha: Float = 0.09f,
    borderAlpha: Float = 0.28f,
    content: @Composable () -> Unit
) {
    val bgBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = tintAlpha + 0.06f),
            GlassWhite.copy(alpha = tintAlpha * 0.55f),
            accentColor.copy(alpha = tintAlpha * 0.35f)
        )
    )
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            GlassWhite.copy(alpha = borderAlpha + 0.18f),
            accentColor.copy(alpha = borderAlpha),
            GlassWhite.copy(alpha = 0.05f),
            accentColor.copy(alpha = borderAlpha * 0.75f)
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(width = 1.1.dp, brush = rimBrush, shape = shape)
    ) {
        content()
    }
}

/**
 * Luxurious 3D Glass Icon Badge with specular top sheen and radial inner luminescence.
 */
@Composable
fun LuxuryGlassIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    accentColor: Color,
    isActive: Boolean,
    size: Dp = 46.dp,
    iconSize: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val animatedTint by animateColorAsState(
        targetValue = if (isActive) accentColor else TextSecondary,
        animationSpec = tween(250),
        label = "badge_tint"
    )
    val shape = RoundedCornerShape(16.dp)
    val baseModifier = modifier
        .size(size)
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = if (isActive) {
                    listOf(
                        accentColor.copy(alpha = 0.34f),
                        accentColor.copy(alpha = 0.12f)
                    )
                } else {
                    listOf(
                        GlassWhite.copy(alpha = 0.15f),
                        GlassWhite.copy(alpha = 0.04f)
                    )
                }
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassWhite.copy(alpha = if (isActive) 0.6f else 0.32f),
                    accentColor.copy(alpha = if (isActive) 0.45f else 0.08f)
                )
            ),
            shape = shape
        )
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Box(modifier = baseModifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = animatedTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Centerpiece Giant Liquid Glass Push-To-Talk Orb with 120Hz reactive audio rings.
 */
@Composable
fun LiquidGlassPttOrb(
    mode: RadioMode,
    isTransmitting: Boolean,
    isReceiving: Boolean,
    micLevel: Float,
    enabled: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPressedVisual = isTransmitting
    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.96f
            isPressedVisual -> 0.94f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ptt_scale"
    )

    val smoothLevel by animateFloatAsState(
        targetValue = micLevel,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "mic_level"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ptt_ring_pulse"
    )

    val coreColor by animateColorAsState(
        targetValue = when {
            mode == RadioMode.ALWAYS_OFF -> RubyCrimson
            isTransmitting -> LuminousEmerald
            isReceiving -> LiquidCyan
            else -> ElectricSapphire
        },
        animationSpec = tween(300),
        label = "orb_core_color"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when {
            mode == RadioMode.ALWAYS_OFF -> RoseGlow
            isTransmitting -> MintGlow
            else -> LiquidCyan
        },
        animationSpec = tween(300),
        label = "orb_secondary_color"
    )

    Box(
        modifier = modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        // 120Hz reactive liquid rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.36f

            if (isTransmitting || isReceiving) {
                // Expanding glass shockwave rings
                for (i in 0..2) {
                    val p = (pulse + i * 0.33f) % 1f
                    val radius = baseRadius + p * (size.minDimension * 0.16f) + (smoothLevel * 28f)
                    val alpha = (1f - p) * (0.45f + smoothLevel * 0.35f)
                    drawCircle(
                        color = coreColor.copy(alpha = alpha.coerceIn(0f, 0.85f)),
                        radius = radius,
                        center = center,
                        style = Stroke(width = (3.5f - p * 2f).dp.toPx())
                    )
                }
            } else {
                // Subtle idle specular halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * 1.32f
                    ),
                    radius = baseRadius * 1.32f,
                    center = center
                )
            }
        }

        // Main Liquid Glass Orb
        Box(
            modifier = Modifier
                .size(204.dp)
                .scale(scale)
                .shadow(
                    elevation = if (isTransmitting) 30.dp else 18.dp,
                    shape = CircleShape,
                    ambientColor = coreColor,
                    spotColor = coreColor
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = if (isTransmitting) 0.55f else 0.28f),
                            coreColor.copy(alpha = if (isTransmitting) 0.34f else 0.16f),
                            ObsidianDeep.copy(alpha = 0.82f)
                        ),
                        center = Offset(140f, 120f),
                        radius = 380f
                    )
                )
                .border(
                    width = 1.8.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassWhite.copy(alpha = 0.72f),
                            secondaryColor.copy(alpha = 0.60f),
                            GlassWhite.copy(alpha = 0.08f),
                            coreColor.copy(alpha = 0.50f)
                        )
                    ),
                    shape = CircleShape
                )
                .testTag("ptt_main_button")
                .pointerInput(enabled, mode) {
                    if (enabled && mode == RadioMode.PUSH_TO_TALK) {
                        detectTapGestures(
                            onPress = {
                                onPressStart()
                                tryAwaitRelease()
                                onPressEnd()
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Top-left glass specular reflection arc
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GlassWhite.copy(alpha = 0.26f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.48f
                    ),
                    radius = size.minDimension * 0.45f,
                    center = Offset(size.width / 2f, size.height * 0.46f)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val centerIcon = when {
                    mode == RadioMode.ALWAYS_OFF -> Icons.Rounded.MicOff
                    isTransmitting -> Icons.Rounded.GraphicEq
                    else -> Icons.Rounded.Mic
                }
                Icon(
                    imageVector = centerIcon,
                    contentDescription = "زر التحدث الفوري",
                    tint = GlassWhite,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        mode == RadioMode.ALWAYS_OFF -> "مُغلق"
                        mode == RadioMode.ALWAYS_ON -> "بث مستمر"
                        isTransmitting -> "على الهواء"
                        else -> "اضغط للتحدث"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlassWhite
                )
            }
        }
    }
}

/**
 * Dedicated Liquid Glass Toggle Tile for Always-On and Always-Off controls.
 */
@Composable
fun GlassModeControlButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mode_btn_scale"
    )

    LiquidGlassSurface(
        modifier = modifier
            .scale(scale)
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        accentColor = if (isActive) accentColor else GlassWhite,
        tintAlpha = if (isActive) 0.20f else 0.08f,
        borderAlpha = if (isActive) 0.55f else 0.24f
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LuxuryGlassIconBadge(
                icon = icon,
                contentDescription = title,
                accentColor = accentColor,
                isActive = isActive,
                size = 48.dp,
                iconSize = 24.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) GlassWhite else TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) GlassWhite.copy(alpha = 0.85f) else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
