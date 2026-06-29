package com.example.aicreationassistant.data.remote.model

/**
 * DeepSeek API 错误响应
 */
data class ApiErrorResponse(
    val error: ApiErrorDetail? = null
)

data class ApiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

class ApiException(
    message: String,
    val code: String? = null,
    val httpCode: Int? = null
) : Exception(message)

class NetworkException : Exception("网络不可用")

class ApiTimeoutException : Exception("请求超时")
