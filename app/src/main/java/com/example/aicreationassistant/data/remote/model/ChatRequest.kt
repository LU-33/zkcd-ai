package com.example.aicreationassistant.data.remote.model

import com.google.gson.JsonElement

/**
 * DeepSeek Chat API 请求体
 */
data class ChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val max_tokens: Int = 2048,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

/**
 * 消息结构。content 可为 JsonPrimitive(纯文本) 或 JsonArray(多模态)
 */
data class Message(
    val role: String,
    val content: JsonElement
)

/**
 * 多模态内容片段（用于 Vision API 的 image_url）
 */
data class ContentPart(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)
