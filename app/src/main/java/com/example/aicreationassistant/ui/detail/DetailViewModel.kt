package com.example.aicreationassistant.ui.detail

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.domain.model.ContentItem
import com.example.aicreationassistant.domain.model.ContentType
import com.example.aicreationassistant.domain.model.CreationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailState(
    val title: String = "详情",
    val contentItem: ContentItem? = null,
    val isMarkdownPreview: Boolean = false,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val deleted: Boolean = false,
    val isEditing: Boolean = false,
    val editingContent: String = "",
    val isSaving: Boolean = false,
    val isEditingTitle: Boolean = false,
    val editingTitle: String = ""
)

class DetailViewModel(
    private val contentId: Long,
    private val contentRepo: ContentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    init {
        loadContent()
    }

    fun loadContent() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Try favorites first, then history
            val favItem = contentRepo.getFavoriteById(contentId)
            if (favItem != null) {
                _state.update {
                    it.copy(
                        contentItem = favItem,
                        isFavorite = true,
                        title = favItem.title ?: favItem.creationType.displayName,
                        isLoading = false
                    )
                }
                return@launch
            }
            val historyItem = contentRepo.getHistoryById(contentId)
            _state.update {
                it.copy(
                    contentItem = historyItem,
                    isFavorite = false,
                    title = historyItem?.title ?: historyItem?.creationType?.displayName ?: "详情",
                    isLoading = false
                )
            }
        }
    }

    fun toggleFormat() {
        _state.update { it.copy(isMarkdownPreview = !it.isMarkdownPreview) }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val item = _state.value.contentItem ?: return@launch
            if (_state.value.isFavorite) {
                contentRepo.removeFavorite(item.id)
                _state.update { it.copy(isFavorite = false) }
            } else {
                contentRepo.addFavorite(
                    content = item.content,
                    contentType = item.contentType,
                    creationType = item.creationType,
                    originalPrompt = item.originalPrompt,
                    title = item.title,
                    imageUri = item.imageUri
                )
                _state.update { it.copy(isFavorite = true) }
            }
        }
    }

    fun deleteContent() {
        viewModelScope.launch {
            contentRepo.removeHistory(contentId)
            contentRepo.removeFavorite(contentId)
            _state.update { it.copy(deleted = true) }
        }
    }

    fun shareContent(context: Context) {
        val content = _state.value.contentItem?.content ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "分享到"))
    }

    fun startEditing() {
        val content = _state.value.contentItem?.content ?: return
        _state.update { it.copy(isEditing = true, editingContent = content) }
    }

    fun updateEditingContent(text: String) {
        _state.update { it.copy(editingContent = text) }
    }

    fun cancelEditing() {
        _state.update { it.copy(isEditing = false, editingContent = "") }
    }

    fun saveEdit() {
        val item = _state.value.contentItem ?: return
        val newContent = _state.value.editingContent.trim()
        if (newContent.isBlank() || newContent == item.content) {
            _state.update { it.copy(isEditing = false, editingContent = "") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val updatedItem = item.copy(content = newContent)
                contentRepo.updateContent(updatedItem)
                _state.update {
                    it.copy(
                        contentItem = updatedItem,
                        isEditing = false,
                        editingContent = "",
                        isSaving = false
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    // ==================== 标题编辑 ====================

    fun startEditingTitle() {
        val item = _state.value.contentItem ?: return
        _state.update {
            it.copy(
                isEditingTitle = true,
                editingTitle = item.title ?: item.creationType.displayName
            )
        }
    }

    fun updateEditingTitle(text: String) {
        if (text.length <= 30) {
            _state.update { it.copy(editingTitle = text) }
        }
    }

    fun cancelEditingTitle() {
        _state.update { it.copy(isEditingTitle = false, editingTitle = "") }
    }

    fun saveTitle() {
        val item = _state.value.contentItem ?: return
        val newTitle = _state.value.editingTitle.trim()
        if (newTitle.isBlank() || newTitle == item.title) {
            _state.update { it.copy(isEditingTitle = false, editingTitle = "") }
            return
        }

        viewModelScope.launch {
            try {
                val updatedItem = item.copy(title = newTitle)
                contentRepo.updateContent(updatedItem)
                _state.update {
                    it.copy(
                        contentItem = updatedItem,
                        title = newTitle,
                        isEditingTitle = false,
                        editingTitle = ""
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(isEditingTitle = false) }
            }
        }
    }
}

class DetailViewModelFactory(
    private val contentId: Long,
    private val contentRepo: ContentRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DetailViewModel(contentId, contentRepo) as T
    }
}
