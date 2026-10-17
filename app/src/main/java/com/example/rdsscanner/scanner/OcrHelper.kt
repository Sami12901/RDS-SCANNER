package com.example.rdsscanner.scanner

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrHelper {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromFile(file: File): Result<String> = suspendCancellableCoroutine { continuation ->
        if (!file.exists()) {
            continuation.resume(Result.failure(IllegalArgumentException("File not found: ${file.path}")))
            return@suspendCancellableCoroutine
        }

        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                continuation.resume(Result.failure(IllegalStateException("Failed to decode image file")))
                return@suspendCancellableCoroutine
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(Result.success(visionText.text))
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun recognizeTextFromUri(context: Context, uri: Uri): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(Result.success(visionText.text))
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }
}
