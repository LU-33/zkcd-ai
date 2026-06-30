package com.example.aicreationassistant.data.repository

import android.content.Context
import android.net.Uri
import com.example.aicreationassistant.data.remote.DeepSeekApi
import com.example.aicreationassistant.data.remote.model.*
import com.example.aicreationassistant.util.toBase64String
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通义千问 VL 视觉模型仓库 — 用于分析商品图片
 * 复用 DeepSeekApi 接口（OpenAI 兼容格式），仅 base URL 不同
 */
class QwenVLRepository(
    private val api: DeepSeekApi,
    private val apiKey: String,
    private val model: String = "qwen-vl-max"
) {
    private val authHeader: String get() = "Bearer $apiKey"

    /**
     * 分析单张商品图片，返回中文描述
     */
    suspend fun describeImage(
        context: Context,
        imageUri: Uri,
        prompt: String = "你是一个电商摄影分析师。请仔细分析这张商品图片，用中文输出以下信息：\n1. 商品类别：这是什么商品？\n2. 外观描述：颜色、材质、形状、设计风格\n3. 包装/配件：是否有包装盒、配件、说明书等\n4. 品牌信息：图片中可见的品牌名、LOGO、型号\n5. 卖点线索：从外观能推断出的功能和卖点\n6. 目标人群：从设计风格推断适合的消费人群\n请简洁输出，每项1-2句话，只输出你确定能看到的内容，不要猜测。"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 图片 → Base64
            val base64 = imageUri.toBase64String(context, maxDimension = 1024)
            val imageUrl = "data:image/jpeg;base64,$base64"

            // 构建多模态消息 content: [image_url, text]
            val contentArray = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "image_url")
                    add("image_url", JsonObject().apply {
                        addProperty("url", imageUrl)
                    })
                })
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", prompt)
                })
            }

            val messages = listOf(
                Message(role = "user", content = contentArray)
            )
            val request = ChatRequest(
                model = model,
                messages = messages,
                max_tokens = 1024
            )
            val response = api.chatCompletion(authHeader, request)
            val description = response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?: throw ApiException("No description from Qwen VL", httpCode = 200)

            description
        }
    }

    /**
     * 批量分析多张商品图片，返回合并的描述
     */
    suspend fun describeImages(
        context: Context,
        imageUris: List<Uri>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (imageUris.isEmpty()) return@runCatching ""

            val descriptions = mutableListOf<String>()
            imageUris.forEachIndexed { index, uri ->
                val result = describeImage(
                    context, uri,
                    prompt = "请分析第${index + 1}张商品图片：商品类别、颜色材质、设计特点、可见的品牌/型号信息。简洁输出，只描述能确定的。"
                )
                result.onSuccess { desc ->
                    descriptions.add("图片${index + 1}：$desc")
                }.onFailure {
                    descriptions.add("图片${index + 1}：无法识别")
                }
            }
            descriptions.joinToString("\n\n")
        }
    }
}
