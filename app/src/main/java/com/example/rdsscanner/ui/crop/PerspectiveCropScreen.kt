package com.example.rdsscanner.ui.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rdsscanner.scanner.ImageProcessor
import com.example.rdsscanner.theme.EmeraldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.hypot

enum class Corner {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT
}

@Composable
fun PerspectiveCropScreen(
    imageFile: File,
    onBack: () -> Unit,
    onCropped: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableIntStateOf(0) }

    // Canvas display coordinates for the 4 corners
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var tl by remember { mutableStateOf(Offset.Zero) }
    var tr by remember { mutableStateOf(Offset.Zero) }
    var br by remember { mutableStateOf(Offset.Zero) }
    var bl by remember { mutableStateOf(Offset.Zero) }
    var activeCorner by remember { mutableStateOf(Corner.NONE) }

    // Load bitmap
    LaunchedEffect(imageFile, rotationAngle) {
        withContext(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bmp != null) {
                sourceBitmap = if (rotationAngle != 0) {
                    ImageProcessor.rotateBitmap(bmp, rotationAngle.toFloat())
                } else {
                    bmp
                }
            }
        }
    }

    // Initialize corner positions inside canvas margins
    fun resetCorners(w: Float, h: Float) {
        val insetX = w * 0.08f
        val insetY = h * 0.08f
        tl = Offset(insetX, insetY)
        tr = Offset(w - insetX, insetY)
        br = Offset(w - insetX, h - insetY)
        bl = Offset(insetX, h - insetY)
    }

    LaunchedEffect(canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            resetCorners(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        }
    }

    Scaffold(
        topBar = {
            Surface(color = Color.Black) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Text(
                        text = "Adjust Page Boundaries",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    IconButton(
                        onClick = {
                            if (sourceBitmap != null && canvasSize.width > 0 && canvasSize.height > 0 && !isProcessing) {
                                isProcessing = true
                                coroutineScope.launch {
                                    val bmp = sourceBitmap!!
                                    // Scale canvas points back to original bitmap coordinates
                                    val scaleX = bmp.width.toFloat() / canvasSize.width.toFloat()
                                    val scaleY = bmp.height.toFloat() / canvasSize.height.toFloat()

                                    val bmpTl = PointF(tl.x * scaleX, tl.y * scaleY)
                                    val bmpTr = PointF(tr.x * scaleX, tr.y * scaleY)
                                    val bmpBr = PointF(br.x * scaleX, br.y * scaleY)
                                    val bmpBl = PointF(bl.x * scaleX, bl.y * scaleY)

                                    val cropped = ImageProcessor.warpPerspective(bmp, bmpTl, bmpTr, bmpBr, bmpBl)
                                    val outputFile = File(context.cacheDir, "crop_${UUID.randomUUID()}.jpg")
                                    ImageProcessor.saveBitmapToFile(cropped, outputFile, quality = 95)

                                    isProcessing = false
                                    onCropped(outputFile)
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = EmeraldPrimary, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Done", tint = EmeraldPrimary)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = Color.Black) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (canvasSize.width > 0) {
                                resetCorners(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                            }
                        }
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Corners", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = { rotationAngle = (rotationAngle + 90) % 360 }
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotate 90°", color = Color.White)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            if (sourceBitmap != null) {
                val bmp = sourceBitmap!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .onGloballyPositioned { coordinates ->
                            canvasSize = coordinates.size
                        }
                ) {
                    // Interactive Canvas with Image + 4 Corner Handles
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(canvasSize) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        val touchRadius = 80f
                                        activeCorner = when {
                                            hypot((pos.x - tl.x).toDouble(), (pos.y - tl.y).toDouble()) < touchRadius -> Corner.TOP_LEFT
                                            hypot((pos.x - tr.x).toDouble(), (pos.y - tr.y).toDouble()) < touchRadius -> Corner.TOP_RIGHT
                                            hypot((pos.x - br.x).toDouble(), (pos.y - br.y).toDouble()) < touchRadius -> Corner.BOTTOM_RIGHT
                                            hypot((pos.x - bl.x).toDouble(), (pos.y - bl.y).toDouble()) < touchRadius -> Corner.BOTTOM_LEFT
                                            else -> Corner.NONE
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val w = canvasSize.width.toFloat()
                                        val h = canvasSize.height.toFloat()

                                        when (activeCorner) {
                                            Corner.TOP_LEFT -> {
                                                tl = Offset(
                                                    (tl.x + dragAmount.x).coerceIn(0f, tr.x - 40f),
                                                    (tl.y + dragAmount.y).coerceIn(0f, bl.y - 40f)
                                                )
                                            }
                                            Corner.TOP_RIGHT -> {
                                                tr = Offset(
                                                    (tr.x + dragAmount.x).coerceIn(tl.x + 40f, w),
                                                    (tr.y + dragAmount.y).coerceIn(0f, br.y - 40f)
                                                )
                                            }
                                            Corner.BOTTOM_RIGHT -> {
                                                br = Offset(
                                                    (br.x + dragAmount.x).coerceIn(bl.x + 40f, w),
                                                    (br.y + dragAmount.y).coerceIn(tr.y + 40f, h)
                                                )
                                            }
                                            Corner.BOTTOM_LEFT -> {
                                                bl = Offset(
                                                    (bl.x + dragAmount.x).coerceIn(0f, br.x - 40f),
                                                    (bl.y + dragAmount.y).coerceIn(tl.y + 40f, h)
                                                )
                                            }
                                            Corner.NONE -> {}
                                        }
                                    },
                                    onDragEnd = { activeCorner = Corner.NONE },
                                    onDragCancel = { activeCorner = Corner.NONE }
                                )
                            }
                    ) {
                        // 1. Draw source background image scaled to canvas
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstSize = canvasSize
                        )

                        // 2. Draw semi-transparent green tint inside polygon
                        val polygonPath = Path().apply {
                            moveTo(tl.x, tl.y)
                            lineTo(tr.x, tr.y)
                            lineTo(br.x, br.y)
                            lineTo(bl.x, bl.y)
                            close()
                        }
                        drawPath(polygonPath, color = Color(0x3300A86B))

                        // 3. Draw boundary lines
                        drawPath(polygonPath, color = Color(0xFF00A86B), style = Stroke(width = 4f))

                        // 4. Draw 4 Corner Handles with circles
                        val handleRadius = 24f
                        val cornerPoints = listOf(tl, tr, br, bl)
                        cornerPoints.forEach { pt ->
                            drawCircle(color = Color.White, radius = handleRadius, center = pt)
                            drawCircle(color = Color(0xFF00A86B), radius = handleRadius - 6f, center = pt)
                        }
                    }

                    // Tip banner
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Drag the 4 corner dots to match paper edges",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
        }
    }
}
