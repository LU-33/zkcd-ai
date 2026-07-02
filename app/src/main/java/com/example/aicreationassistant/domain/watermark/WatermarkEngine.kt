package com.example.aicreationassistant.domain.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.aicreationassistant.util.WatermarkPosition

/**
 * 水印引擎 — 负责文字水印（含阴影/描边/平铺）和图片水印的绘制。
 * 所有坐标计算均在 Bitmap 坐标系中。
 */
object WatermarkEngine {

    private const val PADDING = 48f
    private const val TILE_SPACING = 250f
    private const val TILE_ROTATION = -30f

    /**
     * 将水印应用到 Bitmap 上，返回新 Bitmap。
     * 优先图片水印，其次文字水印。
     */
    fun apply(
        source: Bitmap,
        // 文字水印参数
        text: String = "",
        position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
        opacity: Float = 0.5f,
        textSize: Float = 48f,
        color: Color = Color.White,
        shadowEnabled: Boolean = false,
        strokeEnabled: Boolean = false,
        tileEnabled: Boolean = false,
        // 图片水印参数
        imageBitmap: Bitmap? = null,
        imageScale: Float = 1f
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        if (imageBitmap != null && !imageBitmap.isRecycled) {
            drawImageWatermark(canvas, result, imageBitmap, position, opacity, imageScale)
        } else if (text.isNotBlank()) {
            if (tileEnabled) {
                drawTiledTextWatermark(canvas, result, text, opacity, textSize, color, shadowEnabled, strokeEnabled)
            } else {
                drawSingleTextWatermark(canvas, result, text, position, opacity, textSize, color, shadowEnabled, strokeEnabled)
            }
        }

        return result
    }

    // ==================== 图片水印 ====================

    private fun drawImageWatermark(
        canvas: Canvas, parent: Bitmap,
        image: Bitmap, position: WatermarkPosition,
        opacity: Float, scale: Float
    ) {
        val scaledW = (image.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (image.height * scale).toInt().coerceAtLeast(1)

        val x = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT -> PADDING
            WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> parent.width - scaledW - PADDING
            WatermarkPosition.CENTER, WatermarkPosition.BOTTOM_CENTER -> (parent.width - scaledW) / 2f
        }
        val y = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> PADDING
            WatermarkPosition.CENTER -> (parent.height - scaledH) / 2f
            WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT, WatermarkPosition.BOTTOM_CENTER ->
                parent.height - scaledH - PADDING
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (opacity * 255).toInt()
        }
        val scaled = Bitmap.createScaledBitmap(image, scaledW, scaledH, true)
        canvas.drawBitmap(scaled, x, y, paint)
        if (scaled !== image) scaled.recycle()
    }

    // ==================== 单文字水印 ====================

    private fun drawSingleTextWatermark(
        canvas: Canvas, parent: Bitmap,
        text: String, position: WatermarkPosition,
        opacity: Float, textSize: Float, color: Color,
        shadow: Boolean, stroke: Boolean
    ) {
        val basePaint = createBasePaint(textSize, opacity, color, shadow)
        val textWidth = basePaint.measureText(text)
        val textHeight = basePaint.textSize

        val x = when (position) {
            WatermarkPosition.TOP_LEFT -> PADDING
            WatermarkPosition.TOP_RIGHT -> parent.width - textWidth - PADDING
            WatermarkPosition.CENTER -> (parent.width - textWidth) / 2f
            WatermarkPosition.BOTTOM_LEFT -> PADDING
            WatermarkPosition.BOTTOM_RIGHT -> parent.width - textWidth - PADDING
            WatermarkPosition.BOTTOM_CENTER -> (parent.width - textWidth) / 2f
        }
        val y = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> textHeight + PADDING
            WatermarkPosition.CENTER -> (parent.height + textHeight) / 2f
            WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT, WatermarkPosition.BOTTOM_CENTER ->
                parent.height - PADDING
        }

        if (stroke) {
            // 双层绘制：先黑色 Stroke，再正常颜色 Fill
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = textSize
                this.style = Paint.Style.STROKE
                this.strokeWidth = textSize / 10f
                this.color = android.graphics.Color.BLACK  // 先设 color
                this.alpha = (opacity * 255).toInt()        // 再覆写 alpha
            }
            canvas.drawText(text, x, y, strokePaint)
        }

        canvas.drawText(text, x, y, basePaint)
    }

    // ==================== 平铺水印 ====================

    private fun drawTiledTextWatermark(
        canvas: Canvas, parent: Bitmap,
        text: String, opacity: Float, textSize: Float, color: Color,
        shadow: Boolean, stroke: Boolean
    ) {
        val paint = createBasePaint(textSize, opacity, color, shadow)
        val textWidth = paint.measureText(text)
        val textHeight = paint.textSize

        val spacingX = textWidth + TILE_SPACING
        val spacingY = textHeight + TILE_SPACING

        // 从左上角开始，覆盖整图并略超出
        var cy = -TILE_SPACING
        while (cy < parent.height + TILE_SPACING) {
            var cx = -TILE_SPACING
            while (cx < parent.width + TILE_SPACING) {
                canvas.save()
                canvas.rotate(TILE_ROTATION, cx + textWidth / 2f, cy + textHeight / 2f)

                if (stroke) {
                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.textSize = textSize
                        this.style = Paint.Style.STROKE
                        this.strokeWidth = textSize / 10f
                        this.color = android.graphics.Color.BLACK  // 先设 color
                        this.alpha = (opacity * 255).toInt()        // 再覆写 alpha
                    }
                    canvas.drawText(text, cx, cy + textHeight, strokePaint)
                }

                canvas.drawText(text, cx, cy + textHeight, paint)
                canvas.restore()
                cx += spacingX
            }
            cy += spacingY
        }
    }

    // ==================== 内部工具 ====================

    private fun createBasePaint(
        textSize: Float, opacity: Float, color: Color, shadow: Boolean
    ): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.color = color.toArgb()            // 先设 color（含 alpha=255）
            this.alpha = (opacity * 255).toInt()   // 再覆写 alpha ← 关键顺序！
            if (shadow) {
                setShadowLayer(6f, 2f, 2f, android.graphics.Color.argb(180, 0, 0, 0))
            }
        }
    }
}
