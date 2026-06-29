package com.example.aicreationassistant.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "encrypted_content")
    val encryptedContent: String,

    @ColumnInfo(name = "content_type")
    val contentType: String,

    @ColumnInfo(name = "creation_type")
    val creationType: String,

    @ColumnInfo(name = "original_prompt")
    val originalPrompt: String,

    @ColumnInfo(name = "iv")
    val iv: String,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
