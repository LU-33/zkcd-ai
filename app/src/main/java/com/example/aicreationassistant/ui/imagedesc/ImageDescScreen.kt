package com.example.aicreationassistant.ui.imagedesc

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aicreationassistant.AiCreationApp
import com.example.aicreationassistant.ui.components.*
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
    val listState = rememberLazyListState()

    // 暂存更换图片的临时 URI
    var pendingImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImageFileName by remember { mutableStateOf("") }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = it.getFileName(context)
            if (state.hasGenerated) {
                // 对话模式下更换图片 → 弹出确认对话框
                viewModel.requestChangeImage()
                pendingImageUri = it
                pendingImageFileName = fileName
            } else {
                viewModel.onImageSelected(it, fileName)
            }
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
                viewModel.sendMessage(context)
            }
        )
    }

    // 更换图片确认对话框
    if (state.showChangeImageDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissChangeImageDialog() },
            title = { Text("更换图片") },
            text = { Text("更换图片将清空当前对话记录，确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImageUri
                    if (uri != null) {
                        viewModel.confirmChangeImage(uri, pendingImageFileName)
                        pendingImageUri = null
                        pendingImageFileName = ""
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissChangeImageDialog()
                    pendingImageUri = null
                    pendingImageFileName = ""
                }) { Text("取消") }
            }
        )
    }

    // Saved toast
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            Toast.makeText(context, "已添加到收藏", Toast.LENGTH_SHORT).show()
        }
    }

    // 收藏标题对话框
    if (state.showSaveDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveDialog() },
            title = { Text("收藏并命名") },
            text = {
                OutlinedTextField(
                    value = state.saveDialogTitle,
                    onValueChange = { viewModel.updateSaveDialogTitle(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标题") },
                    singleLine = true,
                    supportingText = { Text("${state.saveDialogTitle.length}/30") }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSaveToFavorites() }) {
                    Text("确认收藏")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // Auto-scroll to bottom
    LaunchedEffect(state.conversation.size) {
        if (state.conversation.isNotEmpty()) {
            listState.animateScrollToItem(state.conversation.size - 1)
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
        },
        bottomBar = {
            if (state.hasGenerated && state.selectedImageUri != null) {
                ChatInputBar(
                    value = state.currentInput,
                    onValueChange = { viewModel.updateCurrentInput(it) },
                    onSend = { viewModel.sendMessage(context) },
                    enabled = !state.isLoading,
                    hint = "告诉 AI 如何修改描述…"
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.hasGenerated) {
                // ========== 初始态：图片选择 + 可选 prompt + 生成按钮 ==========
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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

                    // 编辑图片按钮
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
                        value = state.currentInput,
                        onValueChange = { viewModel.updateCurrentInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("可选：补充描述要求（如「详细描述颜色」）") },
                        minLines = 2,
                        maxLines = 3,
                        enabled = !state.isLoading && state.selectedImageUri != null
                    )

                    // 生成按钮
                    Button(
                        onClick = { viewModel.sendMessage(context) },
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
                }
            } else {
                // ========== 对话态：紧凑图片卡片 + 聊天 + 操作栏 ==========
                Column(modifier = Modifier.fillMaxSize()) {
                    // 紧凑图片卡片
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 缩略图
                            if (state.selectedImageUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(state.selectedImageUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "当前图片",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.imageFileName.ifBlank { "已选图片" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${state.conversation.size} 轮对话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 更换图片
                            IconButton(
                                onClick = { imagePicker.launch("image/*") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "更换图片",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 编辑图片
                            IconButton(
                                onClick = {
                                    state.selectedImageUri?.let {
                                        onNavigateToImageEdit(it.toString())
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑图片",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // 聊天消息
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = state.conversation,
                            key = { it.timestamp }
                        ) { turn ->
                            val isLastAssistant = turn == state.conversation.lastOrNull() && turn.role == "assistant"
                            ChatBubble(
                                turn = turn,
                                isLastAssistant = isLastAssistant,
                                onEditClick = if (isLastAssistant) {
                                    { viewModel.updateEditedDescription(turn.content) }
                                } else null
                            )
                        }

                        if (state.isLoading) {
                            item(key = "typing") {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "AI 正在分析…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 操作栏
                    if (state.currentOutput.isNotBlank()) {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "当前结果",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                FormatToggle(
                                    isMarkdown = state.isMarkdownPreview,
                                    onToggle = { viewModel.toggleFormat() }
                                )
                            }

                            val displayContent = state.editedDescription.ifBlank { state.currentOutput }
                            if (state.isMarkdownPreview) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    MarkdownPreview(
                                        markdown = displayContent,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            } else {
                                OutlinedTextField(
                                    value = displayContent,
                                    onValueChange = { viewModel.updateEditedDescription(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 4,
                                    maxLines = 12,
                                    enabled = !state.isLoading
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.requestSaveToFavorites() },
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
                                    onClick = { viewModel.shareDescription(context) },
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
                }
            }
        }
    }
}
