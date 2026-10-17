package com.example.rdsscanner.ui.idcard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.rdsscanner.data.DocumentRepository
import com.example.rdsscanner.model.DocCategory
import com.example.rdsscanner.model.IdCardLayout
import com.example.rdsscanner.model.ScanFilter
import com.example.rdsscanner.model.ScanPage
import com.example.rdsscanner.model.ScannedDocument
import com.example.rdsscanner.scanner.ImageProcessor
import com.example.rdsscanner.scanner.PdfGenerator
import com.example.rdsscanner.theme.EmeraldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardWorkflowScreen(
    frontFile: File,
    backFile: File?,
    isPassportMode: Boolean = false,
    repository: DocumentRepository,
    onBack: () -> Unit,
    onFinished: (ScannedDocument) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedLayout by remember { mutableStateOf(IdCardLayout.STACKED_A4) }
    var selectedFilter by remember { mutableStateOf(ScanFilter.MAGIC_COLOR) }
    var enableKycWatermark by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("FOR KYC VERIFICATION ONLY") }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingPreview by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Regenerate preview whenever layout, filter, or watermark changes
    fun updatePreview() {
        isGeneratingPreview = true
        coroutineScope.launch {
            val frontBmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(frontFile.absolutePath) }
            val backBmp = backFile?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it.absolutePath) } }

            if (frontBmp != null) {
                val wm = if (enableKycWatermark && watermarkText.isNotBlank()) watermarkText else null
                val result = if (isPassportMode) {
                    ImageProcessor.stitchPassport(frontBmp, wm, selectedFilter)
                } else {
                    ImageProcessor.stitchIdCard(frontBmp, backBmp, selectedLayout, wm, selectedFilter)
                }
                previewBitmap = result
            }
            isGeneratingPreview = false
        }
    }

    LaunchedEffect(selectedLayout, selectedFilter, enableKycWatermark, watermarkText) {
        updatePreview()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isPassportMode) "Passport A4 Layout" else "ID Card A4 Layout",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (previewBitmap != null && !isSaving) {
                                isSaving = true
                                coroutineScope.launch {
                                    val docId = UUID.randomUUID().toString()
                                    val docFolder = File(context.filesDir, "rds_documents/$docId").apply { mkdirs() }
                                    val pageId = UUID.randomUUID().toString()
                                    val targetFile = File(docFolder, "id_card_$pageId.jpg")

                                    ImageProcessor.saveBitmapToFile(previewBitmap!!, targetFile, quality = 95)

                                    val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                    val title = if (isPassportMode) "Passport Scan $timeStamp" else "ID Card Scan $timeStamp"
                                    val newDoc = ScannedDocument(
                                        id = docId,
                                        title = title,
                                        category = if (isPassportMode) DocCategory.PASSPORT else DocCategory.ID_CARD,
                                        pages = listOf(ScanPage(id = pageId, imagePath = targetFile.absolutePath))
                                    )
                                    repository.saveDocument(newDoc)
                                    isSaving = false
                                    onFinished(newDoc)
                                }
                            }
                        },
                        enabled = previewBitmap != null && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = EmeraldPrimary, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = EmeraldPrimary)
                        }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Export PDF Button
                    OutlinedButton(
                        onClick = {
                            if (previewBitmap != null) {
                                coroutineScope.launch {
                                    val docId = UUID.randomUUID().toString()
                                    val tempDoc = ScannedDocument(
                                        id = docId,
                                        title = if (isPassportMode) "Passport_Scan" else "ID_Card_Scan",
                                        category = if (isPassportMode) DocCategory.PASSPORT else DocCategory.ID_CARD,
                                        pages = listOf(
                                            ScanPage(
                                                id = UUID.randomUUID().toString(),
                                                imagePath = frontFile.absolutePath
                                            )
                                        )
                                    )
                                    // Save stitched bitmap directly to temp
                                    val tempFile = File(context.cacheDir, "id_stitched.jpg")
                                    ImageProcessor.saveBitmapToFile(previewBitmap!!, tempFile, quality = 95)
                                    val stitchedDoc = tempDoc.copy(pages = listOf(ScanPage(imagePath = tempFile.absolutePath)))

                                    val pdfFile = File(context.cacheDir, "${stitchedDoc.title}.pdf")
                                    PdfGenerator.generatePdf(context, stitchedDoc, pdfFile)

                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        type = "application/pdf"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share A4 ID PDF"))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share PDF")
                    }

                    // Save Document Button
                    Button(
                        onClick = {
                            if (previewBitmap != null && !isSaving) {
                                isSaving = true
                                coroutineScope.launch {
                                    val docId = UUID.randomUUID().toString()
                                    val docFolder = File(context.filesDir, "rds_documents/$docId").apply { mkdirs() }
                                    val pageId = UUID.randomUUID().toString()
                                    val targetFile = File(docFolder, "id_card_$pageId.jpg")

                                    ImageProcessor.saveBitmapToFile(previewBitmap!!, targetFile, quality = 95)

                                    val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                    val title = if (isPassportMode) "Passport Scan $timeStamp" else "ID Card Scan $timeStamp"
                                    val newDoc = ScannedDocument(
                                        id = docId,
                                        title = title,
                                        category = if (isPassportMode) DocCategory.PASSPORT else DocCategory.ID_CARD,
                                        pages = listOf(ScanPage(id = pageId, imagePath = targetFile.absolutePath))
                                    )
                                    repository.saveDocument(newDoc)
                                    isSaving = false
                                    onFinished(newDoc)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Doc", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Interactive A4 Sheet Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.size(240.dp, 339.dp), // A4 aspect ratio 1:1.414
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (previewBitmap != null && !isGeneratingPreview) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "A4 Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EmeraldPrimary)
                        }
                    }
                }
            }

            // Customization Options Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Layout Selector (if ID card mode)
                    if (!isPassportMode) {
                        Text(
                            text = "1. Page Layout",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(IdCardLayout.values()) { layout ->
                                val isSelected = layout == selectedLayout
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedLayout = layout },
                                    label = { Text(layout.displayName, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = EmeraldPrimary
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Filter Selector
                    Text(
                        text = "2. Color Filter",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ScanFilter.values()) { filter ->
                            val isSelected = filter == selectedFilter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = EmeraldPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Anti-Fraud KYC Watermark Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Anti-Fraud KYC Watermark",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Protects your card against unauthorized misuse",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = enableKycWatermark,
                            onCheckedChange = { enableKycWatermark = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
                        )
                    }

                    if (enableKycWatermark) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            label = { Text("Watermark Text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        // Watermark quick chips
                        val quickChips = listOf(
                            "FOR KYC VERIFICATION ONLY",
                            "FOR BANK USE ONLY",
                            "CONFIDENTIAL"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(quickChips) { chip ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { watermarkText = chip }
                                ) {
                                    Text(
                                        text = chip,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
