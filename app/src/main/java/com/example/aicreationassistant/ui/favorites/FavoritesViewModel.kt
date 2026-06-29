package com.example.aicreationassistant.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.domain.model.ContentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val contentRepo: ContentRepository
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<ContentItem>>(emptyList())
    val favorites: StateFlow<List<ContentItem>> = _favorites.asStateFlow()

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            contentRepo.getAllFavorites().collect { items ->
                _favorites.value = items
            }
        }
    }

    fun deleteFavorite(id: Long) {
        viewModelScope.launch {
            contentRepo.removeFavorite(id)
        }
    }
}

class FavoritesViewModelFactory(
    private val contentRepo: ContentRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FavoritesViewModel(contentRepo) as T
    }
}
