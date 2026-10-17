package com.example.rdsscanner.pdf.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.rdsscanner.pdf.engine.PdfPageRenderer
import com.example.rdsscanner.scanner.OcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Adobe Acrobat PDF Tools Engine: Merge, Split, Compress, Watermark, Convert, and OCR.
 * 100% On-Device, Offline, and Zero-Telemetry.
 */
object PdfToolsManager {

    /**
     * Merge multiple PDF files into a single unified document
     */
    suspend fun mergePdfs(
        context: Context,
        pdfUris: List<Uri>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (pdfUris.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No PDF files selected for merging"))
            }

            val document = PdfDocument()
            var globalPageIndex = 1

            for (uri in pdfUris) {
                PdfPageRenderer.fromUri(context, uri).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        val (baseW, baseH) = renderer.getPageDimensions(i)
                        val pageInfo = PdfDocument.PageInfo.Builder(baseW, baseH, globalPageIndex++).create()
                        val page = document.startPage(pageInfo)

                        val bmp = renderer.renderPage(i, baseW * 2, baseH * 2)
                        if (bmp != null) {
                            val rect = RectF(0f, 0f, baseW.toFloat(), baseH.toFloat())
                            page.canvas.drawBitmap(bmp, null, rect, null)
                        }

                        document.finishPage(page)
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Split a multi-page PDF into individual single-page PDF files
     */
    suspend fun splitPdf(
        context: Context,
        pdfUri: Uri,
        outputDir: File
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            val outputFiles = mutableListOf<File>()
            if (!outputDir.exists()) outputDir.mkdirs()

            PdfPageRenderer.fromUri(context, pdfUri).use { renderer ->
                val totalPages = renderer.pageCount
                val baseName = "split_page"

                for (i in 0 until totalPages) {
                    val pageDoc = PdfDocument()
                    val (baseW, baseH) = renderer.getPageDimensions(i)
                    val pageInfo = PdfDocument.PageInfo.Builder(baseW, baseH, 1).create()
                    val page = pageDoc.startPage(pageInfo)

                    val bmp = renderer.renderPage(i, baseW * 2, baseH * 2)
                    if (bmp != null) {
                        val rect = RectF(0f, 0f, baseW.toFloat(), baseH.toFloat())
                        page.canvas.drawBitmap(bmp, null, rect, null)
                    }
                    pageDoc.finishPage(page)

                    val outFile = File(outputDir, "${baseName}_${i + 1}.pdf")
                    FileOutputStream(outFile).use { out ->
                        pageDoc.writeTo(out)
                    }
                    pageDoc.close()
                    outputFiles.add(outFile)
                }
            }

            Result.success(outputFiles)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Compress a PDF by re-encoding pages with high-efficiency JPEG compression
     */
    suspend fun compressPdf(
        context: Context,
        pdfUri: Uri,
        outputFile: File,
        quality: Int = 65,
        scaleDpi: Float = 1.0f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()

            PdfPageRenderer.fromUri(context, pdfUri).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    val (baseW, baseH) = renderer.getPageDimensions(i)
                    val targetW = (baseW * scaleDpi).toInt()
                    val targetH = (baseH * scaleDpi).toInt()

                    val renderBmp = renderer.renderPage(i, targetW, targetH) ?: continue

                    // Compress to JPEG stream
                    val byteStream = ByteArrayOutputStream()
                    renderBmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), byteStream)
                    val compressedBytes = byteStream.toByteArray()
                    val compressedBmp = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                    val pageInfo = PdfDocument.PageInfo.Builder(baseW, baseH, i + 1).create()
                    val page = document.startPage(pageInfo)
                    val rect = RectF(0f, 0f, baseW.toFloat(), baseH.toFloat())
                    page.canvas.drawBitmap(compressedBmp, null, rect, null)
                    document.finishPage(page)

                    if (compressedBmp != renderBmp) {
                        compressedBmp.recycle()
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Apply a diagonal watermark text across every page of the PDF
     */
    suspend fun watermarkPdf(
        context: Context,
        pdfUri: Uri,
        watermarkText: String,
        outputFile: File,
        opacity: Float = 0.25f,
        colorArgb: Int = Color.RED
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()

            PdfPageRenderer.fromUri(context, pdfUri).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    val (baseW, baseH) = renderer.getPageDimensions(i)
                    val pageInfo = PdfDocument.PageInfo.Builder(baseW, baseH, i + 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    val bmp = renderer.renderPage(i, baseW * 2, baseH * 2)
                    if (bmp != null) {
                        val rect = RectF(0f, 0f, baseW.toFloat(), baseH.toFloat())
                        canvas.drawBitmap(bmp, null, rect, null)
                    }

                    // Watermark painting
                    val paint = Paint().apply {
                        isAntiAlias = true
                        color = colorArgb
                        alpha = (opacity.coerceIn(0.05f, 1.0f) * 255).toInt()
                        textSize = (baseW / 8f).coerceIn(24f, 64f)
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                    }

                    canvas.save()
                    canvas.translate(baseW / 2f, baseH / 2f)
                    canvas.rotate(-45f)
                    canvas.drawText(watermarkText, 0f, 0f, paint)
                    canvas.restore()

                    document.finishPage(page)
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Convert a list of images into a single multi-page PDF document
     */
    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (imageUris.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No images provided"))
            }

            val document = PdfDocument()

            imageUris.forEachIndexed { index, uri ->
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        // Standard page size or scaled to image aspect ratio (standard A4 = 595 x 842 points)
                        val pageW = 595
                        val pageH = 842
                        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                        val page = document.startPage(pageInfo)

                        // Fit image maintaining aspect ratio
                        val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val pageRatio = pageW.toFloat() / pageH.toFloat()

                        val drawW: Float
                        val drawH: Float
                        if (imgRatio > pageRatio) {
                            drawW = pageW.toFloat() - 40f
                            drawH = drawW / imgRatio
                        } else {
                            drawH = pageH.toFloat() - 40f
                            drawW = drawH * imgRatio
                        }

                        val left = (pageW - drawW) / 2f
                        val top = (pageH - drawH) / 2f
                        val destRect = RectF(left, top, left + drawW, top + drawH)

                        page.canvas.drawBitmap(bitmap, null, destRect, null)
                        document.finishPage(page)
                        bitmap.recycle()
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Export all pages of a PDF to high-resolution JPEG images
     */
    suspend fun pdfToImages(
        context: Context,
        pdfUri: Uri,
        outputDir: File,
        quality: Int = 90
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            val outputFiles = mutableListOf<File>()
            if (!outputDir.exists()) outputDir.mkdirs()

            PdfPageRenderer.fromUri(context, pdfUri).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    val (baseW, baseH) = renderer.getPageDimensions(i)
                    // High-res 2x
                    val bmp = renderer.renderPage(i, baseW * 2, baseH * 2) ?: continue
                    val outFile = File(outputDir, "page_${i + 1}.jpg")
                    FileOutputStream(outFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }
                    outputFiles.add(outFile)
                }
            }

            Result.success(outputFiles)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Perform OCR text extraction across all pages of a PDF document
     */
    suspend fun extractText(
        context: Context,
        pdfUri: Uri,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fullTextBuilder = StringBuilder()

            PdfPageRenderer.fromUri(context, pdfUri).use { renderer ->
                val total = renderer.pageCount
                for (i in 0 until total) {
                    onProgress?.invoke(i + 1, total)
                    val (baseW, baseH) = renderer.getPageDimensions(i)
                    val pageBmp = renderer.renderPage(i, baseW * 2, baseH * 2)
                    if (pageBmp != null) {
                        val ocrRes = OcrHelper.recognizeTextFromBitmap(pageBmp)
                        if (ocrRes.isSuccess) {
                            val text = ocrRes.getOrNull()?.trim().orEmpty()
                            if (text.isNotEmpty()) {
                                if (fullTextBuilder.isNotEmpty()) {
                                    fullTextBuilder.append("\n\n--- Page ${i + 1} ---\n\n")
                                } else {
                                    fullTextBuilder.append("--- Page ${i + 1} ---\n\n")
                                }
                                fullTextBuilder.append(text)
                            }
                        }
                    }
                }
            }

            Result.success(fullTextBuilder.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
