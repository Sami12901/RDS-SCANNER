package com.example.rdsscanner.ui.main

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.rdsscanner.data.DocumentRepository
import com.example.rdsscanner.model.ScannedDocument
import com.example.rdsscanner.scanner.PdfGenerator
import com.example.rdsscanner.ui.camera.ScannerMode
import com.example.rdsscanner.ui.home.HomeScreen
import com.example.rdsscanner.ui.pdftools.PdfToolsScreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppHub(val title: String) {
    SCANNER("Scanner & Docs"),
    PDF_TOOLS("PDF Tools"),
    PDF_READER("PDF Reader")
}

/**
 * 3-Hub Bottom Navigation integrating CamScanner and Adobe Acrobat Reader: Edit PDF.
 */
@Composable
fun MainBottomNavigation(
    repository: DocumentRepository,
    onDocumentClick: (String) -> Unit,
    onOpenScanner: (ScannerMode) -> Unit,
    onOpenCrop: (File) -> Unit,
    onOpenIdCardWorkflow: (frontFile: File, backFile: File?, isPassport: Boolean) -> Unit,
    onOpenPdfReader: (Uri, String?) -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppHub.SCANNER) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 8.dp
            ) {
                // Hub 1: CamScanner Document Scanner & Library
                NavigationBarItem(
                    selected = selectedTab == AppHub.SCANNER,
                    onClick = { selectedTab = AppHub.SCANNER },
                    icon = { Icon(Icons.Default.DocumentScanner, contentDescription = "Scanner & Docs") },
                    label = { Text("Scanner & Docs") }
                )

                // Hub 2: Adobe Acrobat PDF Tools Suite
                NavigationBarItem(
                    selected = selectedTab == AppHub.PDF_TOOLS,
                    onClick = { selectedTab = AppHub.PDF_TOOLS },
                    icon = { Icon(Icons.Default.Widgets, contentDescription = "PDF Tools") },
                    label = { Text("PDF Tools") }
                )

                // Hub 3: Adobe Acrobat PDF Reader & Editor
                NavigationBarItem(
                    selected = selectedTab == AppHub.PDF_READER,
                    onClick = { selectedTab = AppHub.PDF_READER },
                    icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Reader") },
                    label = { Text("PDF Reader") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Crossfade(targetState = selectedTab, label = "HubCrossfade") { hub ->
                when (hub) {
                    AppHub.SCANNER -> {
                        HomeScreen(
                            repository = repository,
                            onDocumentClick = onDocumentClick,
                            onOpenScanner = onOpenScanner,
                            onOpenCrop = onOpenCrop,
                            onOpenIdCardWorkflow = onOpenIdCardWorkflow
                        )
                    }
                    AppHub.PDF_TOOLS -> {
                        PdfToolsScreen(
                            onOpenPdfReader = onOpenPdfReader
                        )
                    }
                    AppHub.PDF_READER -> {
                        PdfReaderHubScreen(
                            repository = repository,
                            onOpenPdfReader = onOpenPdfReader
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hub 3: Adobe Acrobat PDF Reader Launcher & Recent Documents screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderHubScreen(
    repository: DocumentRepository,
    onOpenPdfReader: (Uri, String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val documents by repository.documents.collectAsState()

    // SAF PDF File Picker
    val pdfFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onOpenPdfReader(uri, "External Document.pdf")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Adobe Acrobat Reader", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("View, annotate, markup & edit PDFs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Action Card: Open PDF from Storage
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pdfFilePicker.launch("application/pdf") },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Open PDF File",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Browse files from your phone, Downloads, Google Drive or SD card",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Section Header: Scanned Documents ready for Acrobat Markup
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Your Scanned PDFs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }

            if (documents.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No scanned documents yet. Use the 'Scanner & Docs' tab to scan your first document, or tap 'Open PDF File' to read any PDF on your phone.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(documents) { doc ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    val pdfFile = repository.getOutputPdfFile(doc)
                                    val result = PdfGenerator.generatePdf(context, doc, pdfFile)
                                    result.onSuccess { file ->
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        onOpenPdfReader(uri, "${doc.title}.pdf")
                                    }
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                Text(
                                    text = "${doc.pages.size} pages • ${dateFormat.format(Date(doc.createdAt))}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "Read & Edit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
