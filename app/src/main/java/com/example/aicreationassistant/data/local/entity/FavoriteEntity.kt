package com.example.aicreationassistant.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "encrypted_content")
    val encryptedContent: String,

    @ColumnInfo(name = "content_type")
    val contentType: String,  // "text" or "image"

    @ColumnInfo(name = "creation_type")
    val creationType: String, // "social_media", "product_desc", "image_desc"

    @ColumnInfo(name = "original_prompt")
    val originalPrompt: String,

    @ColumnInfo(name = "iv")
    val iv: String,  // Base64-encoded 12-byte IV

    @ColumnInfo(name = "title")
    val title: String? = null,  // 收藏时用户可选的标题

    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null,  // 关联图片的本地路径

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
