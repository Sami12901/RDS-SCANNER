package com.example.rdsscanner.ui.pdfreader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.rdsscanner.pdf.engine.AcrobatTool
import com.example.rdsscanner.pdf.engine.PageConfig
import com.example.rdsscanner.pdf.engine.PdfAnnotation
import com.example.rdsscanner.pdf.engine.PdfBakingEngine
import com.example.rdsscanner.pdf.engine.PdfPageRenderer
import com.example.rdsscanner.pdf.engine.ShapeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-featured Adobe Acrobat Reader & PDF Editor.
 * Real-time continuous vertical scrolling, night mode, highlighters, pens, text boxes,
 * comments, shapes, page organizer, and lossless PDF annotation baking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    pdfUri: Uri,
    documentTitle: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var renderer by remember { mutableStateOf<PdfPageRenderer?>(null) }
    var pageConfigs by remember { mutableStateOf<List<PageConfig>>(emptyList()) }
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }

    // Annotations map: page index -> mutable list of annotations
    val annotationsMap = remember { mutableStateMapOf<Int, MutableList<PdfAnnotation>>() }

    // Active tool and tool settings
    var activeTool by remember { mutableStateOf(AcrobatTool.VIEW) }
    var penColor by remember { mutableStateOf(Color(0xFF1E88E5)) } // Default Blue
    var penStrokeWidth by remember { mutableStateOf(6f) }
    var highlighterColor by remember { mutableStateOf(Color(0xFFFFEB3B)) } // Default Yellow
    var highlighterWidth by remember { mutableStateOf(26f) }
    var shapeType by remember { mutableStateOf(ShapeType.RECTANGLE) }

    var isNightMode by remember { mutableStateOf(false) }
    var showOrganizer by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Invert matrix for night mode
    val nightColorFilter = remember {
        val matrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ColorFilter.colorMatrix(matrix)
    }

    // Load PDF
    LaunchedEffect(pdfUri) {
        withContext(Dispatchers.IO) {
            try {
                val r = PdfPageRenderer.fromUri(context, pdfUri)
                renderer = r
                val initialList = (0 until r.pageCount).map { PageConfig(originalPageIndex = it, rotationDegrees = 0) }
                pageConfigs = initialList

                // Preload first few pages
                for (i in 0 until minOf(3, r.pageCount)) {
                    val (w, h) = r.getPageDimensions(i)
                    val bmp = r.renderPage(i, targetWidth = 1080)
                    if (bmp != null) pageBitmaps[i] = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.close()
        }
    }

    fun saveAndSharePdf(isSharing: Boolean) {
        val r = renderer ?: return
        isSaving = true
        scope.launch {
            val fileName = "Acrobat_${documentTitle ?: "Document"}_${System.currentTimeMillis()}.pdf"
            val outFile = File(context.cacheDir, fileName)

            val success = PdfBakingEngine.bakeAnnotations(
                renderer = r,
                annotationsByPage = annotationsMap,
                pageConfigs = pageConfigs,
                outputFile = outFile,
                dpiScale = 2.0f
            )

            isSaving = false
            if (success && outFile.exists()) {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile
                )

                if (isSharing) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Edited PDF"))
                } else {
                    Toast.makeText(context, "Saved to ${outFile.name}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Failed to export PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = documentTitle ?: "Adobe Acrobat PDF Reader",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1
                        )
                        val total = pageConfigs.size
                        Text(
                            text = if (total > 0) "$total ${if (total == 1) "page" else "pages"}" else "Loading...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Dark / Night Mode Toggle
                    IconButton(onClick = { isNightMode = !isNightMode }) {
                        Icon(
                            if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Night Mode",
                            tint = if (isNightMode) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Page Organizer
                    IconButton(onClick = { showOrganizer = true }) {
                        Icon(Icons.Default.GridOn, contentDescription = "Page Organizer")
                    }

                    // Save / Export
                    IconButton(onClick = { saveAndSharePdf(isSharing = false) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save PDF")
                    }

                    // Share
                    IconButton(onClick = { saveAndSharePdf(isSharing = true) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isNightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Acrobat Editing Floating Toolbar Dock
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = if (isNightMode) Color(0xFF2C2C2C) else MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    // Sub-palette (Color / Type selectors)
                    AnimatedVisibility(visible = activeTool == AcrobatTool.HIGHLIGHT) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colors = listOf(
                                Color(0xFFFFEB3B), // Yellow
                                Color(0xFF69F0AE), // Light Green
                                Color(0xFF80D8FF), // Cyan
                                Color(0xFFFF80AB)  // Pink
                            )
                            colors.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            width = if (highlighterColor == c) 3.dp else 1.dp,
                                            color = if (highlighterColor == c) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { highlighterColor = c }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = activeTool == AcrobatTool.PEN) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colors = listOf(
                                Color(0xFF1E88E5), // Blue
                                Color(0xFF212121), // Black
                                Color(0xFFE53935), // Red
                                Color(0xFF43A047)  // Green
                            )
                            colors.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            width = if (penColor == c) 3.dp else 1.dp,
                                            color = if (penColor == c) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { penColor = c }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = activeTool == AcrobatTool.SHAPE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShapeType.values().forEach { st ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (shapeType == st) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier.clickable { shapeType = st }
                                ) {
                                    Text(
                                        text = st.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = if (shapeType == st) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Main Tool Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. View / Pan
                        IconButton(onClick = { activeTool = AcrobatTool.VIEW }) {
                            Icon(
                                Icons.Default.PanTool,
                                contentDescription = "View",
                                tint = if (activeTool == AcrobatTool.VIEW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 2. Highlighter
                        IconButton(onClick = { activeTool = AcrobatTool.HIGHLIGHT }) {
                            Icon(
                                Icons.Default.Highlight,
                                contentDescription = "Highlight",
                                tint = if (activeTool == AcrobatTool.HIGHLIGHT) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 3. Pen
                        IconButton(onClick = { activeTool = AcrobatTool.PEN }) {
                            Icon(
                                Icons.Default.Brush,
                                contentDescription = "Pen",
                                tint = if (activeTool == AcrobatTool.PEN) penColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 4. Text Note (Type on PDF)
                        IconButton(onClick = { activeTool = AcrobatTool.TEXT }) {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = "Type on PDF",
                                tint = if (activeTool == AcrobatTool.TEXT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 5. Sticky Note (Comment)
                        IconButton(onClick = { activeTool = AcrobatTool.COMMENT }) {
                            Icon(
                                Icons.Default.Comment,
                                contentDescription = "Sticky Note",
                                tint = if (activeTool == AcrobatTool.COMMENT) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 6. Shapes
                        IconButton(onClick = { activeTool = AcrobatTool.SHAPE }) {
                            Icon(
                                Icons.Default.Category,
                                contentDescription = "Shapes",
                                tint = if (activeTool == AcrobatTool.SHAPE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 7. Eraser
                        IconButton(onClick = { activeTool = AcrobatTool.ERASER }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eraser",
                                tint = if (activeTool == AcrobatTool.ERASER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 8. Undo
                        IconButton(
                            onClick = {
                                // Undo last added annotation
                                val lastEntry = annotationsMap.entries.lastOrNull { it.value.isNotEmpty() }
                                if (lastEntry != null) {
                                    val list = lastEntry.value
                                    if (list.isNotEmpty()) {
                                        list.removeAt(list.size - 1)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Undo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        val currentRenderer = renderer

        if (currentRenderer == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(if (isNightMode) Color(0xFF121212) else Color(0xFFF0F2F5))
            ) {
                // Continuous Vertical Scrolling PDF Pages
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(pageConfigs, key = { index, cfg -> "${cfg.originalPageIndex}_$index" }) { displayIndex, pageCfg ->
                        val origIndex = pageCfg.originalPageIndex
                        var pageBitmap = pageBitmaps[origIndex]

                        // Lazy render page bitmap if not preloaded
                        LaunchedEffect(origIndex) {
                            if (pageBitmap == null) {
                                val bmp = currentRenderer.renderPage(origIndex, targetWidth = 1080)
                                if (bmp != null) {
                                    pageBitmaps[origIndex] = bmp
                                }
                            }
                        }

                        val (origW, origH) = remember(origIndex) {
                            // Points to aspect ratio
                            Pair(595, 842)
                        }
                        val isRotated90or270 = pageCfg.rotationDegrees % 180 != 0
                        val aspect = if (isRotated90or270) origH.toFloat() / origW else origW.toFloat() / origH

                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Page Frame
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isNightMode) Color(0xFF1E1E1E) else Color.White
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspect)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val bmp = pageBitmaps[origIndex]
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "PDF Page ${displayIndex + 1}",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .rotate(pageCfg.rotationDegrees.toFloat()),
                                            contentScale = ContentScale.Fit,
                                            colorFilter = if (isNightMode) nightColorFilter else null
                                        )

                                        // Vector Canvas Overlay
                                        val annotations = annotationsMap.getOrPut(origIndex) { mutableStateListOf() }
                                        PdfCanvasOverlay(
                                            pageIndex = origIndex,
                                            activeTool = activeTool,
                                            annotations = annotations,
                                            onAddAnnotation = { newAnn ->
                                                annotations.add(newAnn)
                                            },
                                            onDeleteAnnotation = { delAnn ->
                                                annotations.remove(delAnn)
                                            },
                                            penColor = penColor,
                                            penStrokeWidth = penStrokeWidth,
                                            highlighterColor = highlighterColor,
                                            highlighterWidth = highlighterWidth,
                                            shapeType = shapeType
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }
                            }

                            // Page Number Label
                            Text(
                                text = "Page ${displayIndex + 1} of ${pageConfigs.size}",
                                fontSize = 11.sp,
                                color = if (isNightMode) Color.LightGray else Color.DarkGray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(80.dp))
                    }
                }

                // Saving / Exporting Overlay
                if (isSaving) {
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
                                Text("Baking PDF Annotations...", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Page Organizer Dialog
    if (showOrganizer && renderer != null) {
        PdfPageOrganizerDialog(
            renderer = renderer!!,
            initialConfigs = pageConfigs,
            onDismiss = { showOrganizer = false },
            onApplyConfigs = { updated ->
                pageConfigs = updated
                showOrganizer = false
            }
        )
    }
}
