package com.example.aicreationassistant.ui.imagedesc

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aicreationassistant.AiCreationApp
import com.example.aicreationassistant.ui.components.ErrorDialog
import com.example.aicreationassistant.ui.components.FormatToggle
import com.example.aicreationassistant.ui.components.LoadingOverlay
import com.example.aicreationassistant.ui.components.MarkdownPreview
import com.example.aicreationassistant.util.getFileName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDescScreen(
    editedImageUriResult: String? = null,
    onClearEditedResult: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToImageEdit: (String) -> Unit
) {
    val app = AiCreationApp.instance
    val viewModel: ImageDescViewModel = viewModel(
        factory = ImageDescViewModelFactory(
            deepSeekRepo = app.serviceLocator.deepSeekRepository,
            contentRepo = app.serviceLocator.contentRepository,
            networkMonitor = app.serviceLocator.networkMonitor
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = it.getFileName(context)
            viewModel.onImageSelected(it, fileName)
        }
    }

    // 接收图片编辑页面返回的结果
    LaunchedEffect(editedImageUriResult) {
        if (editedImageUriResult != null) {
            val uri = android.net.Uri.parse(editedImageUriResult)
            val fileName = uri.getFileName(context)
            viewModel.onImageSelected(uri, fileName)
            onClearEditedResult()
        }
    }

    // Error dialog
    if (state.error != null) {
        ErrorDialog(
            message = state.error!!,
            onDismiss = { viewModel.clearError() },
            onRetry = {
                viewModel.clearError()
                viewModel.generateDescription(context)
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
                title = { Text("图片描述") },
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
                // 图片预览区域
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (state.selectedImageUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(state.selectedImageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "选中的图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "选择一张图片",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // 选择图片按钮
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.selectedImageUri != null) "更换图片" else "选择图片")
                }

                // 编辑图片按钮（选图后可用）
                if (state.selectedImageUri != null) {
                    OutlinedButton(
                        onClick = {
                            state.selectedImageUri?.let {
                                onNavigateToImageEdit(it.toString())
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("编辑图片（裁剪 / 水印）")
                    }
                }

                // 可选提示
                OutlinedTextField(
                    value = state.optionalPrompt,
                    onValueChange = { viewModel.updateOptionalPrompt(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("可选：补充描述要求（如「详细描述颜色」）") },
                    minLines = 2,
                    maxLines = 3,
                    enabled = !state.isLoading && state.selectedImageUri != null
                )

                // 生成按钮
                Button(
                    onClick = { viewModel.generateDescription(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && state.selectedImageUri != null
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在生成描述…")
                    } else {
                        Text("生成描述")
                    }
                }

                // 结果区域
                if (state.generatedDescription.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "图片描述",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FormatToggle(
                            isMarkdown = state.isMarkdownPreview,
                            onToggle = { viewModel.toggleFormat() }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (state.isMarkdownPreview) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            MarkdownPreview(
                                markdown = state.editedDescription.ifBlank { state.generatedDescription },
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = state.editedDescription.ifBlank { state.generatedDescription },
                            onValueChange = { viewModel.updateEditedDescription(it) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            maxLines = 15,
                            enabled = !state.isLoading
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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

                        OutlinedButton(
                            onClick = {
                                val content = state.editedDescription.ifBlank { state.generatedDescription }
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, content)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "分享到"))
                            },
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
                LoadingOverlay(message = "AI 正在分析图片…")
            }
        }
    }
}
