package com.example.rdsscanner.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.example.rdsscanner.model.IdCardLayout
import com.example.rdsscanner.model.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {

    suspend fun applyFilter(srcBitmap: Bitmap, filter: ScanFilter): Bitmap = withContext(Dispatchers.Default) {
        when (filter) {
            ScanFilter.ORIGINAL -> srcBitmap
            ScanFilter.MAGIC_COLOR -> toMagicColor(srcBitmap)
            ScanFilter.BW_DOCUMENT -> toBlackAndWhite(srcBitmap, threshold = 145)
            ScanFilter.BW_TEXT -> toBlackAndWhite(srcBitmap, threshold = 165)
            ScanFilter.GRAYSCALE -> toGrayscale(srcBitmap)
            ScanFilter.LIGHTEN -> toLighten(srcBitmap)
        }
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    /**
     * CamScanner signature Magic Color filter:
     * Removes yellowish shadows, pushes background to crisp white, and intensifies text ink.
     */
    private fun toMagicColor(src: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val contrast = 1.45f
        val brightness = 28f

        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    private fun toLighten(src: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cm = ColorMatrix(
            floatArrayOf(
                1.1f, 0f, 0f, 0f, 50f,
                0f, 1.1f, 0f, 0f, 50f,
                0f, 0f, 1.1f, 0f, 50f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    private fun toBlackAndWhite(src: Bitmap, threshold: Int): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

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

    /**
     * Real-time Brightness & Contrast fine-tuning adjustments.
     */
    suspend fun adjustBrightnessContrast(
        src: Bitmap,
        brightness: Float, // -100 to +100
        contrast: Float    // 0.5 to 2.0
    ): Bitmap = withContext(Dispatchers.Default) {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        output
    }

    /**
     * 2D 4-point Perspective Transform (dewarping/flattening angled document).
     */
    suspend fun warpPerspective(
        src: Bitmap,
        tl: PointF,
        tr: PointF,
        br: PointF,
        bl: PointF
    ): Bitmap = withContext(Dispatchers.Default) {
        // Calculate destination width & height based on edge lengths
        val widthTop = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val widthBottom = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val destWidth = max(widthTop, widthBottom).toInt().coerceAtLeast(100)

        val heightLeft = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val heightRight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
        val destHeight = max(heightLeft, heightRight).toInt().coerceAtLeast(100)

        val srcPoints = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            destWidth.toFloat(), 0f,
            destWidth.toFloat(), destHeight.toFloat(),
            0f, destHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val output = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawBitmap(src, matrix, paint)
        output
    }

    fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Advanced ID Card Stitcher supporting Stacked A4, Side-by-Side, Centered, and Anti-Fraud KYC Watermarks.
     */
    suspend fun stitchIdCard(
        front: Bitmap,
        back: Bitmap?,
        layout: IdCardLayout = IdCardLayout.STACKED_A4,
        watermarkText: String? = null,
        filter: ScanFilter = ScanFilter.MAGIC_COLOR
    ): Bitmap = withContext(Dispatchers.Default) {
        val processedFront = applyFilter(front, filter)
        val processedBack = if (back != null) applyFilter(back, filter) else null

        // Standard A4 dimensions at 150 DPI
        val a4Width = 1240
        val a4Height = 1754
        val result = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#CCCCCC")
            strokeWidth = 3f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#444444")
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        // Header Title
        canvas.drawText("RDS SCANNER — ID CARD / PASSPORT KYC COPY", a4Width / 2f, 80f, textPaint)

        // ISO/IEC 7810 ID-1 standard card ratio (85.6mm x 53.98mm = 1.586)
        val cardWidth = 860f
        val cardHeight = 542f

        when (layout) {
            IdCardLayout.STACKED_A4 -> {
                // Front on Top
                val left = (a4Width - cardWidth) / 2f
                val topFront = 140f
                val frontRect = RectF(left, topFront, left + cardWidth, topFront + cardHeight)
                canvas.drawRoundRect(frontRect, 16f, 16f, borderPaint)
                canvas.drawBitmap(processedFront, null, frontRect, imagePaint)
                canvas.drawText("[ FRONT SIDE ]", a4Width / 2f, topFront + cardHeight + 40f, textPaint)

                // Back on Bottom
                if (processedBack != null) {
                    val topBack = topFront + cardHeight + 130f
                    val backRect = RectF(left, topBack, left + cardWidth, topBack + cardHeight)
                    canvas.drawRoundRect(backRect, 16f, 16f, borderPaint)
                    canvas.drawBitmap(processedBack, null, backRect, imagePaint)
                    canvas.drawText("[ BACK SIDE ]", a4Width / 2f, topBack + cardHeight + 40f, textPaint)
                }
            }
            IdCardLayout.SIDE_BY_SIDE_A4 -> {
                val sideCardW = 560f
                val sideCardH = 353f
                val top = 400f
                val frontLeft = 50f
                val frontRect = RectF(frontLeft, top, frontLeft + sideCardW, top + sideCardH)
                canvas.drawRoundRect(frontRect, 12f, 12f, borderPaint)
                canvas.drawBitmap(processedFront, null, frontRect, imagePaint)
                canvas.drawText("[ FRONT ]", frontLeft + sideCardW / 2f, top + sideCardH + 36f, textPaint)

                if (processedBack != null) {
                    val backLeft = frontLeft + sideCardW + 40f
                    val backRect = RectF(backLeft, top, backLeft + sideCardW, top + sideCardH)
                    canvas.drawRoundRect(backRect, 12f, 12f, borderPaint)
                    canvas.drawBitmap(processedBack, null, backRect, imagePaint)
                    canvas.drawText("[ BACK ]", backLeft + sideCardW / 2f, top + sideCardH + 36f, textPaint)
                }
            }
            IdCardLayout.SINGLE_CENTERED -> {
                val left = (a4Width - cardWidth) / 2f
                val top = (a4Height - cardHeight) / 2f
                val rect = RectF(left, top, left + cardWidth, top + cardHeight)
                canvas.drawRoundRect(rect, 16f, 16f, borderPaint)
                canvas.drawBitmap(processedFront, null, rect, imagePaint)
                canvas.drawText("[ IDENTITY DOCUMENT ]", a4Width / 2f, top + cardHeight + 40f, textPaint)
            }
        }

        // Anti-Fraud KYC Watermark across cards
        if (!watermarkText.isNullOrBlank()) {
            drawKycWatermark(canvas, a4Width, a4Height, watermarkText)
        }

        // Footer Branding
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Scanned with RDS SCANNER • Open Source Document Scanner • Develop by Sami",
            a4Width / 2f,
            a4Height - 50f,
            footerPaint
        )

        result
    }

    /**
     * Passport Spread Mode: captures passport page (photo zone + MRZ lines) onto A4.
     */
    suspend fun stitchPassport(
        spread: Bitmap,
        watermarkText: String? = null,
        filter: ScanFilter = ScanFilter.MAGIC_COLOR
    ): Bitmap = withContext(Dispatchers.Default) {
        val processed = applyFilter(spread, filter)
        val a4Width = 1240
        val a4Height = 1754
        val result = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.LTGRAY
            strokeWidth = 3f
        }
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        canvas.drawText("RDS SCANNER — PASSPORT IDENTIFICATION SPREAD", a4Width / 2f, 80f, textPaint)

        // Passport spread dimensions (approx 125mm x 88mm = ~1000 x 704 px)
        val pWidth = 1040f
        val pHeight = 730f
        val left = (a4Width - pWidth) / 2f
        val top = 160f

        val rect = RectF(left, top, left + pWidth, top + pHeight)
        canvas.drawRoundRect(rect, 16f, 16f, borderPaint)
        canvas.drawBitmap(processed, null, rect, imagePaint)
        canvas.drawText("[ PASSPORT PHOTO & MRZ BIO PAGE ]", a4Width / 2f, top + pHeight + 44f, textPaint)

        if (!watermarkText.isNullOrBlank()) {
            drawKycWatermark(canvas, a4Width, a4Height, watermarkText)
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Scanned with RDS SCANNER • Open Source Document Scanner • Develop by Sami",
            a4Width / 2f,
            a4Height - 50f,
            footerPaint
        )

        result
    }

    private fun drawKycWatermark(canvas: Canvas, width: Int, height: Int, text: String) {
        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 220, 20, 60) // Semi-transparent crimson red
            textSize = 38f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        canvas.save()
        canvas.rotate(-35f, width / 2f, height / 2f)

        // Draw multiple diagonal bands across the sheet
        val spacing = 220f
        for (y in -height until height * 2 step spacing.toInt()) {
            canvas.drawText(text, width / 2f, y.toFloat(), wmPaint)
        }

        canvas.restore()
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
