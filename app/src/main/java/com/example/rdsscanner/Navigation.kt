package com.example.rdsscanner

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.rdsscanner.data.DocumentRepository
import com.example.rdsscanner.model.DocCategory
import com.example.rdsscanner.ui.camera.CameraScannerScreen
import com.example.rdsscanner.ui.camera.ScannerMode
import com.example.rdsscanner.ui.crop.PerspectiveCropScreen
import com.example.rdsscanner.ui.detail.DocumentDetailScreen
import com.example.rdsscanner.ui.home.HomeScreen
import com.example.rdsscanner.ui.idcard.IdCardWorkflowScreen
import com.example.rdsscanner.ui.main.MainBottomNavigation
import androidx.compose.runtime.LaunchedEffect
import com.example.rdsscanner.ui.pdfreader.PdfReaderScreen
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MainNavigation(initialPdfUri: Uri? = null) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val backStack = rememberNavBackStack(HomeKey)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialPdfUri) {
        if (initialPdfUri != null) {
            backStack.add(PdfReaderKey(initialPdfUri.toString(), "Opened PDF Document"))
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            // 1. Unified 3-Hub Navigation (Scanner & Docs, PDF Tools, PDF Reader)
            entry<HomeKey> {
                MainBottomNavigation(
                    repository = repository,
                    onDocumentClick = { docId ->
                        backStack.add(DetailKey(docId))
                    },
                    onOpenScanner = { mode ->
                        backStack.add(CameraScanKey(mode.name))
                    },
                    onOpenCrop = { file ->
                        backStack.add(CropKey(file.absolutePath, "DOCS"))
                    },
                    onOpenIdCardWorkflow = { front, back, isPassport ->
                        backStack.add(IdCardWorkflowKey(front.absolutePath, back?.absolutePath, isPassport))
                    },
                    onOpenPdfReader = { uri, title ->
                        backStack.add(PdfReaderKey(uri.toString(), title))
                    }
                )
            }

            // 2. Document Detail Screen
            entry<DetailKey> { key ->
                DocumentDetailScreen(
                    documentId = key.documentId,
                    repository = repository,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenPdfReader = { uri, title ->
                        backStack.add(PdfReaderKey(uri.toString(), title))
                    }
                )
            }

            // 3. CameraX Viewfinder Screen
            entry<CameraScanKey> { key ->
                val mode = try { ScannerMode.valueOf(key.modeName) } catch (e: Exception) { ScannerMode.DOCS }
                CameraScannerScreen(
                    initialMode = mode,
                    onBack = { backStack.removeLastOrNull() },
                    onSingleCaptured = { file ->
                        backStack.add(CropKey(file.absolutePath, "DOCS"))
                    },
                    onPassportCaptured = { file ->
                        backStack.add(IdCardWorkflowKey(file.absolutePath, null, isPassport = true))
                    },
                    onIdCardCaptured = { front, back ->
                        backStack.add(IdCardWorkflowKey(front.absolutePath, back?.absolutePath, isPassport = false))
                    },
                    onBatchCaptured = { files ->
                        coroutineScope.launch {
                            val newDoc = repository.createDocumentFromUris(
                                titlePrefix = "Batch Scan",
                                category = DocCategory.GENERAL,
                                uris = files.map { Uri.fromFile(it) }
                            )
                            while (backStack.isNotEmpty() && backStack.lastOrNull() !is HomeKey) {
                                backStack.removeLastOrNull()
                            }
                            backStack.add(DetailKey(newDoc.id))
                        }
                    }
                )
            }

            // 4. Perspective Crop Screen
            entry<CropKey> { key ->
                PerspectiveCropScreen(
                    imageFile = File(key.imagePath),
                    onBack = { backStack.removeLastOrNull() },
                    onCropped = { croppedFile ->
                        coroutineScope.launch {
                            val newDoc = repository.createDocumentFromUris(
                                titlePrefix = "Scan",
                                category = DocCategory.GENERAL,
                                uris = listOf(Uri.fromFile(croppedFile))
                            )
                            while (backStack.isNotEmpty() && backStack.lastOrNull() !is HomeKey) {
                                backStack.removeLastOrNull()
                            }
                            backStack.add(DetailKey(newDoc.id))
                        }
                    }
                )
            }

            // 5. Dedicated ID Card & Passport Workflow Screen
            entry<IdCardWorkflowKey> { key ->
                IdCardWorkflowScreen(
                    frontFile = File(key.frontPath),
                    backFile = key.backPath?.let { File(it) },
                    isPassportMode = key.isPassport,
                    repository = repository,
                    onBack = { backStack.removeLastOrNull() },
                    onFinished = { newDoc ->
                        while (backStack.isNotEmpty() && backStack.lastOrNull() !is HomeKey) {
                            backStack.removeLastOrNull()
                        }
                        backStack.add(DetailKey(newDoc.id))
                    }
                )
            }

            // 6. Adobe Acrobat Reader & PDF Editor Screen
            entry<PdfReaderKey> { key ->
                PdfReaderScreen(
                    pdfUri = Uri.parse(key.uriString),
                    documentTitle = key.title,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
