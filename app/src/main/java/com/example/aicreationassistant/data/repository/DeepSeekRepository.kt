package com.example.aicreationassistant.data.repository

import com.example.aicreationassistant.data.remote.DeepSeekApi
import com.example.aicreationassistant.data.remote.model.*
import com.example.aicreationassistant.domain.model.ConversationTurn
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

    // ==================== 多轮对话 ====================

    /**
     * 多轮对话文本生成 — 携带完整对话历史
     * 构建 messages: [system] + [history turns...] + [user: newUserMessage]
     */
    suspend fun generateConversation(
        systemPrompt: String,
        history: List<ConversationTurn>,
        newUserMessage: String,
        model: String = "deepseek-chat",
        maxTokens: Int = 2048,
        temperature: Double = 0.7
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val messages = mutableListOf<Message>()
            // 1. System message
            messages.add(Message(role = "system", content = JsonPrimitive(systemPrompt)))
            // 2. All previous turns
            history.forEach { turn ->
                messages.add(Message(role = turn.role, content = JsonPrimitive(turn.content)))
            }
            // 3. Current user message
            messages.add(Message(role = "user", content = JsonPrimitive(newUserMessage)))

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
     * 多轮对话图片描述生成 — 携带图片上下文 + 完整对话历史
     * 图片信息注入到 system message 中，确保每次 API 调用都包含图片上下文
     */
    suspend fun generateImageConversation(
        systemPrompt: String,
        imageInfo: String,
        history: List<ConversationTurn>,
        newUserMessage: String = "",
        model: String = "deepseek-chat",
        maxTokens: Int = 1024
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 将图片元数据注入 system message，使其在每轮对话中都可见
            val fullSystemPrompt = buildString {
                append(systemPrompt)
                append("\n\n=== 图片基本信息（每次对话均包含此上下文）===\n")
                append(imageInfo)
                append("\n\n请根据以上图片信息，结合用户的输入，生成或修改图片描述。")
            }

            val messages = mutableListOf<Message>()
            messages.add(Message(role = "system", content = JsonPrimitive(fullSystemPrompt)))
            // 历史对话
            history.forEach { turn ->
                messages.add(Message(role = turn.role, content = JsonPrimitive(turn.content)))
            }
            // 当前用户消息
            if (newUserMessage.isNotBlank()) {
                messages.add(Message(role = "user", content = JsonPrimitive(newUserMessage)))
            }

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
