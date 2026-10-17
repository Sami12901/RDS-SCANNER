package com.example.rdsscanner.ui.signature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object SignatureManager {

    private fun getSignaturesDir(context: Context): File {
        return File(context.filesDir, "rds_signatures").apply { mkdirs() }
    }

    suspend fun getSavedSignatures(context: Context): List<File> = withContext(Dispatchers.IO) {
        val dir = getSignaturesDir(context)
        dir.listFiles { file -> file.extension.equals("png", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.toList() ?: emptyList()
    }

    suspend fun saveSignature(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val dir = getSignaturesDir(context)
        val file = File(dir, "sig_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }

    suspend fun deleteSignature(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete() else false
    }

    /**
     * Tightly crops transparent margins around drawn signature strokes.
     */
    fun cropToBoundingBox(src: Bitmap, padding: Int = 16): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var hasVisiblePixels = false

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val alpha = (pixels[rowOffset + x] shr 24) and 0xFF
                if (alpha > 20) {
                    hasVisiblePixels = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (!hasVisiblePixels) return src

        val cropLeft = max(0, minX - padding)
        val cropTop = max(0, minY - padding)
        val cropRight = min(width, maxX + padding)
        val cropBottom = min(height, maxY + padding)

        val cropWidth = max(1, cropRight - cropLeft)
        val cropHeight = max(1, cropBottom - cropTop)

        return Bitmap.createBitmap(src, cropLeft, cropTop, cropWidth, cropHeight)
    }

    /**
     * Recolor a signature bitmap to a specified ink color (Black, Blue, Emerald, Red)
     * while preserving transparent stroke anti-aliasing.
     */
    fun recolorSignature(source: Bitmap, targetColor: Int): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val targetR = Color.red(targetColor)
        val targetG = Color.green(targetColor)
        val targetB = Color.blue(targetColor)

        for (i in pixels.indices) {
            val alpha = (pixels[i] shr 24) and 0xFF
            if (alpha > 0) {
                pixels[i] = Color.argb(alpha, targetR, targetG, targetB)
            } else {
                pixels[i] = 0
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
