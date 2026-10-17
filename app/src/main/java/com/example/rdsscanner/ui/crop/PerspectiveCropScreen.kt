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
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.min

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

    // Load bitmap with EXIF rotation check
    LaunchedEffect(imageFile, rotationAngle) {
        withContext(Dispatchers.IO) {
            val bmp = ImageProcessor.loadRotatedBitmapFromFile(imageFile)
            if (bmp != null) {
                sourceBitmap = if (rotationAngle != 0) {
                    ImageProcessor.rotateBitmap(bmp, rotationAngle.toFloat())
                } else {
                    bmp
                }
            }
        }
    }

    val bmp = sourceBitmap
    val bmpW = bmp?.width?.toFloat() ?: 1f
    val bmpH = bmp?.height?.toFloat() ?: 1f
    val scale = if (canvasSize.width > 0 && canvasSize.height > 0) {
        min(canvasSize.width.toFloat() / bmpW, canvasSize.height.toFloat() / bmpH)
    } else 1f
    val drawWidth = bmpW * scale
    val drawHeight = bmpH * scale
    val offsetX = if (canvasSize.width > 0) (canvasSize.width.toFloat() - drawWidth) / 2f else 0f
    val offsetY = if (canvasSize.height > 0) (canvasSize.height.toFloat() - drawHeight) / 2f else 0f

    // Initialize corner positions inside fitted image margins
    fun resetCorners() {
        if (drawWidth <= 0 || drawHeight <= 0) return
        val insetX = drawWidth * 0.05f
        val insetY = drawHeight * 0.05f
        tl = Offset(offsetX + insetX, offsetY + insetY)
        tr = Offset(offsetX + drawWidth - insetX, offsetY + insetY)
        br = Offset(offsetX + drawWidth - insetX, offsetY + drawHeight - insetY)
        bl = Offset(offsetX + insetX, offsetY + drawHeight - insetY)
    }

    LaunchedEffect(canvasSize, sourceBitmap) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && sourceBitmap != null) {
            resetCorners()
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
                                    val currentBmp = sourceBitmap!!
                                    // Scale canvas points back to original bitmap coordinates without distortion
                                    val bmpTl = PointF(
                                        ((tl.x - offsetX) / scale).coerceIn(0f, currentBmp.width.toFloat()),
                                        ((tl.y - offsetY) / scale).coerceIn(0f, currentBmp.height.toFloat())
                                    )
                                    val bmpTr = PointF(
                                        ((tr.x - offsetX) / scale).coerceIn(0f, currentBmp.width.toFloat()),
                                        ((tr.y - offsetY) / scale).coerceIn(0f, currentBmp.height.toFloat())
                                    )
                                    val bmpBr = PointF(
                                        ((br.x - offsetX) / scale).coerceIn(0f, currentBmp.width.toFloat()),
                                        ((br.y - offsetY) / scale).coerceIn(0f, currentBmp.height.toFloat())
                                    )
                                    val bmpBl = PointF(
                                        ((bl.x - offsetX) / scale).coerceIn(0f, currentBmp.width.toFloat()),
                                        ((bl.y - offsetY) / scale).coerceIn(0f, currentBmp.height.toFloat())
                                    )

                                    val cropped = ImageProcessor.warpPerspective(currentBmp, bmpTl, bmpTr, bmpBr, bmpBl)
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
                        onClick = { resetCorners() }
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
                val currentBmp = sourceBitmap!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .onGloballyPositioned { coordinates ->
                            canvasSize = coordinates.size
                        }
                ) {
                    // Interactive Canvas with un-stretched Image + 4 Corner Handles
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(canvasSize, drawWidth, drawHeight, offsetX, offsetY) {
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
                                        val minX = offsetX
                                        val maxX = offsetX + drawWidth
                                        val minY = offsetY
                                        val maxY = offsetY + drawHeight

                                        when (activeCorner) {
                                            Corner.TOP_LEFT -> {
                                                tl = Offset(
                                                    (tl.x + dragAmount.x).coerceIn(minX, tr.x - 30f),
                                                    (tl.y + dragAmount.y).coerceIn(minY, bl.y - 30f)
                                                )
                                            }
                                            Corner.TOP_RIGHT -> {
                                                tr = Offset(
                                                    (tr.x + dragAmount.x).coerceIn(tl.x + 30f, maxX),
                                                    (tr.y + dragAmount.y).coerceIn(minY, br.y - 30f)
                                                )
                                            }
                                            Corner.BOTTOM_RIGHT -> {
                                                br = Offset(
                                                    (br.x + dragAmount.x).coerceIn(bl.x + 30f, maxX),
                                                    (br.y + dragAmount.y).coerceIn(tr.y + 30f, maxY)
                                                )
                                            }
                                            Corner.BOTTOM_LEFT -> {
                                                bl = Offset(
                                                    (bl.x + dragAmount.x).coerceIn(minX, br.x - 30f),
                                                    (bl.y + dragAmount.y).coerceIn(tl.y + 30f, maxY)
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
                        // 1. Draw source background image with true aspect ratio centered
                        drawImage(
                            image = currentBmp.asImageBitmap(),
                            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                            dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
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
