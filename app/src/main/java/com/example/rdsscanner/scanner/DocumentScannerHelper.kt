package com.example.rdsscanner.scanner

import android.app.Activity
import android.content.Context
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

object DocumentScannerHelper {

    fun createScanner(
        activity: Activity,
        pageLimit: Int = 50,
        enableGalleryImport: Boolean = true
    ): GmsDocumentScanner {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(enableGalleryImport)
            .setPageLimit(pageLimit)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        return GmsDocumentScanning.getClient(options)
    }

    fun startScan(
        scanner: GmsDocumentScanner,
        activity: Activity,
        onSuccess: (IntentSenderRequest) -> Unit,
        onError: (Exception) -> Unit
    ) {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                onSuccess(request)
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }
}
