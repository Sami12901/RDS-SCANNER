package com.example.rdsscanner.model

import java.util.UUID

enum class DocCategory(val displayName: String) {
    ALL("All Docs"),
    GENERAL("General"),
    ID_CARD("ID Card"),
    BILL_RECEIPT("Bills & Receipts"),
    NOTE("Notes"),
    PASSPORT("Passport")
}

data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val imagePath: String,
    val rotationDegrees: Int = 0,
    val ocrText: String? = null
)

data class ScannedDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: DocCategory = DocCategory.GENERAL,
    val pages: List<ScanPage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val pageCount: Int
        get() = pages.size

    val thumbnailPath: String?
        get() = pages.firstOrNull()?.imagePath
}
