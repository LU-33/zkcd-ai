package com.example.aicreationassistant.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.domain.model.ContentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val contentRepo: ContentRepository
) : ViewModel() {

    private val _historyItems = MutableStateFlow<List<ContentItem>>(emptyList())
    val historyItems: StateFlow<List<ContentItem>> = _historyItems.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            contentRepo.getAllHistory().collect { items ->
                _historyItems.value = items
            }
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            contentRepo.removeHistory(id)
        }
    }
}

class HistoryViewModelFactory(
    private val contentRepo: ContentRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HistoryViewModel(contentRepo) as T
    }
}
