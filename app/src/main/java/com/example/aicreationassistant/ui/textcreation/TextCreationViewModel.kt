package com.example.aicreationassistant.ui.textcreation

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.domain.model.ContentType
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.data.repository.DeepSeekRepository
import com.example.aicreationassistant.util.Constants
import com.example.aicreationassistant.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TextCreationState(
    val creationType: CreationType = CreationType.SOCIAL_MEDIA,
    val prompt: String = "",
    val generatedContent: String = "",
    val editedContent: String = "",
    val isMarkdownPreview: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedHistoryId: Long? = null,
    val isSaved: Boolean = false,
    val charCount: Int = 0
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

    fun updatePrompt(text: String) {
        if (text.length <= Constants.MAX_PROMPT_LENGTH) {
            _state.update { it.copy(prompt = text, charCount = text.length) }
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

    fun generateContent() {
        val currentPrompt = _state.value.prompt.trim()

        // Validate
        if (!networkMonitor.isOnline()) {
            _state.update { it.copy(error = "网络不可用") }
            return
        }
        if (currentPrompt.length < Constants.MIN_PROMPT_LENGTH) {
            _state.update { it.copy(error = "请输入至少${Constants.MIN_PROMPT_LENGTH}个字符") }
            return
        }

        val systemPrompt = when (creationType) {
            CreationType.SOCIAL_MEDIA -> Constants.SYSTEM_PROMPT_SOCIAL_MEDIA
            CreationType.PRODUCT_DESC -> Constants.SYSTEM_PROMPT_PRODUCT_DESC
            else -> Constants.SYSTEM_PROMPT_SOCIAL_MEDIA
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            deepSeekRepo.generateText(
                systemPrompt = systemPrompt,
                userMessage = currentPrompt
            ).onSuccess { content ->
                _state.update {
                    it.copy(
                        generatedContent = content,
                        editedContent = content,
                        isLoading = false
                    )
                }
                // 自动保存到历史（不阻塞主流程）
                try { autoSaveToHistory(content) } catch (_: Exception) {}
            }.onFailure { t ->
                val msg = when {
                    t is com.example.aicreationassistant.data.remote.model.NetworkException -> "网络不可用"
                    t is com.example.aicreationassistant.data.remote.model.ApiException -> t.message ?: "API错误"
                    t.message?.contains("timeout") == true -> "请求超时，请稍后重试"
                    else -> t.message ?: "生成失败，请重试"
                }
                _state.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    private suspend fun autoSaveToHistory(content: String) {
        val id = contentRepo.addHistory(
            content = content,
            contentType = ContentType.TEXT,
            creationType = creationType,
            originalPrompt = _state.value.prompt.trim(),
            title = creationType.displayName
        )
        _state.update { it.copy(savedHistoryId = id) }
    }

    fun saveToFavorites() {
        val content = _state.value.editedContent.ifBlank {
            _state.value.generatedContent
        }
        if (content.isBlank()) return

        viewModelScope.launch {
            contentRepo.addFavorite(
                content = content,
                contentType = ContentType.TEXT,
                creationType = creationType,
                originalPrompt = _state.value.prompt.trim(),
                title = creationType.displayName
            )
            _state.update { it.copy(isSaved = true) }
        }
    }

    fun shareText(context: Context) {
        val content = _state.value.editedContent.ifBlank {
            _state.value.generatedContent
        }
        if (content.isBlank()) return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享到"))
    }

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
