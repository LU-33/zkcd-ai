package com.example.aicreationassistant.ui.imagedesc

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.domain.model.ContentType
import com.example.aicreationassistant.domain.model.ConversationTurn
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.data.repository.DeepSeekRepository
import com.example.aicreationassistant.util.Constants
import com.example.aicreationassistant.util.NetworkMonitor
import com.example.aicreationassistant.util.extractTitle
import com.example.aicreationassistant.util.getImageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageDescState(
    val selectedImageUri: Uri? = null,
    val imageFileName: String = "",
    /** 图片元数据（首次提取后缓存，后续对话复用） */
    val imageInfo: String = "",
    /** 底部输入栏文本 */
    val currentInput: String = "",
    /** 最新一条 AI 描述 */
    val currentOutput: String = "",
    /** 用户手动编辑后的内容 */
    val editedDescription: String = "",
    /** 完整对话记录 */
    val conversation: List<ConversationTurn> = emptyList(),
    val isMarkdownPreview: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedHistoryId: Long? = null,
    val isSaved: Boolean = false,
    /** 首次生成成功后置 true */
    val hasGenerated: Boolean = false,
    /** 是否显示更换图片确认对话框 */
    val showChangeImageDialog: Boolean = false,
    /** 收藏对话框 */
    val showSaveDialog: Boolean = false,
    val saveDialogTitle: String = ""
)

class ImageDescViewModel(
    private val deepSeekRepo: DeepSeekRepository,
    private val contentRepo: ContentRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(ImageDescState())
    val state: StateFlow<ImageDescState> = _state.asStateFlow()

    // ==================== 图片选择 ====================

    fun onImageSelected(uri: Uri, fileName: String) {
        _state.update {
            it.copy(
                selectedImageUri = uri,
                imageFileName = fileName,
                imageInfo = "",  // 重置，下次生成时重新提取
                hasGenerated = false
            )
        }
    }

    /** 对话模式下更换图片 — 弹出确认对话框 */
    fun requestChangeImage() {
        _state.update { it.copy(showChangeImageDialog = true) }
    }

    fun dismissChangeImageDialog() {
        _state.update { it.copy(showChangeImageDialog = false) }
    }

    fun confirmChangeImage(uri: Uri, fileName: String) {
        _state.update {
            it.copy(
                selectedImageUri = uri,
                imageFileName = fileName,
                imageInfo = "",
                conversation = emptyList(),
                currentInput = "",
                currentOutput = "",
                editedDescription = "",
                hasGenerated = false,
                showChangeImageDialog = false
            )
        }
    }

    // ==================== 输入操作 ====================

    fun updateCurrentInput(text: String) {
        _state.update { it.copy(currentInput = text) }
    }

    fun updateEditedDescription(text: String) {
        _state.update { it.copy(editedDescription = text) }
    }

    fun toggleFormat() {
        _state.update { it.copy(isMarkdownPreview = !it.isMarkdownPreview) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // ==================== 核心：发送消息 ====================

    fun sendMessage(context: Context) {
        val uri = _state.value.selectedImageUri
        if (uri == null) {
            _state.update { it.copy(error = "请先选择图片") }
            return
        }
        if (!networkMonitor.isOnline()) {
            _state.update { it.copy(error = "网络不可用") }
            return
        }

        val input = _state.value.currentInput.trim()

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // 首次提取图片元数据并缓存
                val imageInfo = if (_state.value.imageInfo.isBlank()) {
                    val info = uri.getImageInfo(context)
                    _state.update { it.copy(imageInfo = info) }
                    info
                } else {
                    _state.value.imageInfo
                }

                val previousConversation = _state.value.conversation
                val displayInput = input.ifBlank { "请描述这张图片" }
                val userTurn = ConversationTurn(role = "user", content = displayInput)

                // 乐观更新
                _state.update {
                    it.copy(conversation = previousConversation + userTurn, currentInput = "")
                }

                deepSeekRepo.generateImageConversation(
                    systemPrompt = Constants.SYSTEM_PROMPT_IMAGE_DESC,
                    imageInfo = imageInfo,
                    history = previousConversation,
                    newUserMessage = displayInput
                ).onSuccess { description ->
                    val assistantTurn = ConversationTurn(role = "assistant", content = description)
                    _state.update {
                        it.copy(
                            conversation = it.conversation + assistantTurn,
                            currentOutput = description,
                            editedDescription = description,
                            isLoading = false,
                            hasGenerated = true
                        )
                    }
                    try { autoSaveToHistory(description) } catch (_: Exception) {}
                }.onFailure { t ->
                    val msg = when {
                        t is com.example.aicreationassistant.data.remote.model.NetworkException -> "网络不可用"
                        t is com.example.aicreationassistant.data.remote.model.ApiException -> t.message ?: "API错误"
                        t.message?.contains("timeout") == true -> "请求超时，请稍后重试"
                        t is retrofit2.HttpException -> "API错误 (${t.code()})：${t.message()}"
                        else -> t.message ?: "生成描述失败"
                    }
                    _state.update { it.copy(isLoading = false, error = msg) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "图片处理失败: ${e.message}")
                }
            }
        }
    }

    // ==================== 持久化 ====================

    private suspend fun autoSaveToHistory(content: String) {
        val autoTitle = content.extractTitle().ifBlank { "图片描述" }
        val firstUserMsg = _state.value.conversation.firstOrNull { it.role == "user" }?.content
        val id = contentRepo.addHistory(
            content = content,
            contentType = ContentType.TEXT,
            creationType = CreationType.IMAGE_DESC,
            originalPrompt = firstUserMsg ?: "图片描述",
            title = autoTitle,
            imageUri = _state.value.selectedImageUri?.toString()
        )
        _state.update { it.copy(savedHistoryId = id) }
    }

    /** 打开收藏对话框 */
    fun requestSaveToFavorites() {
        val content = _state.value.editedDescription.ifBlank {
            _state.value.currentOutput
        }
        if (content.isBlank()) return
        val autoTitle = content.extractTitle().ifBlank { "图片描述" }
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
        val title = _state.value.saveDialogTitle.trim().ifBlank { "图片描述" }
        val content = _state.value.editedDescription.ifBlank {
            _state.value.currentOutput
        }
        if (content.isBlank()) return

        viewModelScope.launch {
            contentRepo.addFavorite(
                content = content,
                contentType = ContentType.TEXT,
                creationType = CreationType.IMAGE_DESC,
                originalPrompt = "图片描述",
                title = title,
                imageUri = _state.value.selectedImageUri?.toString()
            )
            _state.update { it.copy(isSaved = true, showSaveDialog = false) }
        }
    }

    // ==================== 分享 ====================

    fun shareDescription(context: Context) {
        val content = _state.value.editedDescription.ifBlank {
            _state.value.currentOutput
        }
        if (content.isBlank()) return

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, content)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "分享到"))
    }

    fun reset() {
        _state.update { ImageDescState() }
    }
}
