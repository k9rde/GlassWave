package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.CloudRoom
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalGlassAccent
import com.example.ui.theme.LuminousEmerald
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun RoomQrShareDialog(
    room: CloudRoom,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalGlassAccent.current.primary
    val payload = remember(room.roomCode, room.roomPasscode) {
        "GLASSWAVE|${room.roomCode}|${room.roomPasscode}"
    }
    val qrBitmap = remember(payload) {
        generateQrBitmap(payload, sizePx = 560)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        icon = {
            Icon(
                imageVector = Icons.Rounded.QrCode2,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = "رمز QR لغرفة ${room.roomName}",
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
                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White)
                            .border(2.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code للغرفة",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Text(
                    text = "الرمز: ${room.roomCode}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = "يمكن لصديقك مسح هذا الرمز بالكاميرا للانضمام فوراً بدون كتابة الرمز وكلمة المرور.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        val shareText = "انضم معي في غرفة اللاسلكي (${room.roomName}) على GlassWave!\nالرمز: ${room.roomCode}\nكلمة المرور: ${room.roomPasscode}\nhttps://k9rde.github.io/GlassWave/"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة الغرفة"))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = ObsidianDeep
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("مشاركة بيانات الغرفة", fontWeight = FontWeight.Bold)
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
fun QrScannerDialog(
    onScanned: (code: String, passcode: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accent = LocalGlassAccent.current.primary

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        icon = {
            Icon(
                imageVector = Icons.Rounded.QrCodeScanner,
                contentDescription = null,
                tint = LuminousEmerald,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "مسح رمز QR للغرفة",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!hasCameraPermission) {
                    Text(
                        text = "يرجى منح إذن الكاميرا لمسح رمز QR الخاص بالغرفة.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = ObsidianDeep
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("السماح بالكاميرا", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val handled = remember { AtomicBoolean(false) }
                    val executor = remember { Executors.newSingleThreadExecutor() }

                    DisposableEffect(Unit) {
                        onDispose {
                            executor.shutdown()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(2.dp, LuminousEmerald.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                                        ?: return@addListener
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val reader = MultiFormatReader()
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { imgAnalysis ->
                                            imgAnalysis.setAnalyzer(executor) { imageProxy ->
                                                if (handled.get()) {
                                                    imageProxy.close()
                                                    return@setAnalyzer
                                                }
                                                try {
                                                    val buffer = imageProxy.planes[0].buffer
                                                    val bytes = ByteArray(buffer.remaining())
                                                    buffer.get(bytes)
                                                    val source = PlanarYUVLuminanceSource(
                                                        bytes,
                                                        imageProxy.width,
                                                        imageProxy.height,
                                                        0,
                                                        0,
                                                        imageProxy.width,
                                                        imageProxy.height,
                                                        false
                                                    )
                                                    val bitmap = BinaryBitmap(HybridBinarizer(source))
                                                    val result = reader.decode(bitmap)
                                                    val rawText = result.text?.trim().orEmpty()
                                                    if (rawText.isNotBlank() && handled.compareAndSet(false, true)) {
                                                        val parts = rawText.split("|")
                                                        val (code, pass) = if (parts.size >= 3 && parts[0] == "GLASSWAVE") {
                                                            parts[1].trim() to parts[2].trim()
                                                        } else {
                                                            rawText.uppercase().filter { it.isLetterOrDigit() }.take(12) to ""
                                                        }
                                                        previewView.post {
                                                            onScanned(code, pass)
                                                        }
                                                    }
                                                } catch (_: Exception) {
                                                } finally {
                                                    reader.reset()
                                                    imageProxy.close()
                                                }
                                            }
                                        }
                                    runCatching {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analysis
                                        )
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )
                    }
                    Text(
                        text = "وجّه الكاميرا نحو رمز QR في هاتف صديقك",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassWhite.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
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

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
                )
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
