package com.example.rdsscanner.ui.pdftools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.rdsscanner.pdf.tools.PdfToolsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Adobe Acrobat PDF Tools Suite Screen:
 * Merge, Split, Compress, Watermark, Images to PDF, PDF to Images, OCR Text Extraction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onOpenPdfReader: (Uri, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }

    // Dialog state for Watermark
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var pendingWatermarkUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkOpacity by remember { mutableFloatStateOf(0.3f) }

    // OCR Result Dialog
    var ocrResultText by remember { mutableStateOf<String?>(null) }

    // 1. Merge PDFs Launcher
    val mergePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.size >= 2) {
            isProcessing = true
            progressStatus = "Merging ${uris.size} PDFs..."
            scope.launch {
                val outFile = File(context.cacheDir, "Merged_${System.currentTimeMillis()}.pdf")
                val res = PdfToolsManager.mergePdfs(context, uris, outFile)
                isProcessing = false
                if (res.isSuccess) {
                    Toast.makeText(context, "Successfully merged into ${outFile.name}", Toast.LENGTH_SHORT).show()
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                    onOpenPdfReader(contentUri, outFile.name)
                } else {
                    Toast.makeText(context, "Merge failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else if (uris.size == 1) {
            Toast.makeText(context, "Please select at least 2 PDFs to merge", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Split PDF Launcher
    val splitPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            progressStatus = "Splitting PDF into pages..."
            scope.launch {
                val outDir = File(context.cacheDir, "split_${System.currentTimeMillis()}")
                val res = PdfToolsManager.splitPdf(context, uri, outDir)
                isProcessing = false
                if (res.isSuccess) {
                    val count = res.getOrNull()?.size ?: 0
                    Toast.makeText(context, "Split completed: $count pages created", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Split failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 3. Compress PDF Launcher
    val compressPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            progressStatus = "Compressing PDF pages..."
            scope.launch {
                val outFile = File(context.cacheDir, "Compressed_${System.currentTimeMillis()}.pdf")
                val res = PdfToolsManager.compressPdf(context, uri, outFile, quality = 60)
                isProcessing = false
                if (res.isSuccess) {
                    val sizeKb = outFile.length() / 1024
                    Toast.makeText(context, "PDF Compressed! New size: ${sizeKb} KB", Toast.LENGTH_LONG).show()
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                    onOpenPdfReader(contentUri, outFile.name)
                } else {
                    Toast.makeText(context, "Compression failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 4. Watermark PDF Launcher
    val watermarkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingWatermarkUri = uri
            showWatermarkDialog = true
        }
    }

    // 5. Images to PDF Launcher
    val imagesToPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            progressStatus = "Converting ${uris.size} images to PDF..."
            scope.launch {
                val outFile = File(context.cacheDir, "Images_${System.currentTimeMillis()}.pdf")
                val res = PdfToolsManager.imagesToPdf(context, uris, outFile)
                isProcessing = false
                if (res.isSuccess) {
                    Toast.makeText(context, "PDF Created from ${uris.size} images!", Toast.LENGTH_SHORT).show()
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                    onOpenPdfReader(contentUri, outFile.name)
                } else {
                    Toast.makeText(context, "Conversion failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 6. PDF to Images Launcher
    val pdfToImagesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            progressStatus = "Extracting pages as JPEG images..."
            scope.launch {
                val outDir = File(context.cacheDir, "images_${System.currentTimeMillis()}")
                val res = PdfToolsManager.pdfToImages(context, uri, outDir)
                isProcessing = false
                if (res.isSuccess) {
                    val count = res.getOrNull()?.size ?: 0
                    Toast.makeText(context, "Extracted $count high-res images", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Extraction failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 7. OCR Text Extraction Launcher
    val ocrPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            progressStatus = "Recognizing text with MLKit..."
            scope.launch {
                val res = PdfToolsManager.extractText(context, uri) { cur, tot ->
                    progressStatus = "Recognizing page $cur of $tot..."
                }
                isProcessing = false
                if (res.isSuccess) {
                    val text = res.getOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        ocrResultText = text
                    } else {
                        Toast.makeText(context, "No text found in PDF", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "OCR failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Adobe Acrobat PDF Tools", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Privacy-first • 100% Offline", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Acrobat Power Suite",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Professional PDF utilities processed locally on your phone",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Tool 1: Merge PDFs
                item {
                    PdfToolCard(
                        title = "Merge PDFs",
                        description = "Combine multiple PDF files into one continuous document",
                        icon = Icons.Default.CallMerge,
                        iconTint = Color(0xFF1E88E5),
                        onClick = { mergePicker.launch("application/pdf") }
                    )
                }

                // Tool 2: Split PDF
                item {
                    PdfToolCard(
                        title = "Split PDF",
                        description = "Extract pages or separate a document into individual PDF pages",
                        icon = Icons.Default.CallSplit,
                        iconTint = Color(0xFFFB8C00),
                        onClick = { splitPicker.launch("application/pdf") }
                    )
                }

                // Tool 3: Compress PDF
                item {
                    PdfToolCard(
                        title = "Compress PDF",
                        description = "Reduce document file size with high visual quality for easy sharing",
                        icon = Icons.Default.Compress,
                        iconTint = Color(0xFF43A047),
                        onClick = { compressPicker.launch("application/pdf") }
                    )
                }

                // Tool 4: Add Watermark
                item {
                    PdfToolCard(
                        title = "Add Watermark",
                        description = "Apply custom text watermarks diagonally across all pages",
                        icon = Icons.Default.WaterDrop,
                        iconTint = Color(0xFF00ACC1),
                        onClick = { watermarkPicker.launch("application/pdf") }
                    )
                }

                // Tool 5: Images to PDF
                item {
                    PdfToolCard(
                        title = "Images to PDF",
                        description = "Convert gallery photos and images into a single multi-page PDF",
                        icon = Icons.Default.Image,
                        iconTint = Color(0xFF8E24AA),
                        onClick = { imagesToPdfPicker.launch("image/*") }
                    )
                }

                // Tool 6: PDF to Images
                item {
                    PdfToolCard(
                        title = "PDF to Images",
                        description = "Export high-resolution JPEG images of every page in the PDF",
                        icon = Icons.Default.Description,
                        iconTint = Color(0xFFE53935),
                        onClick = { pdfToImagesPicker.launch("application/pdf") }
                    )
                }

                // Tool 7: Extract Text (OCR)
                item {
                    PdfToolCard(
                        title = "Extract Text (OCR)",
                        description = "Optical character recognition across all pages with copy & share",
                        icon = Icons.Default.TextFields,
                        iconTint = Color(0xFF00897B),
                        onClick = { ocrPicker.launch("application/pdf") }
                    )
                }

                item {
                    Spacer(Modifier.height(80.dp))
                }
            }

            // In-progress Processing Overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(progressStatus, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }

    // Watermark Configuration Dialog
    if (showWatermarkDialog && pendingWatermarkUri != null) {
        val uri = pendingWatermarkUri!!
        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text("Watermark Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Opacity: ${(watermarkOpacity * 100).toInt()}%", fontSize = 13.sp)
                    Slider(
                        value = watermarkOpacity,
                        onValueChange = { watermarkOpacity = it },
                        valueRange = 0.1f..0.8f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWatermarkDialog = false
                        isProcessing = true
                        progressStatus = "Applying watermark..."
                        scope.launch {
                            val outFile = File(context.cacheDir, "Watermarked_${System.currentTimeMillis()}.pdf")
                            val res = PdfToolsManager.watermarkPdf(
                                context = context,
                                pdfUri = uri,
                                watermarkText = watermarkText.ifBlank { "CONFIDENTIAL" },
                                outputFile = outFile,
                                opacity = watermarkOpacity,
                                colorArgb = AndroidColor.RED
                            )
                            isProcessing = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Watermark applied!", Toast.LENGTH_SHORT).show()
                                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                                onOpenPdfReader(contentUri, outFile.name)
                            } else {
                                Toast.makeText(context, "Watermark failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Apply & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatermarkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // OCR Result Dialog
    val ocrText = ocrResultText
    if (ocrText != null) {
        AlertDialog(
            onDismissRequest = { ocrResultText = null },
            title = { Text("Extracted PDF Text", fontWeight = FontWeight.Bold) },
            text = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(12.dp)
                    ) {
                        item {
                            Text(text = ocrText, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", ocrText))
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, ocrText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Extracted Text"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { ocrResultText = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PdfToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
