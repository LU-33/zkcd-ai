package com.example.aicreationassistant.domain.model

/**
 * 创作场景类型
 */
enum class CreationType(val key: String, val displayName: String, val description: String) {
    SOCIAL_MEDIA(
        "social_media",
        "朋友圈文案",
        "输入关键词，生成适合朋友圈分享的短文案"
    ),
    PRODUCT_DESC(
        "product_desc",
        "商品描述",
        "输入商品信息，生成专业电商描述文案"
    ),
    IMAGE_DESC(
        "image_desc",
        "图片描述",
        "上传图片，AI分析并生成详细描述"
    );
}

/**
 * 内容类型
 */
enum class ContentType(val key: String) {
    TEXT("text"),
    IMAGE("image");
}

/**
 * UI 层的领域模型
 */
data class ContentItem(
    val id: Long,
    val content: String,       // 已解密的内容
    val contentType: ContentType,
    val creationType: CreationType,
    val originalPrompt: String,
    val title: String? = null,
    val imageUri: String? = null,
    val createdAt: Long,
    val isFavorite: Boolean = false
)
