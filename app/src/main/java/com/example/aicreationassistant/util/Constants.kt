package com.example.aicreationassistant.util

object Constants {
    // DeepSeek API
    const val DEEPSEEK_BASE_URL = "https://api.deepseek.com/"
    const val DEEPSEEK_API_KEY = "sk-f41fc7495f5444b29f5fb5a56923e301"
    const val DEEPSEEK_MODEL = "deepseek-chat"

    // API params
    const val DEFAULT_MAX_TOKENS = 2048
    const val DEFAULT_TEMPERATURE = 0.7

    // Limits
    const val MAX_PROMPT_LENGTH = 2000
    const val MIN_PROMPT_LENGTH = 2
    const val MAX_IMAGE_DIMENSION = 2048

    // Prompts
    val SYSTEM_PROMPT_SOCIAL_MEDIA = """
你是一个社交媒体文案写手。根据用户输入的关键词、心情或主题，生成一段适合发布在朋友圈的中文短文案。

要求：
- 字数在50-200字之间
- 语气轻松自然、口语化
- 适当使用emoji增加趣味性
- 适合在社交媒体上分享
- 不要包含任何敏感或不当内容
- 直接输出文案内容，不要加"文案："等前缀
""".trimIndent()

    val SYSTEM_PROMPT_PRODUCT_DESC = """
你是一个专业的电商文案写手。根据用户输入的商品信息，生成一段吸引人的商品描述文案。

要求：
- 包含标题、核心卖点、使用场景
- 语言专业且具有说服力
- 突出商品的核心优势
- 不要过度夸大或虚假宣传
- 用markdown格式输出：## 标题、### 核心卖点、### 适用场景
""".trimIndent()

    val SYSTEM_PROMPT_IMAGE_DESC = """
你是一个专业的图片描述写手。用户会提供一张图片的基本信息（文件名、尺寸、格式等），请根据这些信息以及用户的补充说明，生成一段生动、详细的图片描述文案。

要求：
- 结合图片的基本参数（尺寸、格式等）来丰富描述
- 根据文件名和用户补充说明推断图片可能的主题和内容
- 描述风格客观、细致、有画面感
- 字数在100-300字之间
- 以"这张图片…"开头
""".trimIndent()

    // EncryptedSharedPreferences
    const val SECURE_PREFS_NAME = "ai_assistant_secure_prefs"
    const val KEY_API_KEY = "deepseek_api_key"
    const val KEY_FIRST_LAUNCH = "first_launch"

    // Keystore
    const val KEY_ALIAS = "ai_assistant_master_key"

    // FileProvider
    const val FILE_PROVIDER_AUTHORITY = "com.example.aicreationassistant.fileprovider"
}
