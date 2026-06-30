package com.example.aicreationassistant.ui.textcreation

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicreationassistant.AiCreationApp
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.domain.model.Platform
import com.example.aicreationassistant.domain.model.SalesChannel
import com.example.aicreationassistant.domain.model.getHotTagsForSocialMedia
import com.example.aicreationassistant.domain.model.getHotTagsForProductDesc
import com.example.aicreationassistant.domain.model.getToneOptionsForSocialMedia
import com.example.aicreationassistant.domain.model.getToneOptionsForProductDesc
import com.example.aicreationassistant.domain.model.getStyleOptionsForSocialMedia
import com.example.aicreationassistant.domain.model.getStyleOptionsForProductDesc
import com.example.aicreationassistant.ui.components.*

private val AccentRed = Color(0xFFE53935)
private val AccentPurple = Color(0xFF6C63FF)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    val isSocial = state.creationType == CreationType.SOCIAL_MEDIA
    val hotTags = remember(isSocial) {
        if (isSocial) getHotTagsForSocialMedia() else getHotTagsForProductDesc()
    }
    val toneOptions = remember(isSocial) {
        if (isSocial) getToneOptionsForSocialMedia() else getToneOptionsForProductDesc()
    }
    val styleOptions = remember(isSocial) {
        if (isSocial) getStyleOptionsForSocialMedia() else getStyleOptionsForProductDesc()
    }

    // Error dialog
    if (state.error != null) {
        ErrorDialog(
            message = state.error!!,
            onDismiss = { viewModel.clearError() },
            onRetry = {
                viewModel.clearError()
                viewModel.sendMessage()
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
        },
        bottomBar = {
            if (state.hasGenerated) {
                ChatInputBar(
                    value = state.currentInput,
                    onValueChange = { viewModel.updateCurrentInput(it) },
                    onSend = { viewModel.sendMessage() },
                    enabled = !state.isLoading,
                    hint = "对结果不满意？告诉 AI 如何修改…"
                )
            } else if (state.configExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    tonalElevation = 4.dp
                ) {
                    Button(
                        onClick = { viewModel.sendMessage() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .height(52.dp),
                        enabled = !state.isLoading && state.currentInput.trim().length >= 2,
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
                        Text(
                            text = if (isSocial) "一键生成文案" else "一键生成描述",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ==================== 配置区（可折叠） ====================
            AnimatedVisibility(visible = state.configExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // ===== 第一部分：热门标签 =====
                    SectionHeader(
                        title = if (isSocial) "你想生成关于什么文案？" else "你想描述什么商品？"
                    )
                    val displayTags = if (state.tagsExpanded) hotTags else hotTags.take(8)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        displayTags.forEach { tag ->
                            TagChip(
                                label = tag.label,
                                onClick = { viewModel.fillFromTag(tag.keyword) }
                            )
                        }
                    }
                    if (hotTags.size > 8) {
                        TextButton(
                            onClick = { viewModel.toggleTagsExpanded() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                if (state.tagsExpanded) "收起 ▲" else "展开 ▼",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ===== 第二部分：输入框 =====
                    OutlinedTextField(
                        value = state.currentInput,
                        onValueChange = { viewModel.updateCurrentInput(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        placeholder = {
                            Text(
                                if (isSocial) "请输入关键词，例如：旅行"
                                else "请输入商品信息，例如：无线蓝牙耳机"
                            )
                        },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        supportingText = {
                            Text(
                                "${state.charCount}/${com.example.aicreationassistant.util.Constants.MAX_PROMPT_LENGTH}",
                                color = if (state.charCount >= com.example.aicreationassistant.util.Constants.MAX_PROMPT_LENGTH)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        enabled = !state.isLoading
                    )

                    // ===== 第三部分：平台 / 渠道 =====
                    SectionHeader(
                        title = if (isSocial) "选择发布平台" else "选择销售渠道"
                    )
                    if (isSocial) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Platform.entries.forEach { platform ->
                                PlatformChip(
                                    label = platform.label,
                                    selected = state.selectedPlatform == platform,
                                    onClick = { viewModel.selectPlatform(platform) }
                                )
                            }
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SalesChannel.entries.forEach { channel ->
                                PlatformChip(
                                    label = channel.label,
                                    selected = state.selectedSalesChannel == channel,
                                    onClick = { viewModel.selectSalesChannel(channel) }
                                )
                            }
                        }
                    }

                    // ===== 第四部分：基础语气 =====
                    SectionHeader(title = "基础语气")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        toneOptions.forEach { tone ->
                            SelectableChip(
                                label = tone.label,
                                selected = state.selectedTone?.key == tone.key,
                                onClick = { viewModel.selectTone(tone) }
                            )
                        }
                    }

                    // ===== 第五部分：特色风格 =====
                    SectionHeader(title = "特色风格")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        styleOptions.forEach { style ->
                            SelectableChip(
                                label = style.label,
                                selected = state.selectedStyle?.key == style.key,
                                onClick = { viewModel.selectStyle(style) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ==================== 紧凑摘要条（配置折叠后） ====================
            if (!state.configExpanded && state.hasGenerated) {
                ConfigSummaryBar(
                    platform = state.selectedPlatform?.label,
                    channel = state.selectedSalesChannel?.label,
                    tone = state.selectedTone?.label,
                    style = state.selectedStyle?.label,
                    onExpand = { viewModel.expandConfig() }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ==================== 对话区 ====================
            if (state.hasGenerated) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 聊天气泡
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

                // 加载中指示器
                if (state.isLoading) {
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
                            "AI 正在思考…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 操作栏：格式切换 + 内容编辑/预览 + 收藏/分享
                if (state.currentOutput.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
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
                        val displayContent = state.editedContent.ifBlank { state.currentOutput }
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
                                onValueChange = { viewModel.updateEditedContent(it) },
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

                // 留白给底部输入栏
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ==================== 子组件 ====================

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

/** 配置折叠后的紧凑摘要条 */
@Composable
private fun ConfigSummaryBar(
    platform: String?,
    channel: String?,
    tone: String?,
    style: String?,
    onExpand: () -> Unit
) {
    val parts = listOfNotNull(platform, channel, tone, style)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = parts.joinToString(" · ").ifBlank { "未选择选项" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onExpand) {
                Text(
                    "展开修改",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 热门标签 — 圆角 Button 风格，紧凑尺寸实现一行 3 个 */
@Composable
private fun TagChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** 平台/渠道选择 Chip — 选中红色描边 */
@Composable
private fun PlatformChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) AccentRed.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, AccentRed)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AccentRed else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** 语气/风格单选 Chip — 选中主题色背景+白字 */
@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) AccentPurple else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
