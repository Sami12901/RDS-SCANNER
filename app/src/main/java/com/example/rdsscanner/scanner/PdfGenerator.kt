package com.example.rdsscanner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.rdsscanner.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object PdfGenerator {

    // Standard A4 dimensions in PostScript points (72 points per inch)
    private const val A4_WIDTH_PTS = 595
    private const val A4_HEIGHT_PTS = 842
    private const val MARGIN_PTS = 24f

    suspend fun generatePdf(
        context: Context,
        document: ScannedDocument,
        outputFile: File,
        customWatermark: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            }

            val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(45, 128, 128, 128)
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }

            document.pages.forEachIndexed { index, page ->
                val pageFile = File(page.imagePath)
                if (pageFile.exists()) {
                    // Safe decode with sample size
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(pageFile.absolutePath, boundsOptions)

                    var sampleSize = 1
                    val maxDimension = 2000
                    while (boundsOptions.outWidth / sampleSize > maxDimension || boundsOptions.outHeight / sampleSize > maxDimension) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    var bitmap = BitmapFactory.decodeFile(pageFile.absolutePath, decodeOptions)

                    if (bitmap != null) {
                        if (page.rotationDegrees % 360 != 0) {
                            bitmap = ImageProcessor.rotateBitmap(bitmap, page.rotationDegrees.toFloat())
                        }

                        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PTS, A4_HEIGHT_PTS, index + 1).create()
                        val pdfPage = pdfDocument.startPage(pageInfo)
                        val canvas = pdfPage.canvas

                        // White background
                        canvas.drawColor(Color.WHITE)

                        // Calculate aspect ratio fit inside margins
                        val availableWidth = A4_WIDTH_PTS - (MARGIN_PTS * 2)
                        val availableHeight = A4_HEIGHT_PTS - (MARGIN_PTS * 2)

                        val scale = min(availableWidth / bitmap.width.toFloat(), availableHeight / bitmap.height.toFloat())
                        val destWidth = bitmap.width * scale
                        val destHeight = bitmap.height * scale

                        val left = MARGIN_PTS + ((availableWidth - destWidth) / 2f)
                        val top = MARGIN_PTS + ((availableHeight - destHeight) / 2f)

                        val destRect = RectF(left, top, left + destWidth, top + destHeight)
                        canvas.drawBitmap(bitmap, null, destRect, paint)

                        // Draw optional watermark if requested by user
                        if (!customWatermark.isNullOrBlank()) {
                            canvas.save()
                            canvas.rotate(-45f, A4_WIDTH_PTS / 2f, A4_HEIGHT_PTS / 2f)
                            canvas.drawText(customWatermark, A4_WIDTH_PTS / 2f, A4_HEIGHT_PTS / 2f, watermarkPaint)
                            canvas.restore()
                        }

                        pdfDocument.finishPage(pdfPage)
                        bitmap.recycle()
                    }
                }
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            pdfDocument.close()
        }
    }
}
