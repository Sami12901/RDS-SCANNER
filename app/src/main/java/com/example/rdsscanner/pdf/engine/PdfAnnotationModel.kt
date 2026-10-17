package com.example.rdsscanner.pdf.engine

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.UUID

/**
 * Shape types supported by the Adobe Acrobat Shape tool
 */
enum class ShapeType {
    RECTANGLE,
    CIRCLE,
    ARROW,
    LINE
}

/**
 * Sealed class representing all vector and bitmap annotations that can be placed on a PDF page
 */
sealed class PdfAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int
) {
    /**
     * Freehand ink drawing (Pen tool)
     */
    data class InkStroke(
        val targetPageIndex: Int,
        val points: List<Offset>,
        val colorArgb: Int = Color(0xFF1E88E5).toArgb(), // Default blue pen
        val strokeWidth: Float = 6f,
        val alpha: Float = 1.0f
    ) : PdfAnnotation(pageIndex = targetPageIndex)

    /**
     * Text highlight annotation (Highlighter tool)
     */
    data class Highlight(
        val targetPageIndex: Int,
        val points: List<Offset>,
        val colorArgb: Int = Color(0xFFFFEB3B).toArgb(), // Default yellow
        val strokeWidth: Float = 24f,
        val alpha: Float = 0.45f
    ) : PdfAnnotation(pageIndex = targetPageIndex)

    /**
     * Text inserted onto the PDF (Type on PDF tool)
     */
    data class TextNote(
        val targetPageIndex: Int,
        val xRatio: Float, // Relative X (0.0 to 1.0)
        val yRatio: Float, // Relative Y (0.0 to 1.0)
        val text: String,
        val textColorArgb: Int = Color(0xFF212121).toArgb(),
        val fontSizeSp: Float = 16f,
        val backgroundColorArgb: Int? = null,
        val isBold: Boolean = false
    ) : PdfAnnotation(pageIndex = targetPageIndex)

    /**
     * Sticky note / comment pin
     */
    data class CommentPin(
        val targetPageIndex: Int,
        val xRatio: Float,
        val yRatio: Float,
        val commentText: String,
        val author: String = "User",
        val timestamp: Long = System.currentTimeMillis()
    ) : PdfAnnotation(pageIndex = targetPageIndex)

    /**
     * Geometric shape annotation (Shape tool)
     */
    data class ShapeNote(
        val targetPageIndex: Int,
        val shapeType: ShapeType,
        val start: Offset,
        val end: Offset,
        val colorArgb: Int = Color(0xFFE53935).toArgb(),
        val strokeWidth: Float = 6f
    ) : PdfAnnotation(pageIndex = targetPageIndex)

    /**
     * Digital Signature stamp
     */
    data class SignatureStamp(
        val targetPageIndex: Int,
        val xRatio: Float,
        val yRatio: Float,
        val widthRatio: Float,
        val heightRatio: Float,
        val bitmap: Bitmap
    ) : PdfAnnotation(pageIndex = targetPageIndex)
}

/**
 * Active editing tool in Adobe Acrobat Reader
 */
enum class AcrobatTool {
    VIEW,           // Pan & Zoom
    HIGHLIGHT,      // Semi-transparent highlight
    PEN,            // Freehand writing
    TEXT,           // Type on PDF
    COMMENT,        // Sticky note
    SHAPE,          // Rectangles, Arrows
    SIGNATURE,      // Place signature
    ERASER          // Remove annotations
}
