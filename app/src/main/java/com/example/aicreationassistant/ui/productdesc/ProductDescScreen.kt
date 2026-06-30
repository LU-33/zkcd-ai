package com.example.aicreationassistant.ui.productdesc

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aicreationassistant.AiCreationApp
import com.example.aicreationassistant.domain.model.OutputLanguage
import com.example.aicreationassistant.domain.model.TargetMarket
import com.example.aicreationassistant.domain.model.TargetPlatform
import com.example.aicreationassistant.ui.components.*
import com.example.aicreationassistant.util.getFileName
import java.io.File

private val AccentRed = Color(0xFFE53935)
private val AccentPurple = Color(0xFF6C63FF)
private val PageBg = Color(0xFFF6F7F8)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductDescScreen(
    onNavigateBack: () -> Unit
) {
    val app = AiCreationApp.instance
    val viewModel: ProductDescViewModel = viewModel(
        factory = ProductDescViewModelFactory(
            deepSeekRepo = app.serviceLocator.deepSeekRepository,
            qwenVLRepo = app.serviceLocator.qwenVLRepository,
            contentRepo = app.serviceLocator.contentRepository,
            networkMonitor = app.serviceLocator.networkMonitor
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 拍照 URI 缓存
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // 拍照
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { uri ->
                val fileName = uri.getFileName(context)
                viewModel.addImage(uri, fileName)
            }
        }
    }

    // 相册
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = it.getFileName(context)
            viewModel.addImage(it, fileName)
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

    // Save dialog
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
                TextButton(onClick = { viewModel.confirmSaveToFavorites() }) { Text("确认收藏") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveDialog() }) { Text("取消") }
            }
        )
    }

    // 设置选择弹窗
    if (state.showPlatformDialog) {
        SingleChoiceDialog(
            title = "选择目标平台",
            options = TargetPlatform.entries.map { it.label },
            selected = state.selectedPlatform.label,
            onSelect = { label ->
                val p = TargetPlatform.entries.first { it.label == label }
                viewModel.selectPlatform(p)
            },
            onDismiss = { viewModel.dismissPlatformDialog() }
        )
    }
    if (state.showMarketDialog) {
        SingleChoiceDialog(
            title = "选择目标市场",
            options = TargetMarket.entries.map { it.label },
            selected = state.selectedMarket.label,
            onSelect = { label ->
                val m = TargetMarket.entries.first { it.label == label }
                viewModel.selectMarket(m)
            },
            onDismiss = { viewModel.dismissMarketDialog() }
        )
    }
    if (state.showLanguageDialog) {
        SingleChoiceDialog(
            title = "选择输出语言",
            options = OutputLanguage.entries.map { it.label },
            selected = state.selectedLanguage.label,
            onSelect = { label ->
                val l = OutputLanguage.entries.first { it.label == label }
                viewModel.selectLanguage(l)
            },
            onDismiss = { viewModel.dismissLanguageDialog() }
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
                title = { Text("商品文案生成") },
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
            if (state.configExpanded) {
                // 配置展开时始终显示"一键生成"，方便修改后重新生成
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    tonalElevation = 4.dp
                ) {
                    Button(
                        onClick = { viewModel.sendMessage(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .height(52.dp),
                        enabled = !state.isLoading && state.sellingPoints.trim().isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed,
                            contentColor = Color.White,
                            disabledContainerColor = AccentRed.copy(alpha = 0.5f),
                            disabledContentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text("一键生成商品上架文案", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (state.hasGenerated) {
                ChatInputBar(
                    value = state.currentInput,
                    onValueChange = { viewModel.updateCurrentInput(it) },
                    onSend = { viewModel.sendMessage(context) },
                    enabled = !state.isLoading,
                    hint = "对结果不满意？告诉 AI 如何修改…"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PageBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== 配置区（可折叠） ====================
            AnimatedVisibility(visible = state.configExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // ===== §1 上传商品图片 =====
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "1、上传商品图片",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "提示",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            state.uploadedImages.forEachIndexed { index, uri ->
                                Box(modifier = Modifier.size(90.dp)) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(uri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = state.imageFileNames.getOrElse(index) { "图片" },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    // 删除按钮
                                    IconButton(
                                        onClick = { viewModel.removeImage(index) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .offset(x = 4.dp, y = (-4).dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.Black.copy(alpha = 0.5f)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "删除",
                                                modifier = Modifier.size(16.dp),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                            // 添加按钮
                            if (state.uploadedImages.size < 5) {
                                Surface(
                                    onClick = { viewModel.showImagePicker() },
                                    modifier = Modifier.size(90.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        width = 1.5.dp
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "添加",
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "添加图片",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ===== §2 生成设置 =====
                    SectionCard {
                        Text(
                            "2、生成设置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        SettingsRow(
                            label = "目标平台",
                            value = state.selectedPlatform.label,
                            onClick = { viewModel.showPlatformDialog() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(
                            label = "目标市场",
                            value = state.selectedMarket.label,
                            onClick = { viewModel.showMarketDialog() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(
                            label = "输出语言",
                            value = state.selectedLanguage.label,
                            onClick = { viewModel.showLanguageDialog() }
                        )
                    }

                    // ===== §3 商品卖点&要求 =====
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "3、商品卖点&要求",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = { viewModel.aiWriteSellingPoints(context) },
                                enabled = !state.isAiWriting && state.uploadedImages.isNotEmpty()
                            ) {
                                if (state.isAiWriting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("AI 分析中…")
                                } else {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("AI帮写")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.sellingPoints,
                            onValueChange = { viewModel.updateSellingPoints(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp),
                            placeholder = {
                                Text(
                                    "请输入商品卖点、规格、适用人群、功能特点等。\n\n" +
                                        "建议按以下格式填写（AI帮写也会生成此模板）：\n" +
                                        "商品名称：\n" +
                                        "商品特点：\n" +
                                        "规格参数：\n" +
                                        "适用人群：\n" +
                                        "使用场景："
                                )
                            },
                            minLines = 8,
                            maxLines = 14,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = PageBg,
                                focusedContainerColor = PageBg
                            )
                        )
                    }
                }
            }

            // ==================== 紧凑摘要条 ====================
            if (!state.configExpanded && state.hasGenerated) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📋")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${state.selectedPlatform.label} · ${state.selectedMarket.label} · ${state.selectedLanguage.label}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.expandConfig() }) {
                            Text("展开修改", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ==================== 对话区 ====================
            if (state.hasGenerated) {
                HorizontalDivider()

                state.conversation.forEach { turn ->
                    val isLastAssistant = turn == state.conversation.lastOrNull() && turn.role == "assistant"
                    ChatBubble(
                        turn = turn,
                        isLastAssistant = isLastAssistant,
                        onEditClick = if (isLastAssistant) {
                            { viewModel.updateEditedContent(turn.content) }
                        } else null
                    )
                }

                if (state.isLoading) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 正在创作…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // 操作栏
                if (state.currentOutput.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("当前结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            FormatToggle(isMarkdown = state.isMarkdownPreview, onToggle = { viewModel.toggleFormat() })
                        }
                        val displayContent = state.editedContent.ifBlank { state.currentOutput }
                        if (state.isMarkdownPreview) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                MarkdownPreview(markdown = displayContent, modifier = Modifier.padding(12.dp))
                            }
                        } else {
                            OutlinedTextField(
                                value = displayContent,
                                onValueChange = { viewModel.updateEditedContent(it) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                maxLines = 12
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
                                onClick = { viewModel.shareText(context) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("分享")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ===== 图片选择 BottomSheet =====
        if (state.showImagePicker) {
            ModalBottomSheet(onDismissRequest = { viewModel.dismissImagePicker() }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "添加图片",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ListItem(
                        headlineContent = { Text("拍照") },
                        leadingContent = { Icon(Icons.Default.Camera, contentDescription = null) },
                        modifier = Modifier.clickable {
                            viewModel.dismissImagePicker()
                            val file = File(context.cacheDir, "camera/${System.currentTimeMillis()}.jpg")
                            file.parentFile?.mkdirs()
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("相册") },
                        leadingContent = { Icon(Icons.Default.Photo, contentDescription = null) },
                        modifier = Modifier.clickable {
                            viewModel.dismissImagePicker()
                            galleryLauncher.launch("image/*")
                        }
                    )
                    ListItem(
                        headlineContent = { Text("取消") },
                        leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.dismissImagePicker() }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ==================== 子组件 ====================

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

