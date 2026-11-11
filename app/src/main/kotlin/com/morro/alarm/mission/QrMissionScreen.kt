package com.morro.alarm.mission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

// Design Tokens
private val ColorCanvasRoot = Color(0xFF000000)      // Pure AMOLED Black
private val ColorSurface = Color(0xFF0A0A0F)         // Midnight Obsidian
private val ColorSurfaceLow = Color(0xFF14141A)
private val ColorAccent = Color(0xFFFFD54F)          // Soft Warm Honey Yellow
private val ColorTextPrimary = Color(0xFFFFFFFF)     // Warm Ivory
private val ColorTextSecondary = Color(0xFF999999)
private val ColorBorder = Color(0xFF1F1F2E)
private val ColorSuccess = Color(0xFF4CAF50)

/**
 * QrMissionScreen: Alarmy-inspired QR / Barcode Scanner mission.
 *
 * Runs a real-time CameraX viewfinder with animated laser line and glowing corner brackets,
 * feeding frames to ML Kit BarcodeScanning. Verifies the scanned payload matches the registered code
 * (or any valid barcode if registeredCode is blank).
 */
@Composable
fun QrMissionScreen(
    registeredCode: String? = null,
    isRegistrationMode: Boolean = false,
    onCodeScanned: (String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(false) }
    var verifiedValue by remember { mutableStateOf("") }
    var wrongScanNotice by remember { mutableStateOf<String?>(null) }

    // Laser vertical scan line animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_progress"
    )

    // Trigger completion after short delay so user sees "CODE VERIFIED" feedback
    LaunchedEffect(isVerified) {
        if (isVerified) {
            triggerHaptic(context, view)
            delay(800)
            onCodeScanned(verifiedValue)
        }
    }

    // Auto-clear wrong scan notice after 2.5 seconds
    LaunchedEffect(wrongScanNotice) {
        if (wrongScanNotice != null) {
            delay(2500)
            wrongScanNotice = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraControl?.enableTorch(false)
            } catch (e: Exception) {
                Log.w("QrMissionScreen", "Failed to disable torch on dispose", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorCanvasRoot)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRegistrationMode) "REGISTER QR / BARCODE" else "MISSION: SCAN QR / BARCODE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            isRegistrationMode -> "Scan your morning item to lock it in"
                            !registeredCode.isNullOrBlank() -> "Match your registered code"
                            else -> "Scan any QR or product barcode"
                        },
                        fontSize = 14.sp,
                        color = ColorTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Flashlight Toggle Button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(ColorSurfaceLow, RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isTorchOn) ColorAccent else ColorBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isTorchOn = !isTorchOn
                                cameraControl?.enableTorch(isTorchOn)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isTorchOn) "ON" else "OFF",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTorchOn) ColorAccent else ColorTextSecondary
                        )
                    }

                    if (onCancel != null) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(42.dp)
                                .background(ColorSurfaceLow, RoundedCornerShape(12.dp))
                                .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = ColorTextPrimary
                            )
                        }
                    }
                }
            }

            // Center Viewfinder
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ColorSurface)
                        .border(1.dp, ColorBorder, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Live CameraX Preview
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            val mainExecutor = ContextCompat.getMainExecutor(ctx)
                            val analysisExecutor = Executors.newSingleThreadExecutor()

                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val scanner = BarcodeScanning.getClient()
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null && !isVerified) {
                                            val image = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            scanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    // All mutableStateOf writes MUST happen on the main thread.
                                                    // Wrap in mainExecutor so Compose snapshot safety is respected.
                                                    mainExecutor.execute {
                                                        if (!isVerified) {
                                                            for (barcode in barcodes) {
                                                                val raw = barcode.rawValue
                                                                if (!raw.isNullOrBlank()) {
                                                                    val trimmed = raw.trim()
                                                                    val matches = if (registeredCode.isNullOrBlank() || isRegistrationMode) {
                                                                        true
                                                                    } else {
                                                                        trimmed == registeredCode.trim()
                                                                    }
                                                                    if (matches) {
                                                                        isVerified = true
                                                                        verifiedValue = trimmed
                                                                    } else {
                                                                        wrongScanNotice = "Code mismatch: ${trimmed.take(12)}"
                                                                    }
                                                                    break
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }

                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                    cameraControl = camera.cameraControl
                                } catch (e: Exception) {
                                    Log.e("QrMissionScreen", "Camera bind failed", e)
                                }
                            }, mainExecutor)

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Glowing Corner Reticle + Animated Laser Scan Line Overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 3.5.dp.toPx()
                        val cornerLength = 26.dp.toPx()
                        val cornerColor = if (isVerified) ColorSuccess else ColorAccent
                        val w = size.width
                        val h = size.height

                        // Top-Left Corner
                        drawLine(
                            color = cornerColor,
                            start = Offset(0f, 0f),
                            end = Offset(cornerLength, 0f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = cornerColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, cornerLength),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )

                        // Top-Right Corner
                        drawLine(
                            color = cornerColor,
                            start = Offset(w - cornerLength, 0f),
                            end = Offset(w, 0f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = cornerColor,
                            start = Offset(w, 0f),
                            end = Offset(w, cornerLength),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )

                        // Bottom-Left Corner
                        drawLine(
                            color = cornerColor,
                            start = Offset(0f, h - cornerLength),
                            end = Offset(0f, h),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = cornerColor,
                            start = Offset(0f, h),
                            end = Offset(cornerLength, h),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )

                        // Bottom-Right Corner
                        drawLine(
                            color = cornerColor,
                            start = Offset(w - cornerLength, h),
                            end = Offset(w, h),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = cornerColor,
                            start = Offset(w, h - cornerLength),
                            end = Offset(w, h),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )

                        // Horizontal Laser Scan Line (only when not yet verified)
                        if (!isVerified) {
                            val laserY = h * laserProgress
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0x00FFD54F),
                                        ColorAccent,
                                        Color(0x00FFD54F)
                                    )
                                ),
                                start = Offset(0f, laserY),
                                end = Offset(w, laserY),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }

                    // Verified Overlay Badge
                    if (isVerified) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xAA000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(ColorAccent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Verified",
                                        tint = Color.Black,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "CODE VERIFIED",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorAccent,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // Camera Permission Request Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ColorBorder, ColorBorder)))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Camera Access Required",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Morro processes camera frames strictly on your device to scan your wake-up code.",
                            fontSize = 13.sp,
                            color = ColorTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ColorAccent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Grant Camera Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Bottom Information Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (wrongScanNotice != null) {
                    Text(
                        text = wrongScanNotice!!,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFEF5350),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (!registeredCode.isNullOrBlank()) {
                    Text(
                        text = "TARGET PAYLOAD: ${registeredCode.take(24)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ColorTextSecondary,
                        letterSpacing = 1.sp
                    )
                } else {
                    Text(
                        text = "POINT AT ANY BARCODE OR QR CODE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ColorTextSecondary,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

private fun triggerHaptic(context: Context, view: View) {
    try {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(150)
        }
    } catch (e: Exception) {
        Log.w("QrMissionScreen", "Haptic trigger failed", e)
    }
}
