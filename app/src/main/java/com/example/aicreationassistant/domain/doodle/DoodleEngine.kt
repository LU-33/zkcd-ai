package com.example.aicreationassistant.domain.doodle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.UUID

/**
 * 一条完整的涂鸦笔迹。
 * points 中的所有坐标均在**位图坐标系**中（0 ~ bitmapWidth/Height）。
 */
data class DoodleStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Offset> = emptyList(),
    val color: Color = Color.Red,
    val strokeWidth: Float = 8f   // 位图逻辑像素宽度
)

/** 涂鸦预设颜色 */
val DOODLE_COLOR_OPTIONS = listOf(
    Color.Black  to "黑",
    Color.White  to "白",
    Color.Red    to "红",
    Color.Yellow to "黄",
    Color.Blue   to "蓝",
    Color(0xFF4CAF50) to "绿"
)

/**
 * 涂鸦引擎 — 负责笔迹的创建、路径管理，以及渲染到 Canvas / Bitmap。
 */
object DoodleEngine {

    // ==================== 屏幕渲染 ====================

    /**
     * 将 [strokes] 绘制到原生 Canvas 上（用于 Compose 实时预览）。
     * [rx]/[ry] 是图片在画布上的偏移，[effScale] 是当前缩放因子。
     */
    fun renderStrokes(
        canvas: Canvas,
        strokes: List<DoodleStroke>,
        rx: Float, ry: Float,
        effScale: Float
    ) {
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val paint = createStrokePaint(stroke, effScale)
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(rx + first.x * effScale, ry + first.y * effScale)
            for (i in 1 until stroke.points.size) {
                val p = stroke.points[i]
                path.lineTo(rx + p.x * effScale, ry + p.y * effScale)
            }
            canvas.drawPath(path, paint)
        }
    }

    // ==================== 导出渲染 ====================

    /**
     * 将所有笔迹绘制到目标 Bitmap 上（导出用）。
     * 坐标直接使用位图坐标，不需要转换。
     */
    fun renderStrokesToBitmap(bitmap: Bitmap, strokes: List<DoodleStroke>) {
        val canvas = Canvas(bitmap)
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val paint = Paint().apply {
                color = stroke.color.toArgb()
                strokeWidth = stroke.strokeWidth
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            path.moveTo(stroke.points[0].x, stroke.points[0].y)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x, stroke.points[i].y)
            }
            canvas.drawPath(path, paint)
        }
    }

    // ==================== 内部 ====================

    private fun createStrokePaint(stroke: DoodleStroke, effScale: Float): Paint {
        return Paint().apply {
            color = stroke.color.toArgb()
            strokeWidth = stroke.strokeWidth * effScale
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
}
