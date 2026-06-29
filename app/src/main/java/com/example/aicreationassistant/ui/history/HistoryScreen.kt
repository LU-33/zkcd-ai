package com.example.aicreationassistant.ui.history

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicreationassistant.AiCreationApp
import com.example.aicreationassistant.ui.components.ContentListItem
import com.example.aicreationassistant.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit
) {
    val app = AiCreationApp.instance
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(app.serviceLocator.contentRepository)
    )
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("生成历史") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (historyItems.isEmpty()) {
            EmptyState(
                icon = Icons.Default.History,
                message = "暂无历史记录",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = historyItems,
                    key = { it.id }
                ) { item ->
                    ContentListItem(
                        item = item,
                        onClick = { onNavigateToDetail(item.id) },
                        onDelete = {
                            viewModel.deleteHistory(item.id)
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}
