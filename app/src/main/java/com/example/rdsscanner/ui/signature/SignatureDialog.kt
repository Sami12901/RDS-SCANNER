package com.example.rdsscanner.ui.signature

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rdsscanner.theme.EmeraldPrimary
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

data class DrawPoint(val offset: Offset, val isStart: Boolean)

@Composable
fun SignatureDialog(
    onDismiss: () -> Unit,
    onSignatureCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Draw, 1 = Saved Signatures
    val points = remember { mutableStateListOf<DrawPoint>() }
    var selectedColor by remember { mutableStateOf(Color(0xFF111827)) } // Default Ink Black
    var saveToLibrary by remember { mutableStateOf(true) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var savedFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    fun refreshSaved() {
        coroutineScope.launch {
            savedFiles = SignatureManager.getSavedSignatures(context)
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            refreshSaved()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Add Digital Signature",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = EmeraldPrimary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Draw New", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Saved Signatures", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        },
        text = {
            if (selectedTab == 0) {
                // Drawing Canvas Tab
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Sign with finger or stylus inside the box:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.5.dp, Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
                            .onGloballyPositioned { coordinates ->
                                canvasSize = coordinates.size
                            }
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            points.add(DrawPoint(offset, isStart = true))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            points.add(DrawPoint(change.position, isStart = false))
                                        }
                                    )
                                }
                        ) {
                            for (i in 0 until points.size - 1) {
                                val p1 = points[i]
                                val p2 = points[i + 1]
                                if (!p2.isStart) {
                                    drawLine(
                                        color = selectedColor,
                                        start = p1.offset,
                                        end = p2.offset,
                                        strokeWidth = 6.5f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        // Subtle Sign Guide
                        Text(
                            text = "Sign Here ✍️",
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Color Palettes & Clear Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Color(0xFF111827), // Black
                                Color(0xFF0D47A1), // Document Blue
                                EmeraldPrimary,    // Emerald Green
                                Color(0xFFB71C1C)  // Red
                            ).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColor == color) 3.dp else 1.dp,
                                            color = if (selectedColor == color) Color.DarkGray else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = color }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { points.clear() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Save to Library Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveToLibrary = !saveToLibrary }
                    ) {
                        Checkbox(
                            checked = saveToLibrary,
                            onCheckedChange = { saveToLibrary = it },
                            colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save signature for future reuse",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // Saved Signatures Tab
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (savedFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No saved signatures yet.\nDraw a signature to save it here!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = "Tap a signature to place on your document:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(savedFiles, key = { it.absolutePath }) { file ->
                                val bmp = remember(file.absolutePath) {
                                    BitmapFactory.decodeFile(file.absolutePath)
                                }
                                if (bmp != null) {
                                    Card(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(110.dp)
                                            .clickable {
                                                onSignatureCaptured(bmp)
                                                onDismiss()
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Saved Signature",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(12.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        SignatureManager.deleteSignature(file)
                                                        refreshSaved()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.LightGray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        if (points.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0) {
                            val rawBitmap = createSmoothSignatureBitmap(
                                points = points,
                                color = selectedColor,
                                width = canvasSize.width,
                                height = canvasSize.height
                            )
                            val cropped = SignatureManager.cropToBoundingBox(rawBitmap, padding = 16)

                            if (saveToLibrary) {
                                coroutineScope.launch {
                                    SignatureManager.saveSignature(context, cropped)
                                }
                            }

                            onSignatureCaptured(cropped)
                            onDismiss()
                        }
                    },
                    enabled = points.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(imageVector = Icons.Default.Done, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Place on Document", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Creates smooth anti-aliased bitmap at the exact measured canvas dimensions (zero warping).
 */
private fun createSmoothSignatureBitmap(
    points: List<DrawPoint>,
    color: Color,
    width: Int,
    height: Int
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        strokeWidth = 7.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val path = android.graphics.Path()
    var isDrawing = false

    for (i in points.indices) {
        val p = points[i]
        if (p.isStart || !isDrawing) {
            path.moveTo(p.offset.x, p.offset.y)
            isDrawing = true
        } else {
            val prev = points[i - 1]
            // Midpoint quadratic Bezier for smooth natural handwriting
            val midX = (prev.offset.x + p.offset.x) / 2f
            val midY = (prev.offset.y + p.offset.y) / 2f
            path.quadTo(prev.offset.x, prev.offset.y, midX, midY)
        }
    }

    canvas.drawPath(path, paint)
    return bitmap
}
