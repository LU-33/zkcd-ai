package com.example.aicreationassistant.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aicreationassistant.AiCreationApp
import com.example.aicreationassistant.domain.model.CreationType
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

                    // 标题区域（可编辑）
                    if (state.isEditingTitle) {
                        OutlinedTextField(
                            value = state.editingTitle,
                            onValueChange = { viewModel.updateEditingTitle(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("标题") },
                            singleLine = true,
                            supportingText = { Text("${state.editingTitle.length}/30") }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.cancelEditingTitle() }) {
                                Text("取消")
                            }
                            Button(onClick = { viewModel.saveTitle() }) {
                                Text("保存")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.startEditingTitle() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑标题",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Original prompt（可展开/收起）
                    if (item.originalPrompt.isNotBlank()) {
                        var promptExpanded by remember { mutableStateOf(false) }
                        val isLong = item.originalPrompt.length > 80
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "原始输入",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isLong) {
                                        TextButton(
                                            onClick = { promptExpanded = !promptExpanded },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                if (promptExpanded) "收起 ▲" else "展开 ▼",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    item.originalPrompt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (promptExpanded) Int.MAX_VALUE else 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 商品图片（仅商品描述）
                    if (item.creationType == CreationType.PRODUCT_DESC && !item.imageUri.isNullOrBlank()) {
                        val productImages = item.imageUri.split(",").filter { it.isNotBlank() }
                        if (productImages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "商品图片",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                productImages.forEach { uriStr ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(Uri.parse(uriStr))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "商品图片",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    // Format toggle + Edit button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormatToggle(
                            isMarkdown = state.isMarkdownPreview,
                            onToggle = { viewModel.toggleFormat() }
                        )
                        if (!state.isEditing) {
                            TextButton(onClick = { viewModel.startEditing() }) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("编辑")
                            }
                        }
                    }

                    // Content
                    if (state.isEditing) {
                        OutlinedTextField(
                            value = state.editingContent,
                            onValueChange = { viewModel.updateEditingContent(it) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 8,
                            maxLines = 20,
                            enabled = !state.isSaving
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            TextButton(onClick = { viewModel.cancelEditing() }) {
                                Text("取消")
                            }
                            Button(onClick = { viewModel.saveEdit() }) {
                                Text("保存修改")
                            }
                        }
                    } else if (state.isMarkdownPreview) {
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
