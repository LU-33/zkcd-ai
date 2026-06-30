package com.example.aicreationassistant.ui.productdesc

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.data.repository.DeepSeekRepository
import com.example.aicreationassistant.data.repository.QwenVLRepository
import com.example.aicreationassistant.domain.model.ContentType
import com.example.aicreationassistant.domain.model.ConversationTurn
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.domain.model.OutputLanguage
import com.example.aicreationassistant.domain.model.TargetMarket
import com.example.aicreationassistant.domain.model.TargetPlatform
import com.example.aicreationassistant.util.Constants
import com.example.aicreationassistant.util.NetworkMonitor
import com.example.aicreationassistant.util.extractTitle
import com.example.aicreationassistant.util.getFileName
import com.example.aicreationassistant.util.getImageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductDescState(
    // 图片
    val uploadedImages: List<Uri> = emptyList(),
    val imageFileNames: List<String> = emptyList(),
    val showImagePicker: Boolean = false,
    // 设置
    val selectedPlatform: TargetPlatform = TargetPlatform.TAOBAO,
    val selectedMarket: TargetMarket = TargetMarket.CHINA,
    val selectedLanguage: OutputLanguage = OutputLanguage.CHINESE,
    // 设置弹窗
    val showPlatformDialog: Boolean = false,
    val showMarketDialog: Boolean = false,
    val showLanguageDialog: Boolean = false,
    // 卖点
    val sellingPoints: String = "",
    val isAiWriting: Boolean = false,
    // follow-up 输入
    val currentInput: String = "",
    // 生成
    val currentOutput: String = "",
    val editedContent: String = "",
    val conversation: List<ConversationTurn> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasGenerated: Boolean = false,
    val configExpanded: Boolean = true,
    val isMarkdownPreview: Boolean = false,
    // 保存
    val savedHistoryId: Long? = null,
    val isSaved: Boolean = false,
    val showSaveDialog: Boolean = false,
    val saveDialogTitle: String = ""
)

class ProductDescViewModel(
    private val deepSeekRepo: DeepSeekRepository,
    private val qwenVLRepo: QwenVLRepository,
    private val contentRepo: ContentRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(ProductDescState())
    val state: StateFlow<ProductDescState> = _state.asStateFlow()

    // ==================== 图片管理 ====================

    fun addImage(uri: Uri, fileName: String) {
        val images = _state.value.uploadedImages
        if (images.size >= 5) return
        _state.update {
            it.copy(
                uploadedImages = images + uri,
                imageFileNames = it.imageFileNames + fileName
            )
        }
    }

    fun removeImage(index: Int) {
        _state.update {
            it.copy(
                uploadedImages = it.uploadedImages.filterIndexed { i, _ -> i != index },
                imageFileNames = it.imageFileNames.filterIndexed { i, _ -> i != index }
            )
        }
    }

    fun showImagePicker() {
        _state.update { it.copy(showImagePicker = true) }
    }

    fun dismissImagePicker() {
        _state.update { it.copy(showImagePicker = false) }
    }

    // ==================== 设置选择 ====================

    fun selectPlatform(platform: TargetPlatform) {
        _state.update { it.copy(selectedPlatform = platform, showPlatformDialog = false) }
    }

    fun selectMarket(market: TargetMarket) {
        _state.update { it.copy(selectedMarket = market, showMarketDialog = false) }
    }

    fun selectLanguage(language: OutputLanguage) {
        _state.update { it.copy(selectedLanguage = language, showLanguageDialog = false) }
    }

    fun showPlatformDialog() { _state.update { it.copy(showPlatformDialog = true) } }
    fun dismissPlatformDialog() { _state.update { it.copy(showPlatformDialog = false) } }
    fun showMarketDialog() { _state.update { it.copy(showMarketDialog = true) } }
    fun dismissMarketDialog() { _state.update { it.copy(showMarketDialog = false) } }
    fun showLanguageDialog() { _state.update { it.copy(showLanguageDialog = true) } }
    fun dismissLanguageDialog() { _state.update { it.copy(showLanguageDialog = false) } }

    // ==================== 卖点输入 ====================

    fun updateSellingPoints(text: String) {
        if (text.length <= Constants.MAX_PROMPT_LENGTH) {
            _state.update { it.copy(sellingPoints = text) }
        }
    }

    fun updateCurrentInput(text: String) {
        if (text.length <= Constants.MAX_PROMPT_LENGTH) {
            _state.update { it.copy(currentInput = text) }
        }
    }

    /** AI帮写 — Qwen VL 分析图片 → DeepSeek 生成卖点草稿 */
    fun aiWriteSellingPoints(context: Context) {
        val images = _state.value.uploadedImages
        if (images.isEmpty()) {
            _state.update { it.copy(error = "请先上传商品图片") }
            return
        }
        if (!networkMonitor.isOnline()) {
            _state.update { it.copy(error = "网络不可用") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isAiWriting = true, error = null) }
            try {
                // 阶段1：Qwen VL 分析图片
                _state.update { it.copy(error = null) }
                val imageDescriptions = qwenVLRepo.describeImages(context, images).getOrElse {
                    // 如果 Qwen VL 失败，降级为元数据
                    images.map { uri -> uri.getImageInfo(context) }.joinToString("\n---\n") { it }
                }

                // 阶段2：DeepSeek 基于图片描述生成卖点
                deepSeekRepo.generateConversation(
                    systemPrompt = buildString {
                        append("你是电商运营专家。请基于图片AI视觉分析，生成商品卖点草稿供用户修改。")
                        append("\n\n输出要求：")
                        append("\n1. 商品名称：从图片分析中提取，如果无法确定则写「待填写」")
                        append("\n2. 核心卖点（3-5条）：每条一句话，突出功能和体验优势")
                        append("\n3. 规格参数：列出能推断的参数（尺寸/重量/材质/颜色等）")
                        append("\n4. 适用人群/场景：推断目标用户和使用场景")
                        append("\n\n注意：只输出从图片分析中能确定或合理推断的内容，不确定的标注「待补充」。")
                    },
                    history = emptyList(),
                    newUserMessage = "图片分析结果：\n\n$imageDescriptions\n\n请生成卖点草稿。"
                ).onSuccess { result ->
                    _state.update {
                        it.copy(
                            sellingPoints = if (it.sellingPoints.isBlank()) result
                            else it.sellingPoints + "\n" + result,
                            isAiWriting = false
                        )
                    }
                }.onFailure { t ->
                    _state.update { it.copy(isAiWriting = false, error = "AI帮写失败: ${t.message}") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isAiWriting = false, error = "AI帮写失败: ${e.message}") }
            }
        }
    }

    // ==================== 输入编辑 ====================

    fun updateEditedContent(text: String) {
        _state.update { it.copy(editedContent = text) }
    }

    fun toggleFormat() {
        _state.update { it.copy(isMarkdownPreview = !it.isMarkdownPreview) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun expandConfig() {
        _state.update { it.copy(configExpanded = true) }
    }

    // ==================== 生成 ====================

    fun sendMessage(context: Context? = null) {
        val isFollowUp = _state.value.hasGenerated
        val messageText = if (isFollowUp) _state.value.currentInput.trim()
            else _state.value.sellingPoints.trim()

        if (messageText.isBlank()) {
            _state.update { it.copy(error = if (isFollowUp) "请输入修改要求" else "请输入商品卖点和要求") }
            return
        }
        if (!networkMonitor.isOnline()) {
            _state.update { it.copy(error = "网络不可用") }
            return
        }

        val platform = _state.value.selectedPlatform
        val market = _state.value.selectedMarket
        val language = _state.value.selectedLanguage
        val images = _state.value.uploadedImages
        val previousConversation = _state.value.conversation

        val userTurn = ConversationTurn(role = "user", content = messageText)
        _state.update {
            it.copy(
                conversation = previousConversation + userTurn,
                currentInput = "",
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            // 首次生成 + 有图片 → Qwen VL 分析（降级：元数据）
            var imageContext: String? = null
            if (!isFollowUp && images.isNotEmpty() && context != null) {
                imageContext = try {
                    qwenVLRepo.describeImages(context, images).getOrNull()
                } catch (_: Exception) { null }
                if (imageContext.isNullOrBlank()) {
                    imageContext = images.mapIndexed { i, uri ->
                        val name = _state.value.imageFileNames.getOrElse(i) { "未知" }
                        "图片${i + 1}：${name.substringBeforeLast(".")}"
                    }.joinToString("\n")
                }
            }

            val systemPrompt = buildString {
                append(Constants.SYSTEM_PROMPT_PRODUCT_DESC)
                append("\n\n目标平台：${platform.promptHint}")
                append("\n目标市场：${market.promptHint}")
                append("\n输出语言：${language.promptHint}")
                if (!imageContext.isNullOrBlank()) {
                    append("\n\n=== 商品图片 AI 视觉分析 ===\n$imageContext")
                }
                append("\n\n---")
                append("\n请严格按照上述「输出结构」，结合【图片AI分析】和【用户卖点要求】，为「${platform.label}」平台撰写完整的商品上架文案。")
                append("\n注意：如果用户卖点中已包含商品名称/规格等信息，优先使用用户提供的信息。图片分析仅作为补充参考。")
            }

            deepSeekRepo.generateConversation(
                systemPrompt = systemPrompt,
                history = previousConversation,
                newUserMessage = messageText
            ).onSuccess { content ->
                val assistantTurn = ConversationTurn(role = "assistant", content = content)
                _state.update {
                    it.copy(
                        conversation = it.conversation + assistantTurn,
                        currentOutput = content,
                        editedContent = content,
                        isLoading = false,
                        hasGenerated = true,
                        configExpanded = false
                    )
                }
                try { autoSaveToHistory(content) } catch (_: Exception) {}
            }.onFailure { t ->
                val msg = when {
                    t is com.example.aicreationassistant.data.remote.model.NetworkException -> "网络不可用"
                    t is com.example.aicreationassistant.data.remote.model.ApiException -> t.message ?: "API错误"
                    t.message?.contains("timeout") == true -> "请求超时"
                    else -> t.message ?: "生成失败"
                }
                _state.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    // ==================== 持久化 ====================

    private suspend fun autoSaveToHistory(content: String) {
        val title = content.extractTitle().ifBlank { "商品文案" }
        val imageUris = _state.value.uploadedImages.joinToString(",") { it.toString() }
        val id = contentRepo.addHistory(
            content = content,
            contentType = ContentType.TEXT,
            creationType = CreationType.PRODUCT_DESC,
            originalPrompt = _state.value.sellingPoints.trim(),
            title = title,
            imageUri = imageUris.ifBlank { null }
        )
        _state.update { it.copy(savedHistoryId = id) }
    }

    fun requestSaveToFavorites() {
        val content = _state.value.editedContent.ifBlank { _state.value.currentOutput }
        if (content.isBlank()) return
        val autoTitle = content.extractTitle().ifBlank { "商品文案" }
        _state.update { it.copy(showSaveDialog = true, saveDialogTitle = autoTitle) }
    }

    fun updateSaveDialogTitle(text: String) {
        if (text.length <= 30) _state.update { it.copy(saveDialogTitle = text) }
    }

    fun dismissSaveDialog() {
        _state.update { it.copy(showSaveDialog = false) }
    }

    fun confirmSaveToFavorites() {
        val title = _state.value.saveDialogTitle.trim().ifBlank { "商品文案" }
        val content = _state.value.editedContent.ifBlank { _state.value.currentOutput }
        if (content.isBlank()) return
        viewModelScope.launch {
            val imageUris = _state.value.uploadedImages.joinToString(",") { it.toString() }
            contentRepo.addFavorite(
                content = content,
                contentType = ContentType.TEXT,
                creationType = CreationType.PRODUCT_DESC,
                originalPrompt = _state.value.sellingPoints.trim(),
                title = title,
                imageUri = imageUris.ifBlank { null }
            )
            _state.update { it.copy(isSaved = true, showSaveDialog = false) }
        }
    }

    fun shareText(context: Context) {
        val content = _state.value.editedContent.ifBlank { _state.value.currentOutput }
        if (content.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "分享到"))
    }
}
