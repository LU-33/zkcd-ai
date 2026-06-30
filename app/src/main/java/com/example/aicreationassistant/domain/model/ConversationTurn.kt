package com.example.aicreationassistant.domain.model

/**
 * 单轮对话记录 — 由 user 或 assistant 产生的一条消息
 * 用于在创作模块中进行多轮对话交互
 */
data class ConversationTurn(
    val role: String,   // "user" | "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
