package com.example.aicreationassistant.data.repository

import com.example.aicreationassistant.data.remote.DeepSeekApi
import com.example.aicreationassistant.data.remote.model.*
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeepSeekRepository(
    private val api: DeepSeekApi,
    private val apiKey: String
) {

    private val authHeader: String get() = "Bearer $apiKey"

    /**
     * 文生文：纯文本对话生成
     */
    suspend fun generateText(
        systemPrompt: String,
        userMessage: String,
        model: String = "deepseek-chat",
        maxTokens: Int = 2048,
        temperature: Double = 0.7
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val messages = listOf(
                Message(
                    role = "system",
                    content = JsonPrimitive(systemPrompt)
                ),
                Message(
                    role = "user",
                    content = JsonPrimitive(userMessage)
                )
            )
            val request = ChatRequest(
                model = model,
                messages = messages,
                max_tokens = maxTokens,
                temperature = temperature
            )
            val response = api.chatCompletion(authHeader, request)
            val content = response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?: throw ApiException("No content in response", httpCode = 200)

            content
        }
    }

    /**
     * 图生文：两段式 — 图片元数据 + 用户文本 → LLM 文本生成
     * 第一段（图像理解）：本地提取图片元数据（尺寸、文件名、格式）
     * 第二段（文本生成）：将元数据 + 用户描述发给 DeepSeek Chat 生成描述文案
     */
    suspend fun generateImageDescription(
        systemPrompt: String,
        imageInfo: String,
        userMessage: String = "",
        model: String = "deepseek-chat",
        maxTokens: Int = 1024
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val promptText = buildString {
                append(systemPrompt)
                append("\n\n")
                append(imageInfo)
                if (userMessage.isNotBlank()) {
                    append("\n\n用户补充说明：$userMessage")
                }
                append("\n\n请根据以上信息，生成一段详细的图片描述。")
            }

            val messages = listOf(
                Message(
                    role = "user",
                    content = JsonPrimitive(promptText)
                )
            )
            val request = ChatRequest(
                model = model,
                messages = messages,
                max_tokens = maxTokens
            )
            val response = api.chatCompletion(authHeader, request)
            val content = response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?: throw ApiException("No description in response", httpCode = 200)

            content
        }
    }
}
