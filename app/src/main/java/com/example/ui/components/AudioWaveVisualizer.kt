package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassWhite
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Lightweight 120Hz Jetpack Compose Audio Visualizer.
 * Renders dual harmonic liquid sine waves inside the PTT orb that react in real time to micLevel.
 */
@Composable
fun LiveLiquidSineWave(
    micLevel: Float,
    isActive: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        if (!isActive) return@Canvas

        val width = size.width
        val height = size.height
        val centerY = height * 0.56f
        val energy = (0.18f + micLevel * 0.82f).coerceIn(0.15f, 1f)
        val maxAmp = height * 0.20f * energy

        val wave1 = Path()
        val wave2 = Path()
        val steps = 48

        for (i in 0..steps) {
            val fraction = i.toFloat() / steps
            val x = fraction * width
            // Envelope tapers smoothly to 0 at left and right edges of the orb
            val envelope = sin(fraction * PI).toFloat()
            val y1 = centerY + sin(fraction * 4f * PI + phase).toFloat() * maxAmp * envelope
            val y2 = centerY + sin(fraction * 6f * PI - phase * 1.4f).toFloat() * (maxAmp * 0.65f) * envelope

            if (i == 0) {
                wave1.moveTo(x, y1)
                wave2.moveTo(x, y2)
            } else {
                wave1.lineTo(x, y1)
                wave2.lineTo(x, y2)
            }
        }

        drawPath(
            path = wave2,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    secondaryColor.copy(alpha = 0.55f),
                    GlassWhite.copy(alpha = 0.65f),
                    secondaryColor.copy(alpha = 0.55f),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        drawPath(
            path = wave1,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    primaryColor.copy(alpha = 0.85f),
                    GlassWhite.copy(alpha = 0.95f),
                    primaryColor.copy(alpha = 0.85f),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Lightweight 9-band reactive equalizer bar visualizer for the center of the PTT button.
 */
@Composable
fun LiveAudioWaveBars(
    micLevel: Float,
    isActive: Boolean,
    barColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eq_phase"
    )

    Canvas(modifier = modifier) {
        val barCount = 9
        val spacing = 5.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(3.dp.toPx())
        val maxHeight = size.height
        val minHeight = maxHeight * 0.16f
        val energy = if (isActive) (0.24f + micLevel * 0.76f).coerceIn(0.20f, 1f) else 0.14f

        for (i in 0 until barCount) {
            val centerDist = abs(i - (barCount - 1) / 2f) / ((barCount - 1) / 2f)
            val bellCurve = 1f - centerDist * 0.55f
            val harmonic = if (isActive) {
                0.5f + 0.5f * abs(sin(phase + i * 0.78f)).toFloat()
            } else {
                0.25f
            }
            val barHeight = (minHeight + (maxHeight - minHeight) * energy * bellCurve * harmonic)
                .coerceIn(minHeight, maxHeight)

            val left = i * (barWidth + spacing)
            val top = (maxHeight - barHeight) / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassWhite.copy(alpha = if (isActive) 0.95f else 0.55f),
                        accentColor.copy(alpha = if (isActive) 0.90f else 0.35f),
                        barColor.copy(alpha = if (isActive) 0.85f else 0.25f)
                    ),
                    startY = top,
                    endY = top + barHeight
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
