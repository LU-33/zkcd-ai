package com.example.aicreationassistant.ui.textcreation

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.domain.model.ContentType
import com.example.aicreationassistant.domain.model.ConversationTurn
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.domain.model.Platform
import com.example.aicreationassistant.domain.model.SalesChannel
import com.example.aicreationassistant.domain.model.ToneOption
import com.example.aicreationassistant.domain.model.StyleOption
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.data.repository.DeepSeekRepository
import com.example.aicreationassistant.util.Constants
import com.example.aicreationassistant.util.NetworkMonitor
import com.example.aicreationassistant.util.extractTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TextCreationState(
    val creationType: CreationType = CreationType.SOCIAL_MEDIA,
    /** 底部输入栏文本（初始 prompt / follow-up 共用） */
    val currentInput: String = "",
    /** 最新一条 AI 响应 */
    val currentOutput: String = "",
    /** 用户手动编辑后的内容（与 FormatToggle 联动） */
    val editedContent: String = "",
    /** 完整对话记录（user + assistant 交替） */
    val conversation: List<ConversationTurn> = emptyList(),
    val isMarkdownPreview: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedHistoryId: Long? = null,
    val isSaved: Boolean = false,
    val charCount: Int = 0,
    /** 首次生成成功后置 true，驱动 UI 从"初始输入"切换到"对话模式" */
    val hasGenerated: Boolean = false,
    /** 收藏对话框 */
    val showSaveDialog: Boolean = false,
    val saveDialogTitle: String = "",
    /** 场景化选项 */
    val selectedPlatform: Platform? = null,
    val selectedSalesChannel: SalesChannel? = null,
    val selectedTone: ToneOption? = null,
    val selectedStyle: StyleOption? = null,
    val tagsExpanded: Boolean = false,
    /** 配置区展开/折叠 — 生成后自动折叠为摘要条 */
    val configExpanded: Boolean = true
)

class TextCreationViewModel(
    private val creationTypeKey: String,
    private val deepSeekRepo: DeepSeekRepository,
    private val contentRepo: ContentRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val creationType = CreationType.entries.firstOrNull { it.key == creationTypeKey }
        ?: CreationType.SOCIAL_MEDIA

    private val _state = MutableStateFlow(TextCreationState(creationType = creationType))
    val state: StateFlow<TextCreationState> = _state.asStateFlow()

    // ==================== 输入操作 ====================

    fun updateCurrentInput(text: String) {
        if (text.length <= Constants.MAX_PROMPT_LENGTH) {
            _state.update { it.copy(currentInput = text, charCount = text.length) }
        }
    }

    fun updateEditedContent(text: String) {
        _state.update { it.copy(editedContent = text) }
    }

    fun toggleFormat() {
        _state.update { it.copy(isMarkdownPreview = !it.isMarkdownPreview) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // ==================== 场景化选项操作 ====================

    fun selectPlatform(platform: Platform) {
        _state.update { it.copy(selectedPlatform = if (it.selectedPlatform == platform) null else platform) }
    }

    fun selectSalesChannel(channel: SalesChannel) {
        _state.update { it.copy(selectedSalesChannel = if (it.selectedSalesChannel == channel) null else channel) }
    }

    fun selectTone(tone: ToneOption) {
        _state.update { it.copy(selectedTone = if (it.selectedTone?.key == tone.key) null else tone) }
    }

    fun selectStyle(style: StyleOption) {
        _state.update { it.copy(selectedStyle = if (it.selectedStyle?.key == style.key) null else style) }
    }

    fun toggleTagsExpanded() {
        _state.update { it.copy(tagsExpanded = !it.tagsExpanded) }
    }

    fun expandConfig() {
        _state.update { it.copy(configExpanded = true) }
    }

    fun collapseConfig() {
        _state.update { it.copy(configExpanded = false) }
    }

    /** 点击热门标签 — 填充到输入框 */
    fun fillFromTag(keyword: String) {
        _state.update { it.copy(currentInput = keyword, charCount = keyword.length) }
    }

    // ==================== 核心：发送消息（首次生成 & 后续 follow-up） ====================

    fun sendMessage() {
        val input = _state.value.currentInput.trim()

        // 校验
        if (!networkMonitor.isOnline()) {
            _state.update { it.copy(error = "网络不可用") }
            return
        }
        if (input.length < Constants.MIN_PROMPT_LENGTH) {
            _state.update { it.copy(error = "请输入至少${Constants.MIN_PROMPT_LENGTH}个字符") }
            return
        }

        val basePrompt = when (creationType) {
            CreationType.SOCIAL_MEDIA -> Constants.SYSTEM_PROMPT_SOCIAL_MEDIA
            CreationType.PRODUCT_DESC -> Constants.SYSTEM_PROMPT_PRODUCT_DESC
            else -> Constants.SYSTEM_PROMPT_SOCIAL_MEDIA
        }
        // 根据用户选项增强 system prompt
        val systemPrompt = buildString {
            append(basePrompt)
            val p = _state.value.selectedPlatform
            val c = _state.value.selectedSalesChannel
            val t = _state.value.selectedTone
            val s = _state.value.selectedStyle
            if (p != null) append("\n\n发布平台：${p.promptHint}")
            if (c != null) append("\n\n销售渠道：${c.promptHint}")
            if (t != null) append("\n\n语气要求：${t.promptHint}")
            if (s != null) append("\n\n风格要求：${s.promptHint}")
        }

        val previousConversation = _state.value.conversation
        val userTurn = ConversationTurn(role = "user", content = input)

        // 乐观更新：用户消息立即显示
        _state.update {
            it.copy(
                conversation = previousConversation + userTurn,
                currentInput = "",
                charCount = 0,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            deepSeekRepo.generateConversation(
                systemPrompt = systemPrompt,
                history = previousConversation,
                newUserMessage = input
            ).onSuccess { content ->
                val assistantTurn = ConversationTurn(role = "assistant", content = content)
                _state.update {
                    it.copy(
                        conversation = it.conversation + assistantTurn,
                        currentOutput = content,
                        editedContent = content,
                        isLoading = false,
                        hasGenerated = true,
                        configExpanded = false  // 生成后自动折叠配置区
                    )
                }
                // 每次 AI 响应都自动保存到历史（保存最新一轮）
                try { autoSaveToHistory(content) } catch (_: Exception) {}
            }.onFailure { t ->
                val msg = when {
                    t is com.example.aicreationassistant.data.remote.model.NetworkException -> "网络不可用"
                    t is com.example.aicreationassistant.data.remote.model.ApiException -> t.message ?: "API错误"
                    t.message?.contains("timeout") == true -> "请求超时，请稍后重试"
                    else -> t.message ?: "生成失败，请重试"
                }
                // 保留用户消息，仅清除 loading 状态
                _state.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    // ==================== 持久化 ====================

    private suspend fun autoSaveToHistory(content: String) {
        val autoTitle = content.extractTitle().ifBlank { creationType.displayName }
        val id = contentRepo.addHistory(
            content = content,
            contentType = ContentType.TEXT,
            creationType = creationType,
            originalPrompt = _state.value.conversation.firstOrNull { it.role == "user" }?.content
                ?: creationType.displayName,
            title = autoTitle
        )
        _state.update { it.copy(savedHistoryId = id) }
    }

    /** 打开收藏对话框（标题预填自动提取值） */
    fun requestSaveToFavorites() {
        val content = _state.value.editedContent.ifBlank {
            _state.value.currentOutput
        }
        if (content.isBlank()) return
        val autoTitle = content.extractTitle().ifBlank { creationType.displayName }
        _state.update { it.copy(showSaveDialog = true, saveDialogTitle = autoTitle) }
    }

    fun updateSaveDialogTitle(text: String) {
        if (text.length <= 30) {
            _state.update { it.copy(saveDialogTitle = text) }
        }
    }

    fun dismissSaveDialog() {
        _state.update { it.copy(showSaveDialog = false) }
    }

    fun confirmSaveToFavorites() {
        val title = _state.value.saveDialogTitle.trim().ifBlank {
            creationType.displayName
        }
        val content = _state.value.editedContent.ifBlank {
            _state.value.currentOutput
        }
        if (content.isBlank()) return

        viewModelScope.launch {
            contentRepo.addFavorite(
                content = content,
                contentType = ContentType.TEXT,
                creationType = creationType,
                originalPrompt = _state.value.conversation.firstOrNull { it.role == "user" }?.content
                    ?: creationType.displayName,
                title = title
            )
            _state.update { it.copy(isSaved = true, showSaveDialog = false) }
        }
    }

    fun shareText(context: Context) {
        val content = _state.value.editedContent.ifBlank {
            _state.value.currentOutput
        }
        if (content.isBlank()) return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享到"))
    }

    // ==================== Factory ====================

    companion object {
        fun factory(
            creationTypeKey: String,
            deepSeekRepo: DeepSeekRepository,
            contentRepo: ContentRepository,
            networkMonitor: NetworkMonitor
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TextCreationViewModel(
                    creationTypeKey,
                    deepSeekRepo,
                    contentRepo,
                    networkMonitor
                ) as T
            }
        }
    }
}
