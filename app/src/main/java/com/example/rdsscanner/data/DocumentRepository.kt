package com.example.rdsscanner.data

import android.content.Context
import android.net.Uri
import com.example.rdsscanner.model.DocCategory
import com.example.rdsscanner.model.ScanFilter
import com.example.rdsscanner.model.ScanPage
import com.example.rdsscanner.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(private val context: Context) {

    private val documentsDir = File(context.filesDir, "rds_documents").apply { mkdirs() }
    private val metadataFile = File(documentsDir, "documents_meta.json")

    private val _documents = MutableStateFlow<List<ScannedDocument>>(emptyList())
    val documents: StateFlow<List<ScannedDocument>> = _documents.asStateFlow()

    init {
        loadDocuments()
    }

    private fun loadDocuments() {
        if (!metadataFile.exists()) {
            _documents.value = emptyList()
            return
        }

        try {
            val jsonStr = metadataFile.readText()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<ScannedDocument>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val categoryName = obj.optString("category", DocCategory.GENERAL.name)
                val category = try { DocCategory.valueOf(categoryName) } catch (e: Exception) { DocCategory.GENERAL }
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())

                val isStarred = obj.optBoolean("isStarred", false)

                val pagesArray = obj.getJSONArray("pages")
                val pages = mutableListOf<ScanPage>()
                for (j in 0 until pagesArray.length()) {
                    val pObj = pagesArray.getJSONObject(j)
                    val pId = pObj.getString("id")
                    val imagePath = pObj.getString("imagePath")
                    val originalPath = pObj.optString("originalPath", imagePath)
                    val rot = pObj.optInt("rotationDegrees", 0)
                    val filterName = pObj.optString("filter", ScanFilter.MAGIC_COLOR.name)
                    val filter = try { ScanFilter.valueOf(filterName) } catch (e: Exception) { ScanFilter.MAGIC_COLOR }
                    val brightness = pObj.optDouble("brightness", 0.0).toFloat()
                    val contrast = pObj.optDouble("contrast", 1.0).toFloat()
                    val ocr = if (pObj.has("ocrText")) pObj.getString("ocrText") else null
                    pages.add(ScanPage(
                        id = pId,
                        imagePath = imagePath,
                        originalPath = originalPath,
                        rotationDegrees = rot,
                        filter = filter,
                        brightness = brightness,
                        contrast = contrast,
                        ocrText = ocr
                    ))
                }

                list.add(ScannedDocument(
                    id = id,
                    title = title,
                    category = category,
                    pages = pages,
                    isStarred = isStarred,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                ))
            }

            _documents.value = list.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            e.printStackTrace()
            _documents.value = emptyList()
        }
    }

    private suspend fun persistDocuments() = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            _documents.value.forEach { doc ->
                val obj = JSONObject().apply {
                    put("id", doc.id)
                    put("title", doc.title)
                    put("category", doc.category.name)
                    put("isStarred", doc.isStarred)
                    put("createdAt", doc.createdAt)
                    put("updatedAt", doc.updatedAt)

                    val pagesArray = JSONArray()
                    doc.pages.forEach { p ->
                        val pObj = JSONObject().apply {
                            put("id", p.id)
                            put("imagePath", p.imagePath)
                            put("originalPath", p.originalPath)
                            put("rotationDegrees", p.rotationDegrees)
                            put("filter", p.filter.name)
                            put("brightness", p.brightness.toDouble())
                            put("contrast", p.contrast.toDouble())
                            p.ocrText?.let { put("ocrText", it) }
                        }
                        pagesArray.put(pObj)
                    }
                    put("pages", pagesArray)
                }
                jsonArray.put(obj)
            }
            metadataFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createDocumentFromUris(
        titlePrefix: String = "RDS Scan",
        category: DocCategory = DocCategory.GENERAL,
        uris: List<Uri>
    ): ScannedDocument = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val docTitle = "$titlePrefix $timeStamp"
        val docId = UUID.randomUUID().toString()
        val docFolder = File(documentsDir, docId).apply { mkdirs() }

        val pages = uris.mapIndexed { index, uri ->
            val pageId = UUID.randomUUID().toString()
            val targetFile = File(docFolder, "page_${index + 1}_$pageId.jpg")
            copyUriToFile(uri, targetFile)
            ScanPage(id = pageId, imagePath = targetFile.absolutePath)
        }

        val newDoc = ScannedDocument(
            id = docId,
            title = docTitle,
            category = category,
            pages = pages,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        _documents.value = listOf(newDoc) + _documents.value
        persistDocuments()
        newDoc
    }

    suspend fun saveDocument(doc: ScannedDocument) = withContext(Dispatchers.IO) {
        val current = _documents.value.toMutableList()
        val index = current.indexOfFirst { it.id == doc.id }
        if (index != -1) {
            current[index] = doc.copy(updatedAt = System.currentTimeMillis())
        } else {
            current.add(0, doc)
        }
        _documents.value = current.sortedByDescending { it.updatedAt }
        persistDocuments()
    }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) {
        val current = _documents.value.toMutableList()
        current.removeAll { it.id == docId }
        _documents.value = current
        persistDocuments()

        // Clean up files
        val docFolder = File(documentsDir, docId)
        if (docFolder.exists()) {
            docFolder.deleteRecursively()
        }
    }

    suspend fun rotatePage(docId: String, pageId: String) = withContext(Dispatchers.IO) {
        val current = _documents.value.toMutableList()
        val docIndex = current.indexOfFirst { it.id == docId }
        if (docIndex != -1) {
            val doc = current[docIndex]
            val updatedPages = doc.pages.map { p ->
                if (p.id == pageId) {
                    p.copy(rotationDegrees = (p.rotationDegrees + 90) % 360)
                } else p
            }
            current[docIndex] = doc.copy(pages = updatedPages, updatedAt = System.currentTimeMillis())
            _documents.value = current
            persistDocuments()
        }
    }

    suspend fun deletePage(docId: String, pageId: String) = withContext(Dispatchers.IO) {
        val current = _documents.value.toMutableList()
        val docIndex = current.indexOfFirst { it.id == docId }
        if (docIndex != -1) {
            val doc = current[docIndex]
            val pageToDelete = doc.pages.find { it.id == pageId }
            pageToDelete?.let { File(it.imagePath).delete() }

            val updatedPages = doc.pages.filter { it.id != pageId }
            if (updatedPages.isEmpty()) {
                current.removeAt(docIndex)
                File(documentsDir, docId).deleteRecursively()
            } else {
                current[docIndex] = doc.copy(pages = updatedPages, updatedAt = System.currentTimeMillis())
            }
            _documents.value = current
            persistDocuments()
        }
    }

    suspend fun updatePageOcrText(docId: String, pageId: String, ocrText: String) = withContext(Dispatchers.IO) {
        val current = _documents.value.toMutableList()
        val docIndex = current.indexOfFirst { it.id == docId }
        if (docIndex != -1) {
            val doc = current[docIndex]
            val updatedPages = doc.pages.map { p ->
                if (p.id == pageId) p.copy(ocrText = ocrText) else p
            }
            current[docIndex] = doc.copy(pages = updatedPages, updatedAt = System.currentTimeMillis())
            _documents.value = current
            persistDocuments()
        }
    }

    fun getDocumentById(id: String): ScannedDocument? {
        return _documents.value.find { it.id == id }
    }

    fun getOutputPdfFile(document: ScannedDocument): File {
        val sanitizedTitle = document.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(context.cacheDir, "${sanitizedTitle}.pdf")
    }

    private suspend fun copyUriToFile(uri: Uri, targetFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        val rotated = com.example.rdsscanner.scanner.ImageProcessor.loadRotatedBitmapFromFile(targetFile)
        if (rotated != null) {
            com.example.rdsscanner.scanner.ImageProcessor.saveBitmapToFile(rotated, targetFile, quality = 95)
        }
    }
}
