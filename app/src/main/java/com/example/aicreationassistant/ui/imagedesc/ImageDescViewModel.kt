package com.example.aicreationassistant.ui.imagedesc

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.domain.model.ContentType
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.data.repository.DeepSeekRepository
import com.example.aicreationassistant.util.Constants
import com.example.aicreationassistant.util.NetworkMonitor
import com.example.aicreationassistant.util.getImageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageDescState(
    val selectedImageUri: Uri? = null,
    val imageFileName: String = "",
    val optionalPrompt: String = "",
    val generatedDescription: String = "",
    val editedDescription: String = "",
    val isMarkdownPreview: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedHistoryId: Long? = null,
    val isSaved: Boolean = false
)

class ImageDescViewModel(
    private val deepSeekRepo: DeepSeekRepository,
    private val contentRepo: ContentRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(ImageDescState())
    val state: StateFlow<ImageDescState> = _state.asStateFlow()

    fun onImageSelected(uri: Uri, fileName: String) {
        _state.update {
            it.copy(selectedImageUri = uri, imageFileName = fileName)
        }
    }

    fun updateOptionalPrompt(text: String) {
        _state.update { it.copy(optionalPrompt = text) }
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

    fun generateDescription(context: Context) {
        val uri = _state.value.selectedImageUri
        if (uri == null) {
            _state.update { it.copy(error = "请先选择图片") }
            return
        }
        if (!networkMonitor.isOnline()) {
            _state.update { it.copy(error = "网络不可用") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // 两段式：本地提取图片元数据 → DeepSeek Chat 生成描述
                val imageInfo = uri.getImageInfo(context)

                deepSeekRepo.generateImageDescription(
                    systemPrompt = com.example.aicreationassistant.util.Constants.SYSTEM_PROMPT_IMAGE_DESC,
                    imageInfo = imageInfo,
                    userMessage = _state.value.optionalPrompt.trim()
                ).onSuccess { description ->
                    _state.update {
                        it.copy(
                            generatedDescription = description,
                            editedDescription = description,
                            isLoading = false
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
                    it.copy(
                        isLoading = false,
                        error = "图片处理失败: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun autoSaveToHistory(content: String) {
        val id = contentRepo.addHistory(
            content = content,
            contentType = ContentType.TEXT,
            creationType = CreationType.IMAGE_DESC,
            originalPrompt = _state.value.optionalPrompt.trim().ifBlank { "图片描述" },
            title = "图片描述: ${_state.value.imageFileName}"
        )
        _state.update { it.copy(savedHistoryId = id) }
    }

    fun saveToFavorites() {
        val content = _state.value.editedDescription.ifBlank {
            _state.value.generatedDescription
        }
        if (content.isBlank()) return

        viewModelScope.launch {
            contentRepo.addFavorite(
                content = content,
                contentType = ContentType.TEXT,
                creationType = CreationType.IMAGE_DESC,
                originalPrompt = "图片描述",
                title = "图片描述: ${_state.value.imageFileName}",
                imageUri = _state.value.selectedImageUri?.toString()
            )
            _state.update { it.copy(isSaved = true) }
        }
    }

    fun reset() {
        _state.update {
            ImageDescState()
        }
    }
}
