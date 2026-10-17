package com.example.rdsscanner.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.rdsscanner.scanner.ImageProcessor
import com.example.rdsscanner.theme.EmeraldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ScannerMode(val title: String) {
    DOCS("Docs"),
    ID_CARD("ID Card"),
    PASSPORT("Passport"),
    BATCH("Batch Scan")
}

@Composable
fun CameraScannerScreen(
    initialMode: ScannerMode = ScannerMode.DOCS,
    onBack: () -> Unit,
    onSingleCaptured: (File) -> Unit,
    onIdCardCaptured: (frontFile: File, backFile: File?) -> Unit,
    onPassportCaptured: (File) -> Unit,
    onBatchCaptured: (List<File>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var currentMode by remember { mutableStateOf(initialMode) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isTorchOn by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    // ID Card flow state
    var idCardFrontFile by remember { mutableStateOf<File?>(null) }
    var isScanningIdBack by remember { mutableStateOf(false) }

    // Batch scan pages
    val batchFiles = remember { mutableStateListOf<File>() }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setFlashMode(flashMode)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    // Gallery picker fallback
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val tempFile = File(context.cacheDir, "gallery_${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                when (currentMode) {
                    ScannerMode.DOCS -> onSingleCaptured(tempFile)
                    ScannerMode.PASSPORT -> onPassportCaptured(tempFile)
                    ScannerMode.ID_CARD -> {
                        if (!isScanningIdBack) {
                            idCardFrontFile = tempFile
                            isScanningIdBack = true
                            Toast.makeText(context, "Front selected! Now select Back side", Toast.LENGTH_SHORT).show()
                        } else {
                            onIdCardCaptured(idCardFrontFile!!, tempFile)
                        }
                    }
                    ScannerMode.BATCH -> {
                        batchFiles.add(tempFile)
                    }
                }
            }
        }
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            // CameraX Live Preview
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraScanner", "Use case binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // Overlay with Card/Passport guides & darkened frame
            ScannerOverlay(
                mode = currentMode,
                showGrid = showGrid,
                isScanningIdBack = isScanningIdBack
            )
        } else {
            // Permission Request Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(EmeraldPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(44.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Camera Permission Required",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "RDS SCANNER needs camera access to capture documents, ID cards, and passports clearly.",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Grant Camera Permission", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            // Mode prompt pill
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Text(
                    text = when (currentMode) {
                        ScannerMode.DOCS -> "Document Mode"
                        ScannerMode.ID_CARD -> if (isScanningIdBack) "Step 2: BACK Side" else "Step 1: FRONT Side"
                        ScannerMode.PASSPORT -> "Passport Mode"
                        ScannerMode.BATCH -> "Batch Mode (${batchFiles.size} scanned)"
                    },
                    color = if (currentMode == ScannerMode.ID_CARD && isScanningIdBack) EmeraldPrimary else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Torch toggle
                IconButton(
                    onClick = {
                        isTorchOn = !isTorchOn
                        camera?.cameraControl?.enableTorch(isTorchOn)
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Highlight,
                        contentDescription = "Torch",
                        tint = if (isTorchOn) EmeraldPrimary else Color.White
                    )
                }

                // Flash toggle
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                            else -> Icons.Default.FlashAuto
                        },
                        contentDescription = "Flash",
                        tint = if (flashMode == ImageCapture.FLASH_MODE_ON) EmeraldPrimary else Color.White
                    )
                }

                // Grid toggle
                IconButton(
                    onClick = { showGrid = !showGrid },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.GridOn,
                        contentDescription = "Grid",
                        tint = if (showGrid) EmeraldPrimary else Color.White
                    )
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(bottom = 24.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ScannerMode.values().forEach { mode ->
                    val isSelected = mode == currentMode
                    Text(
                        text = mode.title,
                        color = if (isSelected) EmeraldPrimary else Color.Gray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable {
                                currentMode = mode
                                isScanningIdBack = false
                                idCardFrontFile = null
                            }
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Shutter Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Import Button
                IconButton(
                    onClick = { galleryPicker.launch("image/*") },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.DarkGray.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                }

                // Big Shutter Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) Color.Gray else EmeraldPrimary)
                        .clickable(enabled = !isCapturing) {
                            isCapturing = true
                            val photoFile = File(context.cacheDir, "scan_${UUID.randomUUID()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                val rotated = ImageProcessor.loadRotatedBitmapFromFile(photoFile)
                                                if (rotated != null) {
                                                    ImageProcessor.saveBitmapToFile(rotated, photoFile, quality = 95)
                                                }
                                            }
                                            isCapturing = false
                                            when (currentMode) {
                                                ScannerMode.DOCS -> onSingleCaptured(photoFile)
                                                ScannerMode.PASSPORT -> onPassportCaptured(photoFile)
                                                ScannerMode.ID_CARD -> {
                                                    if (!isScanningIdBack) {
                                                        idCardFrontFile = photoFile
                                                        isScanningIdBack = true
                                                        Toast.makeText(context, "Front captured! Now align Back side", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        onIdCardCaptured(idCardFrontFile!!, photoFile)
                                                    }
                                                }
                                                ScannerMode.BATCH -> {
                                                    batchFiles.add(photoFile)
                                                    Toast.makeText(context, "Page ${batchFiles.size} captured!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                // Batch Mode Done Button or Placeholder
                if (currentMode == ScannerMode.BATCH && batchFiles.isNotEmpty()) {
                    IconButton(
                        onClick = { onBatchCaptured(batchFiles.toList()) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(EmeraldPrimary, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White)
                    }
                } else if (currentMode == ScannerMode.ID_CARD && isScanningIdBack) {
                    // Skip back side button
                    Text(
                        text = "Skip Back",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { onIdCardCaptured(idCardFrontFile!!, null) }
                            .padding(8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay(
    mode: ScannerMode,
    showGrid: Boolean,
    isScanningIdBack: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Calculate transparent cutout box based on mode
        val cutoutRect = when (mode) {
            ScannerMode.ID_CARD -> {
                // ISO 7810 ID-1 aspect ratio: 85.6 / 53.98 = ~1.586
                val boxWidth = width * 0.88f
                val boxHeight = boxWidth / 1.586f
                val left = (width - boxWidth) / 2f
                val top = (height - boxHeight) / 2f - 40f
                Rect(left, top, left + boxWidth, top + boxHeight)
            }
            ScannerMode.PASSPORT -> {
                // Passport spread aspect ratio: ~1.42
                val boxWidth = width * 0.90f
                val boxHeight = boxWidth / 1.42f
                val left = (width - boxWidth) / 2f
                val top = (height - boxHeight) / 2f - 40f
                Rect(left, top, left + boxWidth, top + boxHeight)
            }
            else -> {
                // Document A4 portrait frame (1:1.414)
                val boxWidth = width * 0.85f
                val boxHeight = boxWidth * 1.35f
                val left = (width - boxWidth) / 2f
                val top = (height - boxHeight) / 2f - 30f
                Rect(left, top, left + boxWidth, top + boxHeight)
            }
        }

        // Darkened surrounding mask
        val path = Path().apply {
            addRect(Rect(0f, 0f, width, height))
            addRoundRect(RoundRect(cutoutRect, CornerRadius(20f, 20f)))
        }

        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }

        // Draw Guide Border
        drawRoundRect(
            color = if (mode == ScannerMode.ID_CARD && isScanningIdBack) Color(0xFF00E5FF) else Color(0xFF00A86B),
            topLeft = Offset(cutoutRect.left, cutoutRect.top),
            size = Size(cutoutRect.width, cutoutRect.height),
            cornerRadius = CornerRadius(20f, 20f),
            style = Stroke(width = 4f)
        )

        // Corner accents
        val cornerLen = 32f
        val strokeW = 8f
        val cornerColor = Color.White

        // Top-Left
        drawLine(cornerColor, Offset(cutoutRect.left - 2f, cutoutRect.top), Offset(cutoutRect.left + cornerLen, cutoutRect.top), strokeW)
        drawLine(cornerColor, Offset(cutoutRect.left, cutoutRect.top - 2f), Offset(cutoutRect.left, cutoutRect.top + cornerLen), strokeW)

        // Top-Right
        drawLine(cornerColor, Offset(cutoutRect.right + 2f, cutoutRect.top), Offset(cutoutRect.right - cornerLen, cutoutRect.top), strokeW)
        drawLine(cornerColor, Offset(cutoutRect.right, cutoutRect.top - 2f), Offset(cutoutRect.right, cutoutRect.top + cornerLen), strokeW)

        // Bottom-Left
        drawLine(cornerColor, Offset(cutoutRect.left - 2f, cutoutRect.bottom), Offset(cutoutRect.left + cornerLen, cutoutRect.bottom), strokeW)
        drawLine(cornerColor, Offset(cutoutRect.left, cutoutRect.bottom + 2f), Offset(cutoutRect.left, cutoutRect.bottom - cornerLen), strokeW)

        // Bottom-Right
        drawLine(cornerColor, Offset(cutoutRect.right + 2f, cutoutRect.bottom), Offset(cutoutRect.right - cornerLen, cutoutRect.bottom), strokeW)
        drawLine(cornerColor, Offset(cutoutRect.right, cutoutRect.bottom + 2f), Offset(cutoutRect.right, cutoutRect.bottom - cornerLen), strokeW)

        // Draw 3x3 Grid if enabled
        if (showGrid) {
            val gridPaint = Color.White.copy(alpha = 0.25f)
            val dx = cutoutRect.width / 3f
            val dy = cutoutRect.height / 3f

            drawLine(gridPaint, Offset(cutoutRect.left + dx, cutoutRect.top), Offset(cutoutRect.left + dx, cutoutRect.bottom), 2f)
            drawLine(gridPaint, Offset(cutoutRect.left + dx * 2, cutoutRect.top), Offset(cutoutRect.left + dx * 2, cutoutRect.bottom), 2f)
            drawLine(gridPaint, Offset(cutoutRect.left, cutoutRect.top + dy), Offset(cutoutRect.right, cutoutRect.top + dy), 2f)
            drawLine(gridPaint, Offset(cutoutRect.left, cutoutRect.top + dy * 2), Offset(cutoutRect.right, cutoutRect.top + dy * 2), 2f)
        }
    }
}
