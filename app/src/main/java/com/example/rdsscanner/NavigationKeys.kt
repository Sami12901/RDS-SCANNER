package com.example.rdsscanner

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data class DetailKey(val documentId: String) : NavKey

@Serializable
data class CameraScanKey(val modeName: String = "DOCS") : NavKey

@Serializable
data class CropKey(val imagePath: String, val modeName: String = "DOCS") : NavKey

@Serializable
data class IdCardWorkflowKey(
    val frontPath: String,
    val backPath: String? = null,
    val isPassport: Boolean = false
) : NavKey

@Serializable
data class PdfReaderKey(
    val uriString: String,
    val title: String? = null
) : NavKey
