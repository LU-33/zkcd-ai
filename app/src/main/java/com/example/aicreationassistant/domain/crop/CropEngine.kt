package com.example.aicreationassistant.domain.crop

import kotlin.math.abs

// ==================== 数据类 ====================

/** 裁剪框 — 四个边均在 bitmap 坐标系中（0 ~ bitmapWidth/Height） */
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val isValid: Boolean get() = width > 0 && height > 0
}

/** 裁剪约束 */
data class CropConstraints(
    val minSize: Float = 50f,
    val maxWidth: Float,          // bitmap 宽度
    val maxHeight: Float,         // bitmap 高度
    val aspectRatio: Float? = null // null=自由比例, >0=固定宽高比 (width/height)
)

/** 拖拽目标的角 */
enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** 拖拽目标的边（与 ImageEditViewModel 中原 CropEdge 对应） */
enum class CropEdge { LEFT, TOP, RIGHT, BOTTOM }

/** UI 层发出的裁剪操作（统一事件模型） */
sealed interface CropAction {
    data class MoveCorner(val corner: Corner, val deltaX: Float, val deltaY: Float) : CropAction
    data class MoveEdge(val edge: CropEdge, val deltaX: Float, val deltaY: Float) : CropAction
    data class MoveBody(val deltaX: Float, val deltaY: Float) : CropAction
}

// ==================== 引擎 ====================

object CropEngine {

    /**
     * 核心处理入口：接收一个 CropAction + 当前裁剪框 + 约束 → 返回新的裁剪框。
     * 纯计算，无副作用。
     */
    fun process(action: CropAction, rect: CropRect, constraints: CropConstraints): CropRect {
        return when (action) {
            is CropAction.MoveCorner -> processCorner(action, rect, constraints)
            is CropAction.MoveEdge -> processEdge(action, rect, constraints)
            is CropAction.MoveBody -> processBody(action, rect, constraints)
        }
    }

    /** 创建覆盖全图的初始裁剪框 */
    fun createInitialRect(maxWidth: Float, maxHeight: Float): CropRect {
        return CropRect(0f, 0f, maxWidth, maxHeight)
    }

    /**
     * 调整当前裁剪框以匹配目标宽高比。
     * 保持中心点不变，在当前框内部缩放至目标比例。
     * — 如果当前框比目标比例更"宽"，则收缩宽度
     * — 如果当前框比目标比例更"窄"，则收缩高度
     */
    fun adjustToRatio(rect: CropRect, targetRatio: Float): CropRect {
        val cx = rect.centerX
        val cy = rect.centerY
        val currentWidth = rect.width
        val currentHeight = rect.height
        val currentRatio = currentWidth / currentHeight

        val newWidth: Float
        val newHeight: Float
        if (currentRatio > targetRatio) {
            newHeight = currentHeight
            newWidth = newHeight * targetRatio
        } else {
            newWidth = currentWidth
            newHeight = newWidth / targetRatio
        }
        return CropRect(
            left = cx - newWidth / 2f,
            top = cy - newHeight / 2f,
            right = cx + newWidth / 2f,
            bottom = cy + newHeight / 2f
        )
    }

    /** 将裁剪框修正到约束边界内 */
    fun clamp(rect: CropRect, constraints: CropConstraints): CropRect {
        return clampToBounds(rect, constraints)
    }

    // ==================== 内部实现 ====================

    private fun processCorner(
        action: CropAction.MoveCorner,
        rect: CropRect,
        constraints: CropConstraints
    ): CropRect {
        // 1. 原始拖拽意图
        val raw = when (action.corner) {
            Corner.TOP_LEFT     -> CropRect(rect.left + action.deltaX, rect.top + action.deltaY, rect.right, rect.bottom)
            Corner.TOP_RIGHT    -> CropRect(rect.left, rect.top + action.deltaY, rect.right + action.deltaX, rect.bottom)
            Corner.BOTTOM_LEFT  -> CropRect(rect.left + action.deltaX, rect.top, rect.right, rect.bottom + action.deltaY)
            Corner.BOTTOM_RIGHT -> CropRect(rect.left, rect.top, rect.right + action.deltaX, rect.bottom + action.deltaY)
        }
        // 2. 基本边界约束
        val clamped = clampToBounds(raw, constraints)
        // 3. 比例约束（以对角为 pivot）
        if (constraints.aspectRatio != null && constraints.aspectRatio > 0f) {
            return enforceRatioAtCorner(action.corner, clamped, constraints)
        }
        return clamped
    }

    private fun processEdge(
        action: CropAction.MoveEdge,
        rect: CropRect,
        constraints: CropConstraints
    ): CropRect {
        var (l, t, r, b) = rect
        when (action.edge) {
            CropEdge.LEFT   -> l += action.deltaX
            CropEdge.RIGHT  -> r += action.deltaX
            CropEdge.TOP    -> t += action.deltaY
            CropEdge.BOTTOM -> b += action.deltaY
        }
        val clamped = clampToBounds(CropRect(l, t, r, b), constraints)

        if (constraints.aspectRatio != null && constraints.aspectRatio > 0f) {
            return enforceRatioAtEdge(action.edge, clamped, rect, constraints)
        }
        return clamped
    }

    private fun processBody(
        action: CropAction.MoveBody,
        rect: CropRect,
        constraints: CropConstraints
    ): CropRect {
        val w = rect.width
        val h = rect.height
        val newLeft = (rect.left + action.deltaX).coerceIn(0f, constraints.maxWidth - w)
        val newTop  = (rect.top  + action.deltaY).coerceIn(0f, constraints.maxHeight - h)
        return CropRect(newLeft, newTop, newLeft + w, newTop + h)
    }

    // ---- 边界约束 ----

    private fun clampToBounds(rect: CropRect, c: CropConstraints): CropRect {
        val minS = c.minSize
        val maxW = c.maxWidth
        val maxH = c.maxHeight
        val l = rect.left.coerceIn(0f, maxW - minS)
        val t = rect.top.coerceIn(0f, maxH - minS)
        val r = rect.right.coerceIn(l + minS, maxW)
        val b = rect.bottom.coerceIn(t + minS, maxH)
        return CropRect(l, t, r, b)
    }

    // ---- 比例约束 ----

    /**
     * 以角为 pivot 的比例约束。
     * 对角固定，根据 proposed 宽高与目标比例的关系决定最终尺寸。
     */
    private fun enforceRatioAtCorner(
        corner: Corner,
        rect: CropRect,
        constraints: CropConstraints
    ): CropRect {
        val ratio = constraints.aspectRatio!!
        val result = when (corner) {
            Corner.TOP_LEFT -> {
                val px = rect.right; val py = rect.bottom
                val pw = px - rect.left; val ph = py - rect.top
                val (w, h) = fitInside(pw, ph, ratio)
                CropRect(px - w, py - h, px, py)
            }
            Corner.TOP_RIGHT -> {
                val px = rect.left; val py = rect.bottom
                val pw = rect.right - px; val ph = py - rect.top
                val (w, h) = fitInside(pw, ph, ratio)
                CropRect(px, py - h, px + w, py)
            }
            Corner.BOTTOM_LEFT -> {
                val px = rect.right; val py = rect.top
                val pw = px - rect.left; val ph = rect.bottom - py
                val (w, h) = fitInside(pw, ph, ratio)
                CropRect(px - w, py, px, py + h)
            }
            Corner.BOTTOM_RIGHT -> {
                val px = rect.left; val py = rect.top
                val pw = rect.right - px; val ph = rect.bottom - py
                val (w, h) = fitInside(pw, ph, ratio)
                CropRect(px, py, px + w, py + h)
            }
        }
        return clampToBounds(result, constraints)
    }

    /**
     * 以边为 pivot 的比例约束。
     * 左/右边 → 保持垂直中心不变，调整高度。
     * 上/下边 → 保持水平中心不变，调整宽度。
     */
    private fun enforceRatioAtEdge(
        edge: CropEdge,
        newRect: CropRect,      // 已经按拖拽调整过的 rect
        oldRect: CropRect,      // 拖拽前的原始 rect
        constraints: CropConstraints
    ): CropRect {
        val ratio = constraints.aspectRatio!!
        val result = when (edge) {
            CropEdge.LEFT, CropEdge.RIGHT -> {
                val newW = newRect.width
                val newH = newW / ratio
                val cy = oldRect.centerY
                CropRect(newRect.left, cy - newH / 2f, newRect.right, cy + newH / 2f)
            }
            CropEdge.TOP, CropEdge.BOTTOM -> {
                val newH = newRect.height
                val newW = newH * ratio
                val cx = oldRect.centerX
                CropRect(cx - newW / 2f, newRect.top, cx + newW / 2f, newRect.bottom)
            }
        }
        return clampToBounds(result, constraints)
    }

    /**
     * 给定 proposed 宽高和目标比例，返回满足比例的最大 (width, height)。
     * 如果 proposed 比例 > targetRatio → proposed 太"宽" → 以高度为准
     * 如果 proposed 比例 < targetRatio → proposed 太"窄" → 以宽度为准
     */
    private fun fitInside(pw: Float, ph: Float, ratio: Float): Pair<Float, Float> {
        if (pw <= 0f || ph <= 0f) return Pair(0f, 0f)
        return if (pw / ph > ratio) {
            Pair(ph * ratio, ph)
        } else {
            Pair(pw, pw / ratio)
        }
    }
}
