package com.example.aicreationassistant.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicreationassistant.AiCreationApp
import com.example.aicreationassistant.ui.components.FormatToggle
import com.example.aicreationassistant.ui.components.MarkdownPreview
import com.example.aicreationassistant.util.toRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    contentId: Long,
    onNavigateBack: () -> Unit
) {
    val app = AiCreationApp.instance
    val viewModel: DetailViewModel = viewModel(
        factory = DetailViewModelFactory(
            contentId = contentId,
            contentRepo = app.serviceLocator.contentRepository
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // Share
                    IconButton(onClick = { viewModel.shareContent(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                    // Toggle favorite
                    IconButton(onClick = {
                        viewModel.toggleFavorite()
                        val msg = if (state.isFavorite) "已取消收藏" else "已添加到收藏"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "收藏"
                        )
                    }
                    // Delete
                    IconButton(onClick = {
                        viewModel.deleteContent()
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            )
        }
    ) { padding ->
        if (state.contentItem == null && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("内容不存在或已被删除", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.contentItem?.let { item ->
                    // Metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(item.creationType.displayName) }
                        )
                        Text(
                            text = item.createdAt.toRelativeTime(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Original prompt
                    if (item.originalPrompt.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "原始输入",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    item.originalPrompt,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Format toggle
                    FormatToggle(
                        isMarkdown = state.isMarkdownPreview,
                        onToggle = { viewModel.toggleFormat() }
                    )

                    // Content
                    if (state.isMarkdownPreview) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            MarkdownPreview(
                                markdown = item.content,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
