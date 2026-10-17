package com.example.rdsscanner.model

import java.util.UUID

enum class DocCategory(val displayName: String) {
    ALL("All Docs"),
    GENERAL("General"),
    ID_CARD("ID Card"),
    PASSPORT("Passport"),
    BILL_RECEIPT("Bills & Receipts"),
    NOTE("Notes"),
    BOOK("Book")
}

enum class ScanFilter(val displayName: String) {
    ORIGINAL("Original"),
    MAGIC_COLOR("Magic Color"),
    BW_DOCUMENT("B&W 1"),
    BW_TEXT("B&W 2"),
    GRAYSCALE("Grayscale"),
    LIGHTEN("Lighten")
}

enum class IdCardLayout(val displayName: String) {
    STACKED_A4("Front & Back (Stacked A4)"),
    SIDE_BY_SIDE_A4("Side-by-Side (A4)"),
    SINGLE_CENTERED("Single Card Centered")
}

enum class PdfPageSize(val displayName: String, val widthPts: Int, val heightPts: Int) {
    A4("A4 (Standard)", 595, 842),
    LETTER("US Letter", 612, 792),
    LEGAL("Legal", 612, 1008),
    ORIGINAL_FIT("Fit to Image", 0, 0)
}

enum class PdfCompression(val displayName: String, val quality: Int) {
    HIGH("High (Original Detail)", 95),
    MEDIUM("Medium (Recommended ~1MB)", 75),
    LOW("Low (Email / WhatsApp < 500KB)", 50)
}

data class PdfSettings(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val compression: PdfCompression = PdfCompression.MEDIUM,
    val includePageNumbers: Boolean = true,
    val watermarkText: String? = null
)

data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val imagePath: String,
    val originalPath: String = imagePath,
    val rotationDegrees: Int = 0,
    val filter: ScanFilter = ScanFilter.MAGIC_COLOR,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val ocrText: String? = null
)

data class ScannedDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: DocCategory = DocCategory.GENERAL,
    val pages: List<ScanPage> = emptyList(),
    val pdfSettings: PdfSettings = PdfSettings(),
    val isStarred: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val pageCount: Int
        get() = pages.size

    val thumbnailPath: String?
        get() = pages.firstOrNull()?.imagePath
}
