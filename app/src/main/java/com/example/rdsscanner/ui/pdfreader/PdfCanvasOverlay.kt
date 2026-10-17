package com.example.rdsscanner.ui.pdfreader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rdsscanner.pdf.engine.AcrobatTool
import com.example.rdsscanner.pdf.engine.PdfAnnotation
import com.example.rdsscanner.pdf.engine.ShapeType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive Vector Canvas Overlay for Adobe Acrobat PDF Reader & Editor.
 * Real-time hardware-accelerated drawing, highlighters, text typing, comment pins, and shapes.
 */
@Composable
fun PdfCanvasOverlay(
    pageIndex: Int,
    activeTool: AcrobatTool,
    annotations: List<PdfAnnotation>,
    onAddAnnotation: (PdfAnnotation) -> Unit,
    onDeleteAnnotation: (PdfAnnotation) -> Unit,
    penColor: Color,
    penStrokeWidth: Float,
    highlighterColor: Color,
    highlighterWidth: Float,
    shapeType: ShapeType,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // In-progress gestures
    var livePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember { mutableStateOf<Offset?>(null) }

    // Dialogs for Text Note & Comment Pin
    var pendingTextOffset by remember { mutableStateOf<Offset?>(null) }
    var pendingCommentOffset by remember { mutableStateOf<Offset?>(null) }
    var activeCommentToView by remember { mutableStateOf<PdfAnnotation.CommentPin?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .then(
                if (activeTool == AcrobatTool.VIEW) {
                    Modifier
                } else if (activeTool == AcrobatTool.PEN || activeTool == AcrobatTool.HIGHLIGHT) {
                    Modifier.pointerInput(pageIndex, activeTool, penColor, highlighterColor) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                livePoints = listOf(startOffset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                livePoints = livePoints + change.position
                            },
                            onDragEnd = {
                                if (livePoints.size > 1) {
                                    if (activeTool == AcrobatTool.PEN) {
                                        onAddAnnotation(
                                            PdfAnnotation.InkStroke(
                                                targetPageIndex = pageIndex,
                                                points = livePoints,
                                                colorArgb = penColor.toArgb(),
                                                strokeWidth = penStrokeWidth
                                            )
                                        )
                                    } else {
                                        onAddAnnotation(
                                            PdfAnnotation.Highlight(
                                                targetPageIndex = pageIndex,
                                                points = livePoints,
                                                colorArgb = highlighterColor.toArgb(),
                                                strokeWidth = highlighterWidth,
                                                alpha = 0.45f
                                            )
                                        )
                                    }
                                }
                                livePoints = emptyList()
                            },
                            onDragCancel = {
                                livePoints = emptyList()
                            }
                        )
                    }
                } else if (activeTool == AcrobatTool.SHAPE) {
                    Modifier.pointerInput(pageIndex, shapeType, penColor) {
                        detectDragGestures(
                            onDragStart = { start ->
                                shapeStart = start
                                shapeEnd = start
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                shapeEnd = change.position
                            },
                            onDragEnd = {
                                val s = shapeStart
                                val e = shapeEnd
                                if (s != null && e != null && (s - e).getDistance() > 10f) {
                                    onAddAnnotation(
                                        PdfAnnotation.ShapeNote(
                                            targetPageIndex = pageIndex,
                                            shapeType = shapeType,
                                            start = s,
                                            end = e,
                                            colorArgb = penColor.toArgb(),
                                            strokeWidth = penStrokeWidth
                                        )
                                    )
                                }
                                shapeStart = null
                                shapeEnd = null
                            },
                            onDragCancel = {
                                shapeStart = null
                                shapeEnd = null
                            }
                        )
                    }
                } else if (activeTool == AcrobatTool.TEXT) {
                    Modifier.pointerInput(pageIndex) {
                        detectTapGestures { tapOffset ->
                            pendingTextOffset = tapOffset
                        }
                    }
                } else if (activeTool == AcrobatTool.COMMENT) {
                    Modifier.pointerInput(pageIndex) {
                        detectTapGestures { tapOffset ->
                            pendingCommentOffset = tapOffset
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Compose Vector Rendering Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pageAnnotations = annotations.filter { it.pageIndex == pageIndex }

            // 1. Draw Highlights (Layer 1)
            for (ann in pageAnnotations) {
                if (ann is PdfAnnotation.Highlight && ann.points.size > 1) {
                    val path = Path().apply {
                        moveTo(ann.points[0].x, ann.points[0].y)
                        for (i in 1 until ann.points.size) {
                            lineTo(ann.points[i].x, ann.points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(ann.colorArgb).copy(alpha = ann.alpha),
                        style = Stroke(
                            width = ann.strokeWidth,
                            cap = StrokeCap.Square,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // 2. Draw In-progress highlight
            if (activeTool == AcrobatTool.HIGHLIGHT && livePoints.size > 1) {
                val path = Path().apply {
                    moveTo(livePoints[0].x, livePoints[0].y)
                    for (i in 1 until livePoints.size) {
                        lineTo(livePoints[i].x, livePoints[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = highlighterColor.copy(alpha = 0.45f),
                    style = Stroke(
                        width = highlighterWidth,
                        cap = StrokeCap.Square,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Draw Ink Strokes (Pen)
            for (ann in pageAnnotations) {
                if (ann is PdfAnnotation.InkStroke && ann.points.size > 1) {
                    val path = Path().apply {
                        moveTo(ann.points[0].x, ann.points[0].y)
                        for (i in 1 until ann.points.size) {
                            lineTo(ann.points[i].x, ann.points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(ann.colorArgb),
                        style = Stroke(
                            width = ann.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // 4. Draw In-progress Pen Stroke
            if (activeTool == AcrobatTool.PEN && livePoints.size > 1) {
                val path = Path().apply {
                    moveTo(livePoints[0].x, livePoints[0].y)
                    for (i in 1 until livePoints.size) {
                        lineTo(livePoints[i].x, livePoints[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = penColor,
                    style = Stroke(
                        width = penStrokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 5. Draw Shapes
            for (ann in pageAnnotations) {
                if (ann is PdfAnnotation.ShapeNote) {
                    drawCustomShape(
                        shapeType = ann.shapeType,
                        start = ann.start,
                        end = ann.end,
                        color = Color(ann.colorArgb),
                        strokeWidth = ann.strokeWidth
                    )
                }
            }

            // 6. Draw In-progress Shape
            val s = shapeStart
            val e = shapeEnd
            if (activeTool == AcrobatTool.SHAPE && s != null && e != null) {
                drawCustomShape(
                    shapeType = shapeType,
                    start = s,
                    end = e,
                    color = penColor,
                    strokeWidth = penStrokeWidth
                )
            }
        }

        // 7. Interactive Text Notes Overlay
        val pageAnnotations = annotations.filter { it.pageIndex == pageIndex }
        for (ann in pageAnnotations) {
            if (ann is PdfAnnotation.TextNote && canvasSize.width > 0 && canvasSize.height > 0) {
                val x = (ann.xRatio * canvasSize.width).toInt()
                val y = (ann.yRatio * canvasSize.height).toInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(x, y) }
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (ann.backgroundColorArgb != null) Color(ann.backgroundColorArgb)
                            else Color.White.copy(alpha = 0.85f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clickable {
                            if (activeTool == AcrobatTool.ERASER) {
                                onDeleteAnnotation(ann)
                            }
                        }
                ) {
                    Text(
                        text = ann.text,
                        color = Color(ann.textColorArgb),
                        fontSize = ann.fontSizeSp.sp,
                        fontWeight = if (ann.isBold) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // 8. Interactive Sticky Note Pins Overlay
        for (ann in pageAnnotations) {
            if (ann is PdfAnnotation.CommentPin && canvasSize.width > 0 && canvasSize.height > 0) {
                val x = (ann.xRatio * canvasSize.width - 16).toInt()
                val y = (ann.yRatio * canvasSize.height - 16).toInt()

                Surface(
                    modifier = Modifier
                        .offset { IntOffset(x, y) }
                        .size(32.dp)
                        .clickable {
                            if (activeTool == AcrobatTool.ERASER) {
                                onDeleteAnnotation(ann)
                            } else {
                                activeCommentToView = ann
                            }
                        },
                    shape = CircleShape,
                    color = Color(0xFFFFC107),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = "Sticky Note",
                            tint = Color(0xFF3E2723),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Dialog: Insert Text Note (Type on PDF)
        val textOffset = pendingTextOffset
        if (textOffset != null && canvasSize.width > 0 && canvasSize.height > 0) {
            var inputText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { pendingTextOffset = null },
                title = { Text("Add Text to PDF", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Type here...") },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onAddAnnotation(
                                    PdfAnnotation.TextNote(
                                        targetPageIndex = pageIndex,
                                        xRatio = textOffset.x / canvasSize.width,
                                        yRatio = textOffset.y / canvasSize.height,
                                        text = inputText.trim(),
                                        textColorArgb = penColor.toArgb(),
                                        fontSizeSp = 16f,
                                        backgroundColorArgb = Color.White.toArgb(),
                                        isBold = false
                                    )
                                )
                            }
                            pendingTextOffset = null
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingTextOffset = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog: Insert Comment Pin (Sticky Note)
        val commentOffset = pendingCommentOffset
        if (commentOffset != null && canvasSize.width > 0 && canvasSize.height > 0) {
            var inputComment by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { pendingCommentOffset = null },
                title = { Text("Add Sticky Note", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = inputComment,
                        onValueChange = { inputComment = it },
                        label = { Text("Your note or comment...") },
                        modifier = Modifier.padding(top = 8.dp),
                        minLines = 3
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (inputComment.isNotBlank()) {
                                onAddAnnotation(
                                    PdfAnnotation.CommentPin(
                                        targetPageIndex = pageIndex,
                                        xRatio = commentOffset.x / canvasSize.width,
                                        yRatio = commentOffset.y / canvasSize.height,
                                        commentText = inputComment.trim()
                                    )
                                )
                            }
                            pendingCommentOffset = null
                        }
                    ) {
                        Text("Post")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCommentOffset = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog: View Existing Sticky Note
        val activeComment = activeCommentToView
        if (activeComment != null) {
            AlertDialog(
                onDismissRequest = { activeCommentToView = null },
                title = { Text("Sticky Note", fontWeight = FontWeight.Bold) },
                text = {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = activeComment.commentText,
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFF3E2723),
                            fontSize = 15.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeCommentToView = null }) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            onDeleteAnnotation(activeComment)
                            activeCommentToView = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Note")
                    }
                }
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCustomShape(
    shapeType: ShapeType,
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float
) {
    when (shapeType) {
        ShapeType.RECTANGLE -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val width = kotlin.math.abs(end.x - start.x)
            val height = kotlin.math.abs(end.y - start.y)
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = strokeWidth)
            )
        }
        ShapeType.CIRCLE -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val width = kotlin.math.abs(end.x - start.x)
            val height = kotlin.math.abs(end.y - start.y)
            drawOval(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = strokeWidth)
            )
        }
        ShapeType.LINE -> {
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        ShapeType.ARROW -> {
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
            val headLength = 22.0
            val arrowAngle = Math.PI / 6

            val x1 = end.x - headLength * cos(angle - arrowAngle)
            val y1 = end.y - headLength * sin(angle - arrowAngle)
            val x2 = end.x - headLength * cos(angle + arrowAngle)
            val y2 = end.y - headLength * sin(angle + arrowAngle)

            val headPath = Path().apply {
                moveTo(end.x, end.y)
                lineTo(x1.toFloat(), y1.toFloat())
                moveTo(end.x, end.y)
                lineTo(x2.toFloat(), y2.toFloat())
            }
            drawPath(
                path = headPath,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
