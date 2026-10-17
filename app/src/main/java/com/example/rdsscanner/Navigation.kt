package com.example.rdsscanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.rdsscanner.data.DocumentRepository
import com.example.rdsscanner.ui.detail.DocumentDetailScreen
import com.example.rdsscanner.ui.home.HomeScreen

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val backStack = rememberNavBackStack(HomeKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    repository = repository,
                    onDocumentClick = { docId ->
                        backStack.add(DetailKey(docId))
                    }
                )
            }
            entry<DetailKey> { key ->
                DocumentDetailScreen(
                    documentId = key.documentId,
                    repository = repository,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
