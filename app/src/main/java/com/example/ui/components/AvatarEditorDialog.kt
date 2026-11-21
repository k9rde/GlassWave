package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ElectricSapphire
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.LiquidCyan
import com.example.ui.theme.LocalGlassAccent
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes an image Uri safely with EXIF orientation correction and bounded resolution
 * so camera photos never appear sideways or trigger out-of-memory errors.
 */
fun loadOrientedBitmap(context: Context, uri: Uri, maxDim: Int = 1024): Bitmap? {
    return try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        while (rawW / sampleSize > maxDim * 2 || rawH / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        }

        val oriented = if (!matrix.isIdentity) {
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                if (it != rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }

        val maxSide = max(oriented.width, oriented.height)
        if (maxSide > maxDim) {
            val ratio = maxDim.toFloat() / maxSide.toFloat()
            val targetW = (oriented.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (oriented.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(oriented, targetW, targetH, true).also {
                if (it != oriented) oriented.recycle()
            }
        } else {
            oriented
        }
    } catch (_: Exception) {
        null
    }
}

fun encodeAvatarThumbnailBase64(bitmap: Bitmap): String {
    val thumb = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
    var quality = 72
    var encoded = ""
    while (quality >= 35) {
        val baos = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        encoded = b64
        if (b64.length <= 10500) break
        quality -= 12
    }
    if (thumb != bitmap) thumb.recycle()
    return encoded
}

private fun renderCroppedSquareAvatar(
    source: Bitmap,
    rotationDegrees: Int,
    scale: Float,
    offsetXNorm: Float,
    offsetYNorm: Float,
    outputSizePx: Int = 360
): Bitmap {
    val rotMatrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = if (rotationDegrees % 360 != 0) {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, rotMatrix, true)
    } else {
        source
    }

    val output = Bitmap.createBitmap(outputSizePx, outputSizePx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)
    canvas.drawColor(android.graphics.Color.rgb(10, 16, 34))

    // Center-crop ContentScale.Crop base scale inside output square
    val baseScale = max(
        outputSizePx.toFloat() / rotated.width.toFloat(),
        outputSizePx.toFloat() / rotated.height.toFloat()
    )
    val totalScale = baseScale * scale

    val drawMatrix = Matrix().apply {
        postTranslate(-rotated.width / 2f, -rotated.height / 2f)
        postScale(totalScale, totalScale)
        postTranslate(
            outputSizePx / 2f + offsetXNorm * outputSizePx,
            outputSizePx / 2f + offsetYNorm * outputSizePx
        )
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(rotated, drawMatrix, paint)
    if (rotated != source) rotated.recycle()
    return output
}

@Composable
fun AvatarEditorDialog(
    sourceBitmap: Bitmap,
    onSave: (croppedBitmap: Bitmap, thumbBase64: String) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalGlassAccent.current.primary
    var scale by remember(sourceBitmap) { mutableFloatStateOf(1.1f) }
    var offset by remember(sourceBitmap) { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember(sourceBitmap) { mutableIntStateOf(0) }
    var boxSizePx by remember { mutableIntStateOf(1) }

    val previewBitmap = remember(sourceBitmap, rotationDegrees) {
        if (rotationDegrees % 360 == 0) {
            sourceBitmap
        } else {
            val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, m, true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        icon = {
            Icon(
                imageVector = Icons.Rounded.Crop,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(26.dp)
            )
        },
        title = {
            Text(
                text = "تعديل صورة الملف الشخصي",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "اسحب الصورة وحركها أو استخدم التكبير والتدوير قبل الحفظ",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(ObsidianDeep)
                        .onSizeChanged { boxSizePx = max(it.width, 1) }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 3.5f)
                                scale = newScale
                                val maxOffset = boxSizePx * 0.45f * newScale
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                                    y = (offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "معاينة الصورة",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )

                    // Circular crop overlay mask
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = min(size.width, size.height) * 0.46f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val overlayPath = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                            addOval(
                                androidx.compose.ui.geometry.Rect(
                                    center.x - radius,
                                    center.y - radius,
                                    center.x + radius,
                                    center.y + radius
                                )
                            )
                        }
                        drawPath(overlayPath, color = Color.Black.copy(alpha = 0.56f))
                        drawCircle(
                            color = accent,
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                // Zoom Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 1f..3.5f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", scale)}x",
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Rotate & Reset controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.RotateRight,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("تدوير 90°", color = GlassWhite, style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            scale = 1.1f
                            offset = Offset.Zero
                            rotationDegrees = 0
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("إعادة ضبط", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normX = offset.x / boxSizePx.toFloat()
                    val normY = offset.y / boxSizePx.toFloat()
                    val cropped = renderCroppedSquareAvatar(
                        source = sourceBitmap,
                        rotationDegrees = rotationDegrees,
                        scale = scale,
                        offsetXNorm = normX,
                        offsetYNorm = normY,
                        outputSizePx = 360
                    )
                    val thumb = encodeAvatarThumbnailBase64(cropped)
                    onSave(cropped, thumb)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = ObsidianDeep
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("حفظ الصورة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}

@Composable
fun MemberAvatarCircle(
    name: String,
    localPath: String? = null,
    base64Thumb: String? = null,
    size: Dp = 44.dp,
    borderColor: Color = LiquidCyan,
    statusDotColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(localPath, base64Thumb) {
        var decoded: Bitmap? = null
        if (!localPath.isNullOrBlank()) {
            val file = File(localPath)
            if (file.exists()) {
                decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            }
        }
        if (decoded == null && !base64Thumb.isNullOrBlank()) {
            decoded = runCatching {
                val bytes = Base64.decode(base64Thumb, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
        decoded?.asImageBitmap()
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(borderColor.copy(alpha = 0.28f), ElectricSapphire.copy(alpha = 0.16f))
                    )
                )
                .border(1.5.dp, borderColor.copy(alpha = 0.78f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "صورة $name",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString()
                if (!initial.isNullOrBlank()) {
                    Text(
                        text = initial,
                        color = GlassWhite,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = "صورة $name",
                        tint = GlassWhite,
                        modifier = Modifier.size(size * 0.55f)
                    )
                }
            }
        }
        if (statusDotColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )
        }
    }
}
