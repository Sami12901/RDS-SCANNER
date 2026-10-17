package com.example.rdsscanner.pdf.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * High-performance, hardware-accelerated Native PDF Page Renderer with LRU Bitmap Caching.
 * Thread-safe wrapper around Android's PdfRenderer.
 */
class PdfPageRenderer(
    private val pfd: ParcelFileDescriptor,
    private val tempFileToDelete: File? = null
) : AutoCloseable {

    private val renderer: PdfRenderer = PdfRenderer(pfd)
    private val mutex = Mutex()

    // 64MB LRU cache for rendered page bitmaps
    private val cacheSize = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(32 * 1024)
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    val pageCount: Int
        get() = renderer.pageCount

    /**
     * Get page original dimensions in points (72 DPI)
     */
    suspend fun getPageDimensions(pageIndex: Int): Pair<Int, Int> = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext Pair(595, 842)
            var width = 595
            var height = 842
            try {
                val page = renderer.openPage(pageIndex)
                width = page.width
                height = page.height
                page.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Pair(width, height)
        }
    }

    /**
     * Render page at requested width and height or scaled high-res
     */
    suspend fun renderPage(
        pageIndex: Int,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        renderMode: Int = PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
    ): Bitmap? = withContext(Dispatchers.Default) {
        val cacheKey = "page_${pageIndex}_${targetWidth}x${targetHeight}"
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        mutex.withLock {
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
            try {
                val page = renderer.openPage(pageIndex)
                val origW = page.width
                val origH = page.height

                val w: Int
                val h: Int
                if (targetWidth > 0 && targetHeight > 0) {
                    w = targetWidth
                    h = targetHeight
                } else if (targetWidth > 0) {
                    w = targetWidth
                    h = (origH * (targetWidth.toFloat() / origW)).toInt()
                } else {
                    // Default 2x supersampling for ultra crisp display
                    w = origW * 2
                    h = origH * 2
                }

                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // Fill background with white before rendering
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, renderMode)
                page.close()

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }

    override fun close() {
        try {
            clearCache()
            renderer.close()
            pfd.close()
            tempFileToDelete?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        /**
         * Open a PDF file directly
         */
        fun fromFile(file: File): PdfPageRenderer {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return PdfPageRenderer(pfd)
        }

        /**
         * Open a PDF from any Android content:// or file:// Uri using Storage Access Framework
         */
        fun fromUri(context: Context, uri: Uri): PdfPageRenderer {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: throw IllegalArgumentException("Invalid file URI"))
                return fromFile(file)
            }

            // For content:// URIs, copy to a private temp cache file so ParcelFileDescriptor is seekable
            val tempFile = File.createTempFile("pdf_view_", ".pdf", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            return PdfPageRenderer(pfd, tempFileToDelete = tempFile)
        }
    }
}
