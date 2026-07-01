package com.example.aicreationassistant.domain.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * 图片上的文字对象。
 * 位置 (x, y) 在位图坐标系中，表示文字基线左下角坐标。
 * fontSize 为 sp 单位，屏幕渲染用；导出时换算为 bitmap 像素。
 */
data class TextItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val x: Float,            // 位图坐标系中的 X
    val y: Float,            // 位图坐标系中的 Y（基线）
    val fontSize: Float,     // sp 单位，范围 12~72
    val color: Color
)

/** 编辑面板中的预设颜色选项 */
val TEXT_COLOR_OPTIONS = listOf(
    Color.White  to "白",
    Color.Black  to "黑",
    Color.Red    to "红",
    Color.Yellow to "黄",
    Color.Blue   to "蓝",
    Color(0xFF4CAF50) to "绿"
)
