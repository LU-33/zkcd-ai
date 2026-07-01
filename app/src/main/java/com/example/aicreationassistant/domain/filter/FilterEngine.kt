package com.example.aicreationassistant.domain.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * 滤镜类型枚举 — 每个滤镜对应一组 ColorMatrix 操作。
 */
enum class FilterType(val label: String) {
    ORIGINAL("原图"),
    GRAYSCALE("灰度"),
    BLACK_WHITE("黑白"),
    SEPIA("复古"),
    COOL("冷色"),
    WARM("暖色"),
    HIGH_CONTRAST("高对比"),
    DESATURATE("低饱和"),
    INVERT("反色")
}

/**
 * 滤镜引擎 — 基于 Android ColorMatrix 实现，不依赖第三方库。
 * 内置 LRU 风格缓存，同一 Bitmap + 同一滤镜复用结果。
 */
object FilterEngine {

    // 最大缓存条目数
    private const val MAX_CACHE_SIZE = 16
    private val cache = LinkedHashMap<String, Bitmap>(MAX_CACHE_SIZE, 0.75f, true)

    // ==================== ColorMatrix 定义 ====================

    /** 灰度：标准亮度加权 (R*0.299 + G*0.587 + B*0.114) */
    private val GRAYSCALE_MATRIX = ColorMatrix(
        floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    /** 黑白：先灰度，再通过高对比度映射增强明暗反差 */
    private val BLACK_WHITE_MATRIX = ColorMatrix(
        floatArrayOf(
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    /** 复古（Sepia）色调 */
    private val SEPIA_MATRIX = ColorMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    /** 冷色：增强蓝通道 */
    private val COOL_MATRIX = ColorMatrix(
        floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 0.9f, 0f, 0f, 0f,
            0f, 0f, 1.2f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    /** 暖色：增强红通道 */
    private val WARM_MATRIX = ColorMatrix(
        floatArrayOf(
            1.2f, 0f, 0f, 0f, 10f,
            0f, 0.9f, 0f, 0f, 0f,
            0f, 0f, 0.8f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    /** 高对比度 */
    private val HIGH_CONTRAST_MATRIX = ColorMatrix(
        floatArrayOf(
            1.5f, 0f, 0f, 0f, -60f,
            0f, 1.5f, 0f, 0f, -60f,
            0f, 0f, 1.5f, 0f, -60f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    /** 低饱和度 */
    private val DESATURATE_MATRIX = ColorMatrix().apply {
        setSaturation(0.2f)
    }

    /** 反色 */
    private val INVERT_MATRIX = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    // ==================== 公开方法 ====================

    /**
     * 对 [source] 应用 [filter] 并返回新 Bitmap。
     * 如果 filter == ORIGINAL，直接返回 source（不创建新对象）。
     * 结果会被缓存，相同 source + filter 直接复用。
     */
    fun applyFilter(source: Bitmap, filter: FilterType): Bitmap {
        if (filter == FilterType.ORIGINAL) return source

        val key = "${System.identityHashCode(source)}_${filter.ordinal}"
        cache[key]?.let { return it }

        val matrix = when (filter) {
            FilterType.GRAYSCALE -> GRAYSCALE_MATRIX
            FilterType.BLACK_WHITE -> BLACK_WHITE_MATRIX
            FilterType.SEPIA -> SEPIA_MATRIX
            FilterType.COOL -> COOL_MATRIX
            FilterType.WARM -> WARM_MATRIX
            FilterType.HIGH_CONTRAST -> HIGH_CONTRAST_MATRIX
            FilterType.DESATURATE -> DESATURATE_MATRIX
            FilterType.INVERT -> INVERT_MATRIX
            FilterType.ORIGINAL -> return source // unreachable
        }

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        canvas.setBitmap(null)

        // 缓存管理：超出容量时移除最老的条目
        synchronized(cache) {
            if (cache.size >= MAX_CACHE_SIZE) {
                val oldest = cache.entries.first()
                if (oldest.value != source && oldest.value != result) {
                    oldest.value.recycle()
                }
                cache.remove(oldest.key)
            }
            cache[key] = result
        }
        return result
    }

    /** 清空滤镜缓存，释放 Bitmap 内存 */
    fun clearCache() {
        synchronized(cache) {
            cache.values.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
            cache.clear()
        }
    }

    /**
     * 创建用于缩略图预览的小尺寸 Bitmap。
     * 采样到 targetSize 以内，用于 LazyRow 中快速展示滤镜效果。
     */
    fun createThumbnail(source: Bitmap, filter: FilterType, targetSize: Int = 80): Bitmap {
        val w = source.width; val h = source.height
        val scale = minOf(targetSize.toFloat() / w, targetSize.toFloat() / h)
        val tw = (w * scale).toInt().coerceAtLeast(1)
        val th = (h * scale).toInt().coerceAtLeast(1)
        val thumb = Bitmap.createScaledBitmap(source, tw, th, true)
        return if (filter == FilterType.ORIGINAL) thumb else applyFilter(thumb, filter)
    }

}
