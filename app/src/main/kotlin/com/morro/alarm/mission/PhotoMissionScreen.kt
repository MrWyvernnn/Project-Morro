package com.morro.alarm.mission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.delay

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
 * State machine for the Photo Mission workflow.
 */
enum class PhotoState {
    LIVE_CAMERA,
    ANALYZING_FRAME,
    VERIFIED_SUCCESS,
    RETAKE_REQUIRED
}

/**
 * PhotoMissionScreen: Alarmy-inspired 30-item household object recognition engine.
 *
 * Uses CameraX live preview with targeting brackets, captures an image frame on shutter tap,
 * and runs ML Kit ImageLabeling offline to verify presence of the target item.
 */
@Composable
fun PhotoMissionScreen(
    targetItem: PhotoTargetItem,
    onMissionSuccess: () -> Unit,
    onSwapItem: (() -> Unit)? = null
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

    var photoState by remember { mutableStateOf(PhotoState.LIVE_CAMERA) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var matchedLabelText by remember { mutableStateOf("") }
    var detectedLabels by remember { mutableStateOf<List<String>>(emptyList()) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    // Success delay before proceeding
    LaunchedEffect(photoState) {
        if (photoState == PhotoState.VERIFIED_SUCCESS) {
            triggerHaptic(context, view)
            delay(800)
            onMissionSuccess()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraControl?.enableTorch(false)
            } catch (e: Exception) {
                Log.w("PhotoMissionScreen", "Failed to disable torch on dispose", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorCanvasRoot)
    ) {
        // Background Viewfinder or Dimmed Captured Photo
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ColorSurface)
                    .border(1.dp, ColorBorder, RoundedCornerShape(24.dp))
            ) {
                // Live camera preview (active during LIVE_CAMERA and ANALYZING_FRAME)
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        val mainExecutor = ContextCompat.getMainExecutor(ctx)

                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                                cameraControl = camera.cameraControl
                            } catch (e: Exception) {
                                Log.e("PhotoMissionScreen", "Camera bind failed", e)
                            }
                        }, mainExecutor)

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Targeting Brackets Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bracketLength = 32.dp.toPx()
                    val stroke = 3.dp.toPx()
                    val bracketColor = if (photoState == PhotoState.VERIFIED_SUCCESS) ColorSuccess else ColorAccent.copy(alpha = 0.85f)
                    val w = size.width
                    val h = size.height

                    // Inset padding from viewfinder border
                    val inset = 24.dp.toPx()

                    // Top Left
                    drawLine(bracketColor, Offset(inset, inset), Offset(inset + bracketLength, inset), stroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + bracketLength), stroke, StrokeCap.Round)

                    // Top Right
                    drawLine(bracketColor, Offset(w - inset - bracketLength, inset), Offset(w - inset, inset), stroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(w - inset, inset), Offset(w - inset, inset + bracketLength), stroke, StrokeCap.Round)

                    // Bottom Left
                    drawLine(bracketColor, Offset(inset, h - inset), Offset(inset + bracketLength, h - inset), stroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(inset, h - inset - bracketLength), Offset(inset, h - inset), stroke, StrokeCap.Round)

                    // Bottom Right
                    drawLine(bracketColor, Offset(w - inset - bracketLength, h - inset), Offset(w - inset, h - inset), stroke, StrokeCap.Round)
                    drawLine(bracketColor, Offset(w - inset, h - inset - bracketLength), Offset(w - inset, h - inset), stroke, StrokeCap.Round)
                }

                // If in RETAKE_REQUIRED or ANALYZING, show captured image overlay dimmed
                if (capturedBitmap != null && (photoState == PhotoState.RETAKE_REQUIRED || photoState == PhotoState.ANALYZING_FRAME)) {
                    Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Captured Frame",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )
                }
            }
        }

        // Foreground UI Elements
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP TARGET BANNER CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = ColorSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, ColorAccent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WAKE-UP PHOTO MISSION",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorAccent,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Take a photo of: ${targetItem.displayName}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Torch Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(ColorSurfaceLow, RoundedCornerShape(12.dp))
                                .border(1.dp, if (isTorchOn) ColorAccent else ColorBorder, RoundedCornerShape(12.dp))
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

                        // Swap Item Button
                        if (onSwapItem != null && photoState == PhotoState.LIVE_CAMERA) {
                            IconButton(
                                onClick = onSwapItem,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(ColorSurfaceLow, RoundedCornerShape(12.dp))
                                    .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Swap Target Item",
                                    tint = ColorAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // MIDDLE STATES OVERLAY
            when (photoState) {
                PhotoState.ANALYZING_FRAME -> {
                    Card(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ColorAccent)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = ColorAccent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Analyzing object...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColorTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Matching against ${targetItem.displayName}",
                                fontSize = 12.sp,
                                color = ColorTextSecondary
                            )
                        }
                    }
                }

                PhotoState.VERIFIED_SUCCESS -> {
                    Card(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, ColorSuccess)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(ColorSuccess, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Verified",
                                    tint = Color.Black,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "MATCH CONFIRMED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorAccent,
                                letterSpacing = 1.sp
                            )
                            if (matchedLabelText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = matchedLabelText.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = ColorTextSecondary
                                )
                            }
                        }
                    }
                }

                PhotoState.RETAKE_REQUIRED -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ColorAccent.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Couldn't recognize ${targetItem.displayName}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val detectedSummary = if (detectedLabels.isNotEmpty()) {
                                detectedLabels.take(3).joinToString { it }
                            } else {
                                "Nothing recognizable"
                            }
                            Text(
                                text = "Detected: $detectedSummary. Move closer and ensure good lighting.",
                                fontSize = 13.sp,
                                color = ColorTextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    capturedBitmap = null
                                    detectedLabels = emptyList()
                                    photoState = PhotoState.LIVE_CAMERA
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ColorAccent,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = "RETAKE PICTURE",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                PhotoState.LIVE_CAMERA -> {
                    // Spacer keeping layout balanced
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }

            // BOTTOM SHUTTER BAR (Only shown in LIVE_CAMERA state)
            if (photoState == PhotoState.LIVE_CAMERA) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Large circular 72.dp capture button with #FFD54F outer ring and warm-white core
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .border(3.5.dp, ColorAccent, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(ColorTextPrimary)
                            .clickable {
                                photoState = PhotoState.ANALYZING_FRAME
                                val executor = ContextCompat.getMainExecutor(context)
                                imageCapture.takePicture(
                                    executor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                            try {
                                                val bitmap = imageProxy.toBitmap()
                                                capturedBitmap = bitmap

                                                val mediaImage = imageProxy.image
                                                val inputImage = if (mediaImage != null) {
                                                    InputImage.fromMediaImage(
                                                        mediaImage,
                                                        imageProxy.imageInfo.rotationDegrees
                                                    )
                                                } else {
                                                    // Use actual rotation degrees so ML Kit labels correctly
                                                    // regardless of how the device was held when the capture fired.
                                                    InputImage.fromBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
                                                }

                                                val labeler = ImageLabeling.getClient(
                                                    ImageLabelerOptions.Builder()
                                                        .setConfidenceThreshold(0.55f)
                                                        .build()
                                                )

                                                labeler.process(inputImage)
                                                    .addOnSuccessListener { labels ->
                                                        val match = labels.firstOrNull { label ->
                                                            val text = label.text.lowercase().trim()
                                                            targetItem.mlKitSynonyms.any { syn ->
                                                                val s = syn.lowercase().trim()
                                                                text == s || text.contains(s) || s.contains(text)
                                                            } || text == targetItem.id.lowercase() || text == targetItem.displayName.lowercase()
                                                        }

                                                        if (match != null) {
                                                            matchedLabelText = match.text
                                                            photoState = PhotoState.VERIFIED_SUCCESS
                                                        } else {
                                                            detectedLabels = labels.map { it.text }
                                                            photoState = PhotoState.RETAKE_REQUIRED
                                                        }
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.w("PhotoMissionScreen", "Labeling failed", e)
                                                        detectedLabels = emptyList()
                                                        photoState = PhotoState.RETAKE_REQUIRED
                                                    }
                                                    .addOnCompleteListener {
                                                        imageProxy.close()
                                                    }
                                            } catch (e: Exception) {
                                                Log.e("PhotoMissionScreen", "Processing exception", e)
                                                imageProxy.close()
                                                photoState = PhotoState.RETAKE_REQUIRED
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("PhotoMissionScreen", "Capture failed", exception)
                                            photoState = PhotoState.RETAKE_REQUIRED
                                        }
                                    }
                                )
                            }
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(1.dp))
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
        Log.w("PhotoMissionScreen", "Haptic trigger failed", e)
    }
}
