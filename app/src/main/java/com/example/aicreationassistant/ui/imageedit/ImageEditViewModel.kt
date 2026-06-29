package com.example.aicreationassistant.ui.imageedit

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.util.WatermarkPosition
import com.example.aicreationassistant.util.addWatermark
import com.example.aicreationassistant.util.toBitmap
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImageEditState(
    val sourceUri: String = "",
    val croppedUri: Uri? = null,
    val currentBitmapUri: Uri? = null,
    val watermarkText: String = "",
    val watermarkPosition: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val watermarkOpacity: Float = 0.5f,
    val processedUri: Uri? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

class ImageEditViewModel : ViewModel() {

    private val _state = MutableStateFlow(ImageEditState())
    val state: StateFlow<ImageEditState> = _state.asStateFlow()

    fun init(sourceUri: String) {
        _state.update {
            it.copy(
                sourceUri = sourceUri,
                currentBitmapUri = Uri.parse(sourceUri),
                processedUri = null
            )
        }
    }

    fun onCropResult(uri: Uri) {
        _state.update { it.copy(croppedUri = uri, currentBitmapUri = uri, processedUri = null) }
    }

    fun updateWatermarkText(text: String) {
        _state.update { it.copy(watermarkText = text, processedUri = null) }
    }

    fun updateWatermarkPosition(position: WatermarkPosition) {
        _state.update { it.copy(watermarkPosition = position, processedUri = null) }
    }

    fun updateWatermarkOpacity(opacity: Float) {
        _state.update { it.copy(watermarkOpacity = opacity, processedUri = null) }
    }

    fun applyWatermark(context: Context) {
        val currentUri = _state.value.currentBitmapUri ?: return
        val watermarkText = _state.value.watermarkText.trim()
        if (watermarkText.isBlank()) {
            _state.update { it.copy(error = "请输入水印文字") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            withContext(Dispatchers.IO) {
                try {
                    val bitmap = currentUri.toBitmap(context)
                        ?: throw IllegalStateException("无法加载图片")

                    val watermarked = bitmap.addWatermark(
                        text = watermarkText,
                        position = _state.value.watermarkPosition,
                        opacity = _state.value.watermarkOpacity
                    )

                    // Save to cache/shared/ (FileProvider 允许的路径)
                    val sharedDir = File(context.cacheDir, "shared")
                    if (!sharedDir.exists()) sharedDir.mkdirs()
                    val outputFile = File(sharedDir, "edited_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(outputFile).use { fos ->
                        watermarked.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    }

                    bitmap.recycle()
                    watermarked.recycle()

                    val uri = Uri.fromFile(outputFile)

                    _state.update {
                        it.copy(
                            processedUri = uri,
                            currentBitmapUri = uri,
                            isLoading = false
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(isLoading = false, error = "处理图片失败: ${e.message}")
                    }
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun markSaved() {
        _state.update { it.copy(isSaved = true) }
    }

    /**
     * 将图片保存到系统相册（MediaStore），返回相册中的 Uri
     */
    fun saveToGallery(context: Context, sourceUri: Uri): Uri? {
        return try {
            val bitmap = sourceUri.toBitmap(context) ?: return null
            val savedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "AI编辑_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI创作助手")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )
                uri?.also {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES + "/AI创作助手"
                )
                dir.mkdirs()
                val file = File(dir, "AI编辑_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                Uri.fromFile(file)
            }
            bitmap.recycle()
            savedUri
        } catch (e: Exception) {
            null
        }
    }
}
