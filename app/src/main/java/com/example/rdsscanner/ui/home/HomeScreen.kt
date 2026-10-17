package com.example.rdsscanner.ui.home

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.rdsscanner.data.DocumentRepository
import com.example.rdsscanner.model.DocCategory
import com.example.rdsscanner.model.ScannedDocument
import com.example.rdsscanner.scanner.DocumentScannerHelper
import com.example.rdsscanner.scanner.PdfGenerator
import com.example.rdsscanner.theme.EmeraldPrimary
import com.example.rdsscanner.ui.about.AboutDialog
import com.example.rdsscanner.ui.camera.ScannerMode
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class SortOption(val title: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    NAME_ASC("Name (A-Z)"),
    PAGE_COUNT("Pages Count")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: DocumentRepository,
    onDocumentClick: (String) -> Unit,
    onOpenScanner: (ScannerMode) -> Unit,
    onOpenCrop: (File) -> Unit,
    onOpenIdCardWorkflow: (frontFile: File, backFile: File?, isPassport: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity

    val documents by repository.documents.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DocCategory.ALL) }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Google ML Kit Official Document Scanner
    val gmsScanner = remember {
        activity?.let { DocumentScannerHelper.createScanner(it) }
    }
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val gmsResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = gmsResult?.pages
            if (!pages.isNullOrEmpty()) {
                coroutineScope.launch {
                    val newDoc = repository.createDocumentFromUris(
                        titlePrefix = "Scan",
                        category = DocCategory.GENERAL,
                        uris = pages.map { it.imageUri }
                    )
                    onDocumentClick(newDoc.id)
                }
            }
        }
    }

    fun startSmartScan() {
        if (activity != null && gmsScanner != null) {
            DocumentScannerHelper.startScan(
                scanner = gmsScanner,
                activity = activity,
                onSuccess = { request -> scannerLauncher.launch(request) },
                onError = {
                    // Fall back to custom CameraX scanner on devices without Google Play Services
                    onOpenScanner(ScannerMode.DOCS)
                }
            )
        } else {
            onOpenScanner(ScannerMode.DOCS)
        }
    }

    // Gallery Picker
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                // If single image, send to perspective crop screen first!
                coroutineScope.launch {
                    val tempFile = File(context.cacheDir, "crop_in_${UUID.randomUUID()}.jpg")
                    context.contentResolver.openInputStream(uris.first())?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    onOpenCrop(tempFile)
                }
            } else {
                coroutineScope.launch {
                    val newDoc = repository.createDocumentFromUris(
                        titlePrefix = "Gallery Import",
                        category = DocCategory.GENERAL,
                        uris = uris
                    )
                    Toast.makeText(context, "Imported ${uris.size} pages", Toast.LENGTH_SHORT).show()
                    onDocumentClick(newDoc.id)
                }
            }
        }
    }

    // Deep search (matches document title OR any page's extracted OCR text!)
    val searchedDocs = documents.filter { doc ->
        val query = searchQuery.trim()
        val matchesTitle = doc.title.contains(query, ignoreCase = true)
        val matchesOcr = doc.pages.any { it.ocrText?.contains(query, ignoreCase = true) == true }
        val matchesCategory = selectedCategory == DocCategory.ALL || doc.category == selectedCategory

        (query.isEmpty() || matchesTitle || matchesOcr) && matchesCategory
    }

    // Sort documents
    val sortedDocs = when (sortOption) {
        SortOption.DATE_DESC -> searchedDocs.sortedByDescending { it.updatedAt }
        SortOption.DATE_ASC -> searchedDocs.sortedBy { it.updatedAt }
        SortOption.NAME_ASC -> searchedDocs.sortedBy { it.title.lowercase() }
        SortOption.PAGE_COUNT -> searchedDocs.sortedByDescending { it.pageCount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(EmeraldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "RDS SCANNER",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Develop by Sami • Open Source",
                                fontSize = 11.sp,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                actions = {
                    // Sort Menu
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.values().forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = opt.title,
                                            fontWeight = if (opt == sortOption) FontWeight.Bold else FontWeight.Normal,
                                            color = if (opt == sortOption) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        sortOption = opt
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // About App
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About App", tint = EmeraldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { startSmartScan() },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White) },
                text = { Text("Scan Document", color = Color.White, fontWeight = FontWeight.Bold) },
                containerColor = EmeraldPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Quick Feature Tiles (CamScanner Style)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    QuickTile(
                        icon = Icons.Default.DocumentScanner,
                        label = "Smart Scan",
                        onClick = { startSmartScan() }
                    )
                }
                item {
                    QuickTile(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        onClick = { onOpenScanner(ScannerMode.DOCS) }
                    )
                }
                item {
                    QuickTile(
                        icon = Icons.Default.Badge,
                        label = "ID Card",
                        onClick = { onOpenScanner(ScannerMode.ID_CARD) }
                    )
                }
                item {
                    QuickTile(
                        icon = Icons.Default.Flight,
                        label = "Passport",
                        onClick = { onOpenScanner(ScannerMode.PASSPORT) }
                    )
                }
                item {
                    QuickTile(
                        icon = Icons.Default.PictureAsPdf,
                        label = "Batch",
                        onClick = { onOpenScanner(ScannerMode.BATCH) }
                    )
                }
                item {
                    QuickTile(
                        icon = Icons.Default.AddPhotoAlternate,
                        label = "Import",
                        onClick = { galleryPicker.launch("image/*") }
                    )
                }
            }

            // Search Bar with Deep OCR capability
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search by name or inside OCR text...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Category Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DocCategory.values()) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category.displayName, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Document List
            if (sortedDocs.isEmpty()) {
                EmptyStateView(
                    searchQuery = searchQuery,
                    onScanClick = { onOpenScanner(ScannerMode.DOCS) },
                    onIdCardClick = { onOpenScanner(ScannerMode.ID_CARD) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedDocs, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            onClick = { onDocumentClick(doc.id) },
                            onShare = {
                                coroutineScope.launch {
                                    val outputFile = repository.getOutputPdfFile(doc)
                                    val result = PdfGenerator.generatePdf(context, doc, outputFile)
                                    result.onSuccess { pdfFile ->
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            type = "application/pdf"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share PDF"))
                                    }
                                }
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    repository.deleteDocument(doc.id)
                                    Toast.makeText(context, "Deleted ${doc.title}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DocumentCard(
    document: ScannedDocument,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val formattedDate = remember(document.updatedAt) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(document.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(68.dp, 88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val thumb = document.thumbnailPath
                if (thumb != null && File(thumb).exists()) {
                    AsyncImage(
                        model = File(thumb),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "${document.pageCount}P",
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = EmeraldPrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = document.category.displayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Has OCR badge
                    if (document.pages.any { !it.ocrText.isNullOrBlank() }) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "OCR Extracted",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share Clean PDF") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldPrimary) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Document", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    searchQuery: String,
    onScanClick: () -> Unit,
    onIdCardClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(EmeraldPrimary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (searchQuery.isNotEmpty()) "No matching documents found" else "No Scanned Documents",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (searchQuery.isNotEmpty()) "Try searching for a different word or clear your query."
                   else "Scan documents, ID cards, books, or notes with live perspective crop and watermark-free PDF exports.\n\nDeveloped by Sami • Open Source",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onScanClick,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Docs", color = Color.White)
            }

            OutlinedButton(onClick = onIdCardClick) {
                Icon(Icons.Default.Badge, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("ID Card Mode")
            }
        }
    }
}
