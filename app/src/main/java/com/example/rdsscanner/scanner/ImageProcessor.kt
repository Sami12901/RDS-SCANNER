package com.example.rdsscanner.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

enum class ScanFilter {
    ORIGINAL,
    MAGIC_COLOR,
    GRAYSCALE,
    BW_THRESHOLD
}

object ImageProcessor {

    suspend fun applyFilter(srcBitmap: Bitmap, filter: ScanFilter): Bitmap = withContext(Dispatchers.Default) {
        when (filter) {
            ScanFilter.ORIGINAL -> srcBitmap
            ScanFilter.GRAYSCALE -> toGrayscale(srcBitmap)
            ScanFilter.MAGIC_COLOR -> toMagicColor(srcBitmap)
            ScanFilter.BW_THRESHOLD -> toBlackAndWhite(srcBitmap)
        }
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmpGrayscale)
        val paint = Paint()
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmpGrayscale
    }

    /**
     * CamScanner signature "Magic Color" filter:
     * Brightens paper background, strengthens dark ink, and increases contrast & clarity.
     */
    private fun toMagicColor(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Contrast boost + slight brightness lift
        val contrast = 1.35f
        val brightness = 20f

        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    /**
     * High contrast Black & White binarization for clean documents and receipts.
     */
    private fun toBlackAndWhite(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val threshold = 145
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = if (luminance > threshold) Color.WHITE else Color.BLACK
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Stitches Front and Back of an ID Card or Passport onto a single A4 page.
     */
    suspend fun stitchIdCard(front: Bitmap, back: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        // Standard A4 aspect ratio at 1240 x 1754 px
        val a4Width = 1240
        val a4Height = 1754
        val result = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.LTGRAY
            strokeWidth = 3f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }

        // Draw Header
        canvas.drawText("RDS SCANNER — ID CARD / PASSPORT SCAN", a4Width / 2f, 80f, textPaint)

        // Slot 1: Front
        val cardWidth = 900
        val cardHeight = 560
        val left = (a4Width - cardWidth) / 2f
        val topFront = 140f

        val frontRect = RectF(left, topFront, left + cardWidth, topFront + cardHeight)
        canvas.drawRoundRect(frontRect, 16f, 16f, borderPaint)
        canvas.drawBitmap(front, null, frontRect, paint)
        canvas.drawText("[ FRONT SIDE ]", a4Width / 2f, topFront + cardHeight + 40f, textPaint)

        // Slot 2: Back
        val topBack = topFront + cardHeight + 120f
        val backRect = RectF(left, topBack, left + cardWidth, topBack + cardHeight)
        canvas.drawRoundRect(backRect, 16f, 16f, borderPaint)
        canvas.drawBitmap(back, null, backRect, paint)
        canvas.drawText("[ BACK SIDE ]", a4Width / 2f, topBack + cardHeight + 40f, textPaint)

        // Footer Branding
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Scanned with RDS SCANNER • Open Source Document Scanner • Develop by Sami", a4Width / 2f, a4Height - 50f, footerPaint)

        result
    }

    suspend fun saveBitmapToFile(bitmap: Bitmap, targetFile: File, quality: Int = 90): Boolean = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadBitmapFromFile(file: File, sampleSize: Int = 1): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
