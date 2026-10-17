package com.example.rdsscanner.pdf.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Specification for exporting/baking an individual page, including rotation and ordering.
 */
data class PageConfig(
    val originalPageIndex: Int,
    val rotationDegrees: Int = 0 // 0, 90, 180, 270
)

/**
 * Lossless PDF Annotation Baking and Page Modification Engine.
 * Burns vector strokes, text, highlights, stamps, and shapes directly into PDF pages
 * using Android's native PdfDocument.
 */
object PdfBakingEngine {

    suspend fun bakeAnnotations(
        renderer: PdfPageRenderer,
        annotationsByPage: Map<Int, List<PdfAnnotation>>,
        pageConfigs: List<PageConfig>? = null,
        outputFile: File,
        dpiScale: Float = 2.0f // 2x supersampling for high fidelity
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(outputFile).use { outStream ->
                bakeAnnotations(renderer, annotationsByPage, pageConfigs, outStream, dpiScale)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun bakeAnnotations(
        renderer: PdfPageRenderer,
        annotationsByPage: Map<Int, List<PdfAnnotation>>,
        pageConfigs: List<PageConfig>? = null,
        outputStream: OutputStream,
        dpiScale: Float = 2.0f
    ) = withContext(Dispatchers.Default) {
        val pdfDocument = PdfDocument()

        val pagesToProcess = pageConfigs ?: (0 until renderer.pageCount).map {
            PageConfig(originalPageIndex = it, rotationDegrees = 0)
        }

        pagesToProcess.forEachIndexed { exportIndex, pageCfg ->
            val origIndex = pageCfg.originalPageIndex
            if (origIndex in 0 until renderer.pageCount) {
                val (baseW, baseH) = renderer.getPageDimensions(origIndex)

                // Swap width and height if rotated 90 or 270
                val isRotated90or270 = pageCfg.rotationDegrees % 180 != 0
                val targetPageW = if (isRotated90or270) baseH else baseW
                val targetPageH = if (isRotated90or270) baseW else baseH

                val pageInfo = PdfDocument.PageInfo.Builder(targetPageW, targetPageH, exportIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Render original page bitmap at scaled DPI
                val renderW = (baseW * dpiScale).toInt()
                val renderH = (baseH * dpiScale).toInt()
                val pageBitmap = renderer.renderPage(origIndex, renderW, renderH)

                if (pageBitmap != null) {
                    canvas.save()

                    // Apply rotation around center if needed
                    if (pageCfg.rotationDegrees != 0) {
                        canvas.translate(targetPageW / 2f, targetPageH / 2f)
                        canvas.rotate(pageCfg.rotationDegrees.toFloat())
                        canvas.translate(-baseW / 2f, -baseH / 2f)
                    }

                    // Draw base bitmap scaled down to PDF point bounds
                    val baseRect = RectF(0f, 0f, baseW.toFloat(), baseH.toFloat())
                    canvas.drawBitmap(pageBitmap, null, baseRect, null)

                    // Draw all annotations for this page
                    val annotations = annotationsByPage[origIndex] ?: emptyList()
                    drawAnnotations(canvas, annotations, baseW.toFloat(), baseH.toFloat())

                    canvas.restore()
                }

                pdfDocument.finishPage(page)
            }
        }

        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    private fun drawAnnotations(
        canvas: Canvas,
        annotations: List<PdfAnnotation>,
        pageWidth: Float,
        pageHeight: Float
    ) {
        for (annotation in annotations) {
            when (annotation) {
                is PdfAnnotation.InkStroke -> {
                    if (annotation.points.size > 1) {
                        val paint = Paint().apply {
                            isAntiAlias = true
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            color = annotation.colorArgb
                            strokeWidth = annotation.strokeWidth
                            alpha = (annotation.alpha * 255).toInt().coerceIn(0, 255)
                        }
                        val path = Path()
                        path.moveTo(annotation.points[0].x, annotation.points[0].y)
                        for (i in 1 until annotation.points.size) {
                            path.lineTo(annotation.points[i].x, annotation.points[i].y)
                        }
                        canvas.drawPath(path, paint)
                    }
                }

                is PdfAnnotation.Highlight -> {
                    if (annotation.points.size > 1) {
                        val paint = Paint().apply {
                            isAntiAlias = true
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.SQUARE
                            strokeJoin = Paint.Join.ROUND
                            color = annotation.colorArgb
                            strokeWidth = annotation.strokeWidth
                            alpha = (annotation.alpha * 255).toInt().coerceIn(0, 255)
                        }
                        val path = Path()
                        path.moveTo(annotation.points[0].x, annotation.points[0].y)
                        for (i in 1 until annotation.points.size) {
                            path.lineTo(annotation.points[i].x, annotation.points[i].y)
                        }
                        canvas.drawPath(path, paint)
                    }
                }

                is PdfAnnotation.TextNote -> {
                    val x = annotation.xRatio * pageWidth
                    val y = annotation.yRatio * pageHeight

                    val paint = Paint().apply {
                        isAntiAlias = true
                        color = annotation.textColorArgb
                        textSize = annotation.fontSizeSp * (pageWidth / 400f).coerceIn(1f, 2.5f)
                        typeface = if (annotation.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    }

                    // Optional background card
                    if (annotation.backgroundColorArgb != null) {
                        val textWidth = paint.measureText(annotation.text)
                        val textHeight = paint.fontSpacing
                        val bgPaint = Paint().apply {
                            color = annotation.backgroundColorArgb
                            style = Paint.Style.FILL
                        }
                        val padding = 8f
                        canvas.drawRoundRect(
                            x - padding,
                            y - textHeight + padding,
                            x + textWidth + padding,
                            y + padding,
                            6f,
                            6f,
                            bgPaint
                        )
                    }

                    canvas.drawText(annotation.text, x, y, paint)
                }

                is PdfAnnotation.CommentPin -> {
                    val x = annotation.xRatio * pageWidth
                    val y = annotation.yRatio * pageHeight
                    drawCommentPin(canvas, x, y, annotation.commentText)
                }

                is PdfAnnotation.ShapeNote -> {
                    val paint = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        color = annotation.colorArgb
                        strokeWidth = annotation.strokeWidth
                    }
                    when (annotation.shapeType) {
                        ShapeType.RECTANGLE -> {
                            val left = minOf(annotation.start.x, annotation.end.x)
                            val top = minOf(annotation.start.y, annotation.end.y)
                            val right = maxOf(annotation.start.x, annotation.end.x)
                            val bottom = maxOf(annotation.start.y, annotation.end.y)
                            canvas.drawRect(left, top, right, bottom, paint)
                        }
                        ShapeType.CIRCLE -> {
                            val left = minOf(annotation.start.x, annotation.end.x)
                            val top = minOf(annotation.start.y, annotation.end.y)
                            val right = maxOf(annotation.start.x, annotation.end.x)
                            val bottom = maxOf(annotation.start.y, annotation.end.y)
                            canvas.drawOval(RectF(left, top, right, bottom), paint)
                        }
                        ShapeType.LINE -> {
                            canvas.drawLine(annotation.start.x, annotation.start.y, annotation.end.x, annotation.end.y, paint)
                        }
                        ShapeType.ARROW -> {
                            drawArrow(canvas, annotation.start.x, annotation.start.y, annotation.end.x, annotation.end.y, paint)
                        }
                    }
                }

                is PdfAnnotation.SignatureStamp -> {
                    val left = annotation.xRatio * pageWidth
                    val top = annotation.yRatio * pageHeight
                    val width = annotation.widthRatio * pageWidth
                    val height = annotation.heightRatio * pageHeight
                    val destRect = RectF(left, top, left + width, top + height)
                    canvas.drawBitmap(annotation.bitmap, null, destRect, null)
                }
            }
        }
    }

    private fun drawCommentPin(canvas: Canvas, x: Float, y: Float, text: String) {
        val pinRadius = 14f
        val circlePaint = Paint().apply {
            isAntiAlias = true
            color = 0xFFFFC107.toInt() // Amber sticky note color
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = 0xFFFFA000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawCircle(x, y, pinRadius, circlePaint)
        canvas.drawCircle(x, y, pinRadius, borderPaint)

        // Draw comment icon lines inside pin
        val linePaint = Paint().apply {
            isAntiAlias = true
            color = 0xFF3E2723.toInt()
            strokeWidth = 2f
        }
        canvas.drawLine(x - 6f, y - 3f, x + 6f, y - 3f, linePaint)
        canvas.drawLine(x - 6f, y + 1f, x + 4f, y + 1f, linePaint)
        canvas.drawLine(x - 6f, y + 5f, x + 2f, y + 5f, linePaint)
    }

    private fun drawArrow(canvas: Canvas, fromX: Float, fromY: Float, toX: Float, toY: Float, paint: Paint) {
        canvas.drawLine(fromX, fromY, toX, toY, paint)
        val angle = atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())
        val arrowHeadLength = 20.0
        val arrowAngle = Math.PI / 6 // 30 degrees

        val x1 = toX - arrowHeadLength * cos(angle - arrowAngle)
        val y1 = toY - arrowHeadLength * sin(angle - arrowAngle)
        val x2 = toX - arrowHeadLength * cos(angle + arrowAngle)
        val y2 = toY - arrowHeadLength * sin(angle + arrowAngle)

        val headPath = Path().apply {
            moveTo(toX, toY)
            lineTo(x1.toFloat(), y1.toFloat())
            moveTo(toX, toY)
            lineTo(x2.toFloat(), y2.toFloat())
        }
        canvas.drawPath(headPath, paint)
    }
}
