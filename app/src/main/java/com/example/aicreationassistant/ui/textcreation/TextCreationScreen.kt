package com.example.aicreationassistant.ui.textcreation

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
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
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.ui.components.ErrorDialog
import com.example.aicreationassistant.ui.components.FormatToggle
import com.example.aicreationassistant.ui.components.LoadingOverlay
import com.example.aicreationassistant.ui.components.MarkdownPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextCreationScreen(
    creationTypeKey: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToImageEdit: (String) -> Unit
) {
    val app = AiCreationApp.instance
    val viewModel: TextCreationViewModel = viewModel(
        factory = TextCreationViewModel.factory(
            creationTypeKey = creationTypeKey,
            deepSeekRepo = app.serviceLocator.deepSeekRepository,
            contentRepo = app.serviceLocator.contentRepository,
            networkMonitor = app.serviceLocator.networkMonitor
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Error dialog
    if (state.error != null) {
        ErrorDialog(
            message = state.error!!,
            onDismiss = { viewModel.clearError() },
            onRetry = {
                viewModel.clearError()
                viewModel.generateContent()
            }
        )
    }

    // Saved toast
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            Toast.makeText(context, "已添加到收藏", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.creationType.displayName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 提示信息
                Text(
                    text = state.creationType.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 输入框
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { viewModel.updatePrompt(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            when (state.creationType) {
                                CreationType.SOCIAL_MEDIA -> "输入你的心情、主题或关键词…"
                                CreationType.PRODUCT_DESC -> "输入商品名称、卖点等信息…"
                                else -> "输入你想生成的内容…"
                            }
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    supportingText = {
                        Text(
                            "${state.charCount}/${com.example.aicreationassistant.util.Constants.MAX_PROMPT_LENGTH}",
                            color = if (state.charCount >= com.example.aicreationassistant.util.Constants.MAX_PROMPT_LENGTH)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    enabled = !state.isLoading
                )

                // 生成按钮
                Button(
                    onClick = { viewModel.generateContent() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && state.prompt.trim().length >= 2
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在生成…")
                    } else {
                        Text("生成内容")
                    }
                }

                // 生成结果区域
                if (state.generatedContent.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // 格式切换
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "生成结果",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FormatToggle(
                            isMarkdown = state.isMarkdownPreview,
                            onToggle = { viewModel.toggleFormat() }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 可编辑的内容区域
                    if (state.isMarkdownPreview) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            MarkdownPreview(
                                markdown = state.editedContent.ifBlank { state.generatedContent },
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = state.editedContent.ifBlank { state.generatedContent },
                            onValueChange = { viewModel.updateEditedContent(it) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            maxLines = 20,
                            enabled = !state.isLoading
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 操作按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 收藏
                        OutlinedButton(
                            onClick = { viewModel.saveToFavorites() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (state.isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (state.isSaved) "已收藏" else "收藏")
                        }

                        // 分享
                        OutlinedButton(
                            onClick = { viewModel.shareText(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("分享")
                        }
                    }
                }
            }

            if (state.isLoading) {
                LoadingOverlay(message = "AI 正在为你创作…")
            }
        }
    }
}
