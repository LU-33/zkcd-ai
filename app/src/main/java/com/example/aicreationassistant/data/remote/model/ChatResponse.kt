package com.example.aicreationassistant.data.remote.model

/**
 * DeepSeek Chat API 响应体
 */
data class ChatResponse(
    val id: String,
    val `object`: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null
)

data class Choice(
    val index: Int? = null,
    val message: MessageResponse? = null,
    val finish_reason: String? = null
)

data class MessageResponse(
    val role: String? = null,
    val content: String? = null
)

data class Usage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)
