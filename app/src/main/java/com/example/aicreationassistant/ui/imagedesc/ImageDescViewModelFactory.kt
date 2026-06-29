package com.example.aicreationassistant.ui.imagedesc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.data.repository.DeepSeekRepository
import com.example.aicreationassistant.util.NetworkMonitor

class ImageDescViewModelFactory(
    private val deepSeekRepo: DeepSeekRepository,
    private val contentRepo: ContentRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ImageDescViewModel(deepSeekRepo, contentRepo, networkMonitor) as T
    }
}
