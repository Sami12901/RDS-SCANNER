package com.example.rdsscanner.ui.detail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.rdsscanner.data.DocumentRepository
import com.example.rdsscanner.model.PdfCompression
import com.example.rdsscanner.model.PdfPageSize
import com.example.rdsscanner.model.PdfSettings
import com.example.rdsscanner.model.ScanFilter
import com.example.rdsscanner.model.ScanPage
import com.example.rdsscanner.model.ScannedDocument
import com.example.rdsscanner.scanner.ImageProcessor
import com.example.rdsscanner.scanner.OcrHelper
import com.example.rdsscanner.scanner.PdfGenerator
import com.example.rdsscanner.theme.EmeraldPrimary
import com.example.rdsscanner.ui.ocr.OcrResultDialog
import com.example.rdsscanner.ui.signature.SignatureDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: String,
    repository: DocumentRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var doc by remember { mutableStateOf(repository.getDocumentById(documentId)) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf(doc?.title ?: "") }

    var showAdjustSheet by remember { mutableStateOf(false) }
    var showPdfSettingsSheet by remember { mutableStateOf(false) }

    var showOcrDialog by remember { mutableStateOf(false) }
    var ocrResultText by remember { mutableStateOf("") }
    var isOcrRunning by remember { mutableStateOf(false) }

    var showSignatureDialog by remember { mutableStateOf(false) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var isFilterApplying by remember { mutableStateOf(false) }

    // Adjust sliders
    var brightnessVal by remember { mutableFloatStateOf(0f) }
    var contrastVal by remember { mutableFloatStateOf(1f) }

    // PDF custom settings
    var pdfPageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var pdfCompression by remember { mutableStateOf(PdfCompression.MEDIUM) }
    var pdfPageNumbering by remember { mutableStateOf(true) }
    var pdfWatermarkText by remember { mutableStateOf("") }

    fun refreshDoc() {
        doc = repository.getDocumentById(documentId)
        if (doc == null || doc?.pages?.isEmpty() == true) {
            onBack()
        } else if (selectedPageIndex >= (doc?.pages?.size ?: 0)) {
            selectedPageIndex = 0
        }
    }

    if (doc == null) {
        onBack()
        return
    }

    val currentDocument = doc!!
    val currentPage = currentDocument.pages.getOrNull(selectedPageIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            newTitle = currentDocument.title
                            showRenameDialog = true
                        }
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentDocument.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(14.dp), tint = EmeraldPrimary)
                            }
                            Text(
                                text = "${currentDocument.pageCount} pages • Developed by Sami",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // PDF Settings Button
                    IconButton(onClick = { showPdfSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "PDF Settings", tint = EmeraldPrimary)
                    }

                    // Share PDF
                    IconButton(
                        onClick = {
                            isGeneratingPdf = true
                            coroutineScope.launch {
                                val outputFile = repository.getOutputPdfFile(currentDocument)
                                val result = PdfGenerator.generatePdf(context, currentDocument, outputFile)
                                isGeneratingPdf = false

                                result.onSuccess { pdfFile ->
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        type = "application/pdf"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Scanned PDF"))
                                }.onFailure {
                                    Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF", tint = EmeraldPrimary)
                    }

                    // Delete Doc
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.deleteDocument(currentDocument.id)
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Doc", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Filters Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ScanFilter.values()) { filter ->
                            val isSelected = currentPage?.filter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (currentPage != null && !isFilterApplying) {
                                        isFilterApplying = true
                                        coroutineScope.launch {
                                            val pageFile = File(currentPage.imagePath)
                                            val originalFile = File(currentPage.originalPath)
                                            val fileToProcess = if (originalFile.exists()) originalFile else pageFile
                                            val baseBmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(fileToProcess.absolutePath) }

                                            if (baseBmp != null) {
                                                val filtered = ImageProcessor.applyFilter(baseBmp, filter)
                                                ImageProcessor.saveBitmapToFile(filtered, pageFile, quality = 95)

                                                val updatedPages = currentDocument.pages.map { p ->
                                                    if (p.id == currentPage.id) p.copy(filter = filter) else p
                                                }
                                                repository.saveDocument(currentDocument.copy(pages = updatedPages))
                                                refreshDoc()
                                            }
                                            isFilterApplying = false
                                        }
                                    }
                                },
                                label = { Text(filter.displayName, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = EmeraldPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Toolbar actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Adjust Sliders
                        ActionItem(
                            icon = Icons.Default.Tune,
                            label = "Adjust",
                            onClick = { showAdjustSheet = true }
                        )

                        // Rotate
                        ActionItem(
                            icon = Icons.Default.RotateRight,
                            label = "Rotate",
                            onClick = {
                                if (currentPage != null) {
                                    coroutineScope.launch {
                                        repository.rotatePage(currentDocument.id, currentPage.id)
                                        refreshDoc()
                                    }
                                }
                            }
                        )

                        // OCR
                        ActionItem(
                            icon = Icons.Default.TextFields,
                            label = "OCR (Text)",
                            onClick = {
                                if (currentPage != null) {
                                    isOcrRunning = true
                                    coroutineScope.launch {
                                        val result = OcrHelper.recognizeTextFromFile(File(currentPage.imagePath))
                                        isOcrRunning = false
                                        result.onSuccess { text ->
                                            ocrResultText = text
                                            showOcrDialog = true
                                            repository.updatePageOcrText(currentDocument.id, currentPage.id, text)
                                        }.onFailure {
                                            Toast.makeText(context, "OCR failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )

                        // Sign
                        ActionItem(
                            icon = Icons.Default.Draw,
                            label = "Signature",
                            onClick = { showSignatureDialog = true }
                        )

                        // Reorder Page Left
                        ActionItem(
                            icon = Icons.Default.ArrowBack,
                            label = "Move Left",
                            onClick = {
                                if (selectedPageIndex > 0) {
                                    val pages = currentDocument.pages.toMutableList()
                                    val item = pages.removeAt(selectedPageIndex)
                                    pages.add(selectedPageIndex - 1, item)
                                    coroutineScope.launch {
                                        repository.saveDocument(currentDocument.copy(pages = pages))
                                        selectedPageIndex--
                                        refreshDoc()
                                    }
                                }
                            }
                        )

                        // Reorder Page Right
                        ActionItem(
                            icon = Icons.Default.ArrowForward,
                            label = "Move Right",
                            onClick = {
                                if (selectedPageIndex < currentDocument.pages.size - 1) {
                                    val pages = currentDocument.pages.toMutableList()
                                    val item = pages.removeAt(selectedPageIndex)
                                    pages.add(selectedPageIndex + 1, item)
                                    coroutineScope.launch {
                                        repository.saveDocument(currentDocument.copy(pages = pages))
                                        selectedPageIndex++
                                        refreshDoc()
                                    }
                                }
                            }
                        )

                        // Delete Page
                        ActionItem(
                            icon = Icons.Default.Delete,
                            label = "Delete",
                            isDestructive = true,
                            onClick = {
                                if (currentPage != null) {
                                    coroutineScope.launch {
                                        repository.deletePage(currentDocument.id, currentPage.id)
                                        refreshDoc()
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Big PDF Export Button
                    Button(
                        onClick = {
                            isGeneratingPdf = true
                            coroutineScope.launch {
                                val outputFile = repository.getOutputPdfFile(currentDocument)
                                val customSettings = PdfSettings(
                                    pageSize = pdfPageSize,
                                    compression = pdfCompression,
                                    includePageNumbers = pdfPageNumbering,
                                    watermarkText = if (pdfWatermarkText.isNotBlank()) pdfWatermarkText else null
                                )
                                val result = PdfGenerator.generatePdf(context, currentDocument, outputFile, customSettings)
                                isGeneratingPdf = false

                                result.onSuccess { pdfFile ->
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        type = "application/pdf"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Open / Share PDF"))
                                }.onFailure {
                                    Toast.makeText(context, "PDF compilation failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isGeneratingPdf) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compiling PDF...", color = Color.White)
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Clean PDF (No Watermark)", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Page Viewer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentPage != null) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = File(currentPage.imagePath),
                                contentDescription = "Page ${selectedPageIndex + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .rotate(currentPage.rotationDegrees.toFloat()),
                                contentScale = ContentScale.Fit
                            )

                            // Page Counter Pill
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = "Page ${selectedPageIndex + 1} of ${currentDocument.pageCount}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Page Thumbnails Strip
            if (currentDocument.pageCount > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(currentDocument.pages) { idx, page ->
                        val isSelected = idx == selectedPageIndex
                        Box(
                            modifier = Modifier
                                .size(60.dp, 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) EmeraldPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedPageIndex = idx }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = File(page.imagePath),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .rotate(page.rotationDegrees.toFloat()),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            coroutineScope.launch {
                                repository.saveDocument(currentDocument.copy(title = newTitle.trim()))
                                refreshDoc()
                                showRenameDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Adjust Bottom Sheet (Brightness & Contrast Sliders)
    if (showAdjustSheet && currentPage != null) {
        ModalBottomSheet(
            onDismissRequest = { showAdjustSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Fine-Tune Image Adjustment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Brightness Slider
                Text(text = "Brightness: ${brightnessVal.toInt()}", fontSize = 13.sp)
                Slider(
                    value = brightnessVal,
                    onValueChange = { brightnessVal = it },
                    valueRange = -80f..80f,
                    colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Contrast Slider
                Text(text = "Contrast: ${String.format("%.2f", contrastVal)}x", fontSize = 13.sp)
                Slider(
                    value = contrastVal,
                    onValueChange = { contrastVal = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            brightnessVal = 0f
                            contrastVal = 1f
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val pageFile = File(currentPage.imagePath)
                                val originalFile = File(currentPage.originalPath)
                                val fileToProcess = if (originalFile.exists()) originalFile else pageFile
                                val baseBmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(fileToProcess.absolutePath) }

                                if (baseBmp != null) {
                                    val adjusted = ImageProcessor.adjustBrightnessContrast(baseBmp, brightnessVal, contrastVal)
                                    ImageProcessor.saveBitmapToFile(adjusted, pageFile, quality = 95)
                                    refreshDoc()
                                    Toast.makeText(context, "Adjustment applied!", Toast.LENGTH_SHORT).show()
                                }
                                showAdjustSheet = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text("Apply", color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // PDF Export Settings Sheet
    if (showPdfSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showPdfSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "PDF Export Configuration",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Page Size Selector
                Text(text = "Page Size", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PdfPageSize.values()) { size ->
                        FilterChip(
                            selected = size == pdfPageSize,
                            onClick = { pdfPageSize = size },
                            label = { Text(size.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Compression Quality
                Text(text = "PDF File Quality", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PdfCompression.values()) { comp ->
                        FilterChip(
                            selected = comp == pdfCompression,
                            onClick = { pdfCompression = comp },
                            label = { Text(comp.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Page Numbering Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Include Page Numbers (Page X of Y)", fontSize = 13.sp)
                    Switch(
                        checked = pdfPageNumbering,
                        onCheckedChange = { pdfPageNumbering = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Watermark
                OutlinedTextField(
                    value = pdfWatermarkText,
                    onValueChange = { pdfWatermarkText = it },
                    label = { Text("Watermark (Leave blank for clean PDF)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showPdfSettingsSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Save Settings", color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // OCR Result Dialog
    if (showOcrDialog) {
        OcrResultDialog(
            extractedText = ocrResultText,
            onDismiss = { showOcrDialog = false }
        )
    }

    // Signature Dialog
    if (showSignatureDialog && currentPage != null) {
        SignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureCaptured = { signatureBmp ->
                coroutineScope.launch {
                    val pageFile = File(currentPage.imagePath)
                    val baseBmp = BitmapFactory.decodeFile(pageFile.absolutePath)
                    if (baseBmp != null) {
                        val merged = Bitmap.createBitmap(baseBmp.width, baseBmp.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(merged)
                        canvas.drawBitmap(baseBmp, 0f, 0f, null)

                        val sigWidth = (baseBmp.width * 0.35f).toInt()
                        val sigHeight = (sigWidth * (signatureBmp.height.toFloat() / signatureBmp.width)).toInt()
                        val scaledSig = Bitmap.createScaledBitmap(signatureBmp, sigWidth, sigHeight, true)

                        val left = baseBmp.width - sigWidth - (baseBmp.width * 0.05f)
                        val top = baseBmp.height - sigHeight - (baseBmp.height * 0.05f)
                        canvas.drawBitmap(scaledSig, left, top, null)

                        ImageProcessor.saveBitmapToFile(merged, pageFile, quality = 95)
                        refreshDoc()
                        Toast.makeText(context, "Signature stamped on page!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else EmeraldPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
