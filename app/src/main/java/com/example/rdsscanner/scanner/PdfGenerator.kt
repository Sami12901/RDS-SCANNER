package com.example.rdsscanner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.rdsscanner.model.PdfCompression
import com.example.rdsscanner.model.PdfPageSize
import com.example.rdsscanner.model.PdfSettings
import com.example.rdsscanner.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object PdfGenerator {

    private const val DEFAULT_MARGIN_PTS = 20f

    suspend fun generatePdf(
        context: Context,
        document: ScannedDocument,
        outputFile: File,
        customSettings: PdfSettings? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val settings = customSettings ?: document.pdfSettings
        val pdfDocument = PdfDocument()

        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

            val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(40, 120, 120, 120)
                textSize = 42f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }

            val pageNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }

            val totalPages = document.pages.size

            document.pages.forEachIndexed { index, page ->
                val pageFile = File(page.imagePath)
                if (pageFile.exists()) {
                    // Calculate safe sample size to avoid OOM
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(pageFile.absolutePath, boundsOptions)

                    var sampleSize = 1
                    val maxDimension = 2400
                    while (boundsOptions.outWidth / sampleSize > maxDimension || boundsOptions.outHeight / sampleSize > maxDimension) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    var bitmap = BitmapFactory.decodeFile(pageFile.absolutePath, decodeOptions)

                    if (bitmap != null) {
                        // Apply rotation if any
                        if (page.rotationDegrees % 360 != 0) {
                            bitmap = ImageProcessor.rotateBitmap(bitmap, page.rotationDegrees.toFloat())
                        }

                        // Apply compression quality if needed
                        if (settings.compression != PdfCompression.HIGH) {
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, settings.compression.quality, stream)
                            val compressedBytes = stream.toByteArray()
                            val reDecoded = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                            if (reDecoded != null) {
                                bitmap.recycle()
                                bitmap = reDecoded
                            }
                        }

                        // Determine target page width and height in points
                        val targetWidth: Int
                        val targetHeight: Int
                        if (settings.pageSize == PdfPageSize.ORIGINAL_FIT) {
                            // Scale image down to 72 DPI standard
                            val scaleFactor = 0.5f
                            targetWidth = (bitmap.width * scaleFactor).toInt().coerceAtLeast(300)
                            targetHeight = (bitmap.height * scaleFactor).toInt().coerceAtLeast(400)
                        } else {
                            targetWidth = settings.pageSize.widthPts
                            targetHeight = settings.pageSize.heightPts
                        }

                        val pageInfo = PdfDocument.PageInfo.Builder(targetWidth, targetHeight, index + 1).create()
                        val pdfPage = pdfDocument.startPage(pageInfo)
                        val canvas = pdfPage.canvas

                        // Clean white background
                        canvas.drawColor(Color.WHITE)

                        // Calculate aspect-fit destination rectangle
                        val availableWidth = targetWidth - (DEFAULT_MARGIN_PTS * 2)
                        val bottomReserved = if (settings.includePageNumbers) 30f else 0f
                        val availableHeight = targetHeight - (DEFAULT_MARGIN_PTS * 2) - bottomReserved

                        val scale = min(availableWidth / bitmap.width.toFloat(), availableHeight / bitmap.height.toFloat())
                        val destWidth = bitmap.width * scale
                        val destHeight = bitmap.height * scale

                        val left = DEFAULT_MARGIN_PTS + ((availableWidth - destWidth) / 2f)
                        val top = DEFAULT_MARGIN_PTS + ((availableHeight - destHeight) / 2f)

                        val destRect = RectF(left, top, left + destWidth, top + destHeight)
                        canvas.drawBitmap(bitmap, null, destRect, paint)

                        // Optional custom watermark
                        if (!settings.watermarkText.isNullOrBlank()) {
                            canvas.save()
                            canvas.rotate(-40f, targetWidth / 2f, targetHeight / 2f)
                            canvas.drawText(settings.watermarkText, targetWidth / 2f, targetHeight / 2f, watermarkPaint)
                            canvas.restore()
                        }

                        // Optional page numbers footer
                        if (settings.includePageNumbers) {
                            canvas.drawText(
                                "Page ${index + 1} of $totalPages",
                                targetWidth / 2f,
                                targetHeight - 12f,
                                pageNumberPaint
                            )
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
