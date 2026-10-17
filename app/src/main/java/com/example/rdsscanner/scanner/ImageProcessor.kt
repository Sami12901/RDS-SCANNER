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
            ScanFilter.BW_DOCUMENT -> toBlackAndWhite(srcBitmap, offset = 10)
            ScanFilter.BW_TEXT -> toBlackAndWhite(srcBitmap, offset = 18)
            ScanFilter.GRAYSCALE -> toGrayscale(srcBitmap)
            ScanFilter.LIGHTEN -> toLighten(srcBitmap)
        }
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c shr 24) and 0xFF
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (299 * r + 587 * g + 114 * b) / 1000

            val contrast = 1.3f
            val brightened = ((lum - 128) * contrast + 128 + 24).toInt().coerceIn(0, 255)
            val finalL = if (brightened >= 242) 255 else brightened
            pixels[i] = (a shl 24) or (finalL shl 16) or (finalL shl 8) or finalL
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * CamScanner Magic Color 2.0:
     * - Automatic paper background detection & whitening (cleans gray/yellow paper cast)
     * - Deep ink text contrast enhancement
     * - Color vibrancy preservation for stamps, blue ink, colored headers without highlight blowout
     */
    private fun toMagicColor(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Build luminance histogram from a distributed sample
        val hist = IntArray(256)
        val sampleStep = max(1, (width * height) / 50000)
        var sampledCount = 0
        for (i in pixels.indices step sampleStep) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (299 * r + 587 * g + 114 * b) / 1000
            hist[lum]++
            sampledCount++
        }

        // Find black point (~5th percentile) and paper white point (~88th percentile)
        var count = 0
        var blackPoint = 30
        var whitePoint = 220
        val targetBlack = (sampledCount * 0.05).toInt()
        val targetWhite = (sampledCount * 0.88).toInt()

        for (i in 0..255) {
            count += hist[i]
            if (count >= targetBlack && blackPoint == 30) {
                blackPoint = i.coerceIn(10, 75)
            }
            if (count >= targetWhite) {
                whitePoint = i.coerceIn(160, 245)
                break
            }
        }
        val range = max(1, whitePoint - blackPoint).toFloat()

        // S-curve tone mapping LUT
        val lut = IntArray(256)
        for (i in 0..255) {
            val norm = ((i - blackPoint) / range).coerceIn(0f, 1f)
            val curved = if (norm > 0.72f) {
                0.72f + (norm - 0.72f) * (0.28f / 0.28f) * 1.15f
            } else if (norm < 0.25f) {
                norm * 0.85f
            } else {
                norm
            }
            lut[i] = (curved.coerceIn(0f, 1f) * 255f).toInt()
        }

        // 2. Process pixels with chroma-preserving ink vibrancy
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c shr 24) and 0xFF
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val delta = maxC - minC
            val lum = (299 * r + 587 * g + 114 * b) / 1000

            // If pixel is nearly neutral (paper background or black/gray text)
            if (delta < 18 || maxC < 40) {
                val newL = lut[lum]
                val finalL = if (newL >= 236) 255 else newL
                pixels[i] = (a shl 24) or (finalL shl 16) or (finalL shl 8) or finalL
            } else {
                // Colored pixel (stamps, signatures, colored text/logos)
                val scale = lut[lum].toFloat() / max(1, lum).toFloat()
                val newR = (r * scale).toInt().coerceIn(0, 255)
                val newG = (g * scale).toInt().coerceIn(0, 255)
                val newB = (b * scale).toInt().coerceIn(0, 255)

                Color.colorToHSV((a shl 24) or (newR shl 16) or (newG shl 8) or newB, hsv)
                hsv[1] = (hsv[1] * 1.25f).coerceIn(0f, 1f)
                pixels[i] = Color.HSVToColor(a, hsv)
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun toLighten(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c shr 24) and 0xFF
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val newR = (r + 38 * (1f - r / 255f)).toInt().coerceIn(0, 255)
            val newG = (g + 38 * (1f - g / 255f)).toInt().coerceIn(0, 255)
            val newB = (b + 38 * (1f - b / 255f)).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Adaptive Local Window Binarization:
     * Eliminates shadow gradients across documents (no dark blob corners)
     * and produces razor-sharp monochrome text for printing.
     */
    private fun toBlackAndWhite(src: Bitmap, offset: Int = 10): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val lum = IntArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lum[i] = (299 * r + 587 * g + 114 * b) / 1000
        }

        // Fast block-based local thresholding
        val blockSize = 32
        val blocksX = (width + blockSize - 1) / blockSize
        val blocksY = (height + blockSize - 1) / blockSize
        val blockMeans = IntArray(blocksX * blocksY)

        for (by in 0 until blocksY) {
            val startY = by * blockSize
            val endY = min(height, startY + blockSize)
            for (bx in 0 until blocksX) {
                val startX = bx * blockSize
                val endX = min(width, startX + blockSize)
                var sum = 0
                var count = 0
                for (y in startY until endY) {
                    val rowOffset = y * width
                    for (x in startX until endX) {
                        sum += lum[rowOffset + x]
                        count++
                    }
                }
                blockMeans[by * blocksX + bx] = if (count > 0) sum / count else 128
            }
        }

        for (y in 0 until height) {
            val by = (y / blockSize).coerceIn(0, blocksY - 1)
            val rowOffset = y * width
            for (x in 0 until width) {
                val bx = (x / blockSize).coerceIn(0, blocksX - 1)
                val localMean = blockMeans[by * blocksX + bx]
                val threshold = (localMean - offset).coerceIn(40, 230)
                pixels[rowOffset + x] = if (lum[rowOffset + x] > threshold) Color.WHITE else Color.BLACK
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Real-time Brightness & Contrast fine-tuning adjustments using centered contrast.
     */
    suspend fun adjustBrightnessContrast(
        src: Bitmap,
        brightness: Float, // -80 to +80
        contrast: Float    // 0.5 to 2.5
    ): Bitmap = withContext(Dispatchers.Default) {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness

        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
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

    suspend fun loadBitmapFromFile(file: File, sampleSize: Int = 1): Bitmap? = loadRotatedBitmapFromFile(file, sampleSize)

    suspend fun loadRotatedBitmapFromFile(file: File, sampleSize: Int = 1): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val original = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@withContext null
            val exif = try {
                android.media.ExifInterface(file.absolutePath)
            } catch (e: Exception) {
                null
            }
            val orientation = exif?.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            ) ?: android.media.ExifInterface.ORIENTATION_NORMAL

            val rotationAngle = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationAngle != 0f) {
                rotateBitmap(original, rotationAngle)
            } else {
                original
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
