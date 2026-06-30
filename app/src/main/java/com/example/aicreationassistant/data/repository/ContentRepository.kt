package com.example.aicreationassistant.data.repository

import android.net.Uri
import com.example.aicreationassistant.data.local.AppDatabase
import com.example.aicreationassistant.data.local.entity.FavoriteEntity
import com.example.aicreationassistant.data.local.entity.HistoryEntity
import com.example.aicreationassistant.domain.model.ContentItem
import com.example.aicreationassistant.domain.model.ContentType
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContentRepository(
    private val db: AppDatabase,
    private val cryptoManager: CryptoManager
) {

    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()

    // ==================== 收藏 ====================

    fun getAllFavorites(): Flow<List<ContentItem>> {
        return favoriteDao.getAll().map { entities ->
            entities.mapNotNull { it.toContentItem() }
        }
    }

    suspend fun getFavoriteById(id: Long): ContentItem? {
        return favoriteDao.getById(id)?.toContentItem()
    }

    suspend fun addFavorite(
        content: String,
        contentType: ContentType,
        creationType: CreationType,
        originalPrompt: String,
        title: String? = null,
        imageUri: String? = null
    ): Long {
        val result = cryptoManager.encrypt(content)
        val entity = FavoriteEntity(
            encryptedContent = result.ciphertext,
            contentType = contentType.key,
            creationType = creationType.key,
            originalPrompt = originalPrompt,
            iv = result.iv,
            title = title,
            imageUri = imageUri
        )
        return favoriteDao.insert(entity)
    }

    suspend fun removeFavorite(id: Long) {
        favoriteDao.deleteById(id)
    }

    suspend fun clearAllFavorites() {
        favoriteDao.deleteAll()
    }

    suspend fun isFavorite(prompt: String, type: String): Boolean {
        return favoriteDao.exists(prompt, type)
    }

    // ==================== 历史记录 ====================

    fun getAllHistory(): Flow<List<ContentItem>> {
        return historyDao.getAll().map { entities ->
            entities.mapNotNull { it.toContentItem() }
        }
    }

    suspend fun getHistoryById(id: Long): ContentItem? {
        return historyDao.getById(id)?.toContentItem()
    }

    suspend fun addHistory(
        content: String,
        contentType: ContentType,
        creationType: CreationType,
        originalPrompt: String,
        title: String? = null,
        imageUri: String? = null
    ): Long {
        val result = cryptoManager.encrypt(content)
        val entity = HistoryEntity(
            encryptedContent = result.ciphertext,
            contentType = contentType.key,
            creationType = creationType.key,
            originalPrompt = originalPrompt,
            iv = result.iv,
            title = title,
            imageUri = imageUri
        )
        return historyDao.insert(entity)
    }

    suspend fun removeHistory(id: Long) {
        historyDao.deleteById(id)
    }

    suspend fun clearAllHistory() {
        historyDao.deleteAll()
    }

    // ==================== 更新 ====================

    suspend fun updateContent(item: ContentItem) {
        val result = cryptoManager.encrypt(item.content)
        if (item.isFavorite) {
            favoriteDao.insert(
                FavoriteEntity(
                    id = item.id,
                    encryptedContent = result.ciphertext,
                    contentType = item.contentType.key,
                    creationType = item.creationType.key,
                    originalPrompt = item.originalPrompt,
                    iv = result.iv,
                    title = item.title,
                    imageUri = item.imageUri
                )
            )
        } else {
            historyDao.insert(
                HistoryEntity(
                    id = item.id,
                    encryptedContent = result.ciphertext,
                    contentType = item.contentType.key,
                    creationType = item.creationType.key,
                    originalPrompt = item.originalPrompt,
                    iv = result.iv,
                    title = item.title,
                    imageUri = item.imageUri
                )
            )
        }
    }

    // ==================== 映射 ====================

    private fun FavoriteEntity.toContentItem(): ContentItem? {
        return try {
            val decrypted = cryptoManager.decrypt(encryptedContent, iv)
            ContentItem(
                id = id,
                content = decrypted,
                contentType = ContentType.entries.firstOrNull { it.key == contentType }
                    ?: ContentType.TEXT,
                creationType = CreationType.entries.firstOrNull { it.key == creationType }
                    ?: CreationType.SOCIAL_MEDIA,
                originalPrompt = originalPrompt,
                title = title,
                imageUri = imageUri,
                createdAt = createdAt,
                isFavorite = true
            )
        } catch (e: Exception) {
            null // 解密失败则跳过该条
        }
    }

    private fun HistoryEntity.toContentItem(): ContentItem? {
        return try {
            val decrypted = cryptoManager.decrypt(encryptedContent, iv)
            ContentItem(
                id = id,
                content = decrypted,
                contentType = ContentType.entries.firstOrNull { it.key == contentType }
                    ?: ContentType.TEXT,
                creationType = CreationType.entries.firstOrNull { it.key == creationType }
                    ?: CreationType.SOCIAL_MEDIA,
                originalPrompt = originalPrompt,
                title = title,
                imageUri = imageUri,
                createdAt = createdAt,
                isFavorite = false
            )
        } catch (e: Exception) {
            null
        }
    }
}
