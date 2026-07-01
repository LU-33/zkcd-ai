package com.example.aicreationassistant.ui.imageedit

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicreationassistant.domain.crop.CropAction
import com.example.aicreationassistant.domain.crop.CropEdge
import com.example.aicreationassistant.domain.crop.Corner
import com.example.aicreationassistant.domain.doodle.DOODLE_COLOR_OPTIONS
import com.example.aicreationassistant.domain.doodle.DoodleEngine
import com.example.aicreationassistant.domain.filter.FilterEngine
import com.example.aicreationassistant.domain.filter.FilterType
import com.example.aicreationassistant.domain.model.TEXT_COLOR_OPTIONS
import com.example.aicreationassistant.domain.model.TextItem
import com.example.aicreationassistant.util.WatermarkPosition
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

private val ToolbarBg = Color(0xE6404040)
private val ToolbarHeight = 72.dp
private val EditorBg = Color(0xFFE8E8E8)
private val CropBorder = Color(0xFF4A90D9)        // 醒目的蓝色边框
private val CropHandleColor = Color.White

/**
 * 裁剪拖拽目标（屏幕视觉坐标空间的 hit-test 结果）。
 * 在 onDrag 中会通过 flip 映射为位图坐标系中的 CropAction。
 */
private enum class CropDragTarget { NONE, BODY, CORNER_TL, CORNER_TR, CORNER_BL, CORNER_BR, EDGE_L, EDGE_T, EDGE_R, EDGE_B }

/** 将视觉空间的拖拽目标映射到位图坐标空间（考虑水平翻转） */
private fun mapToBitmapTarget(visual: CropDragTarget, flip: Boolean): CropDragTarget {
    if (!flip) return visual
    return when (visual) {
        CropDragTarget.CORNER_TL -> CropDragTarget.CORNER_TR
        CropDragTarget.CORNER_TR -> CropDragTarget.CORNER_TL
        CropDragTarget.CORNER_BL -> CropDragTarget.CORNER_BR
        CropDragTarget.CORNER_BR -> CropDragTarget.CORNER_BL
        CropDragTarget.EDGE_L -> CropDragTarget.EDGE_R
        CropDragTarget.EDGE_R -> CropDragTarget.EDGE_L
        else -> visual // BODY, EDGE_T, EDGE_B, NONE 不变
    }
}

// ==================== 文字命中检测 ====================

/**
 * 在屏幕坐标 (touchX, touchY) 处查找命中的文字对象。
 * 返回文字对象 ID，或 null（未命中）。
 */
private fun findTextItemAt(
    touchX: Float, touchY: Float,
    items: List<TextItem>,
    rx: Float, ry: Float,
    effScale: Float
): String? {
    // 用与渲染一致的 Paint 测量文字尺寸，确保命中区域与视觉一致
    val measurePaint = android.graphics.Paint().apply { isAntiAlias = true }
    for (item in items.reversed()) {
        val sx = rx + item.x * effScale
        val sy = ry + item.y * effScale  // top-left
        measurePaint.textSize = item.fontSize * effScale * 2.5f
        val tw = if (item.text.isNotEmpty()) measurePaint.measureText(item.text) else 120f  // 空文字给 120px 宽命中区
        val fm = measurePaint.fontMetrics
        val th = fm.bottom - fm.top.coerceAtLeast(40f)  // 最少 40px 高
        val pad = 36f  // 四周容差 36px
        if (touchX in (sx - pad)..(sx + tw + pad) &&
            touchY in (sy - pad)..(sy + th + pad)
        ) {
            return item.id
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditScreen(
    sourceUri: String,
    onNavigateBack: () -> Unit,
    onSaveCompleted: (String) -> Unit = {}
) {
    val viewModel: ImageEditViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(sourceUri) { viewModel.init(sourceUri) }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("错误") },
            text = { Text(state.error!!) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("确定") } }
        )
    }

    Scaffold(
        topBar = {
            if (!state.cropMode) {
                TopAppBar(
                    title = { Text("图片编辑") },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                    actions = {
                        IconButton(onClick = {
                            val uri = viewModel.saveToGallery(context)
                            if (uri != null) {
                                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                                viewModel.markSaved()
                                onSaveCompleted(uri.toString())
                            } else {
                                Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            } else {
                // 裁剪模式：压缩的顶部栏
                TopAppBar(
                    title = { Text("裁剪", style = MaterialTheme.typography.titleSmall) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xE6333333),
                        titleContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            if (state.cropMode) {
                // 裁剪模式：只显示裁剪工具栏，隐藏主工具栏
                CropToolbar(
                    ratio = state.cropRatio,
                    subMode = state.cropSubMode,
                    rotationDegrees = state.rotationDegrees,
                    flipHorizontal = state.flipHorizontal,
                    flipVertical = state.flipVertical,
                    onRatio = { viewModel.setCropRatio(it) },
                    onSubModeChanged = { viewModel.setCropSubMode(it) },
                    onRotationChanged = { viewModel.setRotation(it) },
                    onRotateLeft = { viewModel.rotateCounterClockwise90() },
                    onRotateRight = { viewModel.rotateClockwise90() },
                    onFlipH = { viewModel.toggleFlipHorizontal() },
                    onFlipV = { viewModel.toggleFlipVertical() },
                    onResetRotation = { viewModel.resetRotationAndFlip() },
                    onCancel = { viewModel.exitCropMode(false) },
                    onConfirm = { viewModel.exitCropMode(true) }
                )
            } else {
                // 普通模式：工具面板 + 主工具栏
                Column {
                    AnimatedVisibility(visible = state.selectedTool != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 4.dp
                        ) {
                            when (state.selectedTool) {
                                EditorTool.FILTER -> FilterPanel(
                                    state.filterType,
                                    state.bitmap,
                                    { viewModel.selectFilter(it) }
                                )
                                EditorTool.DOODLE -> DoodlePanel(
                                    color = state.doodleColor,
                                    width = state.doodleWidth,
                                    onColorChanged = { viewModel.updateDoodleColor(it) },
                                    onWidthChanged = { viewModel.updateDoodleWidth(it) },
                                    onCancel = { viewModel.cancelDoodle() },
                                    onDone = { viewModel.confirmDoodle() }
                                )
                                EditorTool.TEXT -> TextPanel(
                                    text = state.editingText,
                                    color = state.editingTextColor,
                                    fontSize = state.editingTextSize,
                                    onTextChanged = { viewModel.updateEditingText(it) },
                                    onColorChanged = { viewModel.updateEditingTextColor(it) },
                                    onSizeChanged = { viewModel.updateEditingTextSize(it) },
                                    onCancel = { viewModel.cancelTextEditing() },
                                    onDone = { viewModel.confirmAddText() }
                                )
                                EditorTool.WATERMARK -> WatermarkPanel(
                                    state.watermarkText, state.watermarkPosition, state.watermarkOpacity,
                                    state.isLoading,
                                    { viewModel.updateWatermarkText(it) },
                                    { viewModel.updateWatermarkPosition(it) },
                                    { viewModel.updateWatermarkOpacity(it) },
                                    { viewModel.applyWatermark(context) }
                                )
                                else -> {}
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ToolbarBg,
                        shadowElevation = 8.dp
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ToolbarHeight)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(EditorTool.entries) { tool ->
                                ToolButton(
                                    tool.icon, tool.label,
                                    state.selectedTool == tool,
                                    { viewModel.selectTool(tool) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(EditorBg)
        ) {
            val cw = constraints.maxWidth.toFloat()
            val ch = constraints.maxHeight.toFloat()
            LaunchedEffect(cw, ch) { viewModel.setCanvasSize(cw, ch) }

            val bmp = state.filteredBitmap ?: state.bitmap
            val bmpW = bmp?.width ?: 0
            val bmpH = bmp?.height ?: 0
            val effScale = state.baseScale * state.userScale
            val dispW = bmpW * effScale
            val dispH = bmpH * effScale
            val rx = (cw - dispW) / 2f + state.panOffsetX
            val ry = (ch - dispH) / 2f + state.panOffsetY

            if (bmp != null && bmpW > 0) {
                val img = remember(bmp) { bmp.asImageBitmap() }

                // key(state.cropMode) 强制裁剪/普通模式切换时重建 Canvas + 手势处理器
                androidx.compose.runtime.key(state.cropMode) {
                    var dragTrg by remember { mutableStateOf(CropDragTarget.NONE) }
                    // 保持最新 state 引用，避免 pointerInput 因 cropRect 变化而重启中断拖拽
                    val latest by rememberUpdatedState(state)
                    // 密度无关的触摸命中域（dp → px）
                    val density = LocalDensity.current
                    val cornerHit = with(density) { 28.dp.toPx() }  // 角：28dp ≈ 7mm
                    val edgeHit   = with(density) { 22.dp.toPx() }  // 边：22dp ≈ 5.5mm

                    // 图像中心（旋转中心）
                    val imgCX = rx + dispW / 2f
                    val imgCY = ry + dispH / 2f

                    // 决定当前唯一的活跃手势模式（互斥，避免 pointerInput 冲突）
                    val isDoodle = state.selectedTool == EditorTool.DOODLE && !state.cropMode
                    val isTextEdit = state.selectedTool == EditorTool.TEXT && !state.cropMode
                    val isNormal = !state.cropMode && !isDoodle && !isTextEdit
                    val isCrop = state.cropMode && state.cropSubMode == CropSubMode.CROP

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                when {
                                    isDoodle -> {
                                        // 涂鸦模式：单指自由绘制
                                        Modifier.pointerInput("doodle") {
                                            detectDragGestures(
                                                onDragStart = { off ->
                                                    val bx = ((off.x - rx) / effScale).coerceIn(0f, bmpW.toFloat())
                                                    val by = ((off.y - ry) / effScale).coerceIn(0f, bmpH.toFloat())
                                                    viewModel.startDoodleStroke(bx, by)
                                                },
                                                onDrag = { change, _ ->
                                                    val bx = ((change.position.x - rx) / effScale).coerceIn(0f, bmpW.toFloat())
                                                    val by = ((change.position.y - ry) / effScale).coerceIn(0f, bmpH.toFloat())
                                                    viewModel.addDoodlePoint(bx, by)
                                                    change.consume()
                                                },
                                                onDragEnd = { viewModel.endDoodleStroke() }
                                            )
                                        }
                                    }
                                    isTextEdit || (isNormal && state.textItems.isNotEmpty()) -> {
                                        // 加字模式 / 普通模式有文字 → detectDragGestures 统一处理
                                        Modifier.pointerInput("text") {
                                            var dragTargetId: String? = null
                                            detectDragGestures(
                                                onDragStart = { off ->
                                                    val s = latest
                                                    dragTargetId = findTextItemAt(
                                                        off.x, off.y, s.textItems, rx, ry, effScale
                                                    )
                                                },
                                                onDrag = { change, dragAmount ->
                                                    val id = dragTargetId
                                                    if (id != null) {
                                                        val dx = dragAmount.x / effScale
                                                        val dy = dragAmount.y / effScale
                                                        val s = latest
                                                        val item = s.textItems.find { it.id == id }
                                                        if (item != null && (dx != 0f || dy != 0f)) {
                                                            viewModel.updateTextPosition(id, item.x + dx, item.y + dy)
                                                        }
                                                    } else if (isNormal) {
                                                        // 未命中文字 + 普通模式 → 手动 pan
                                                        viewModel.updateTransform(
                                                            state.userScale,
                                                            state.panOffsetX + dragAmount.x,
                                                            state.panOffsetY + dragAmount.y
                                                        )
                                                    }
                                                    change.consume()
                                                },
                                                onDragEnd = { dragTargetId = null }
                                            )
                                        }
                                    }
                                    isCrop -> {
                                        // 裁剪模式
                                        Modifier.pointerInput("crop") {
                                            detectDragGestures(
                                                onDragStart = { off ->
                                                    val s = latest
                                                    val (tx, ty) = inverseTransformTouch(
                                                        off.x, off.y, imgCX, imgCY,
                                                        s.rotationDegrees, s.flipHorizontal, s.flipVertical
                                                    )
                                                    val vis = cropVisualRect(s, rx, ry, dispW, effScale)
                                                    val x = tx; val y = ty
                                                    dragTrg = when {
                                                        x in (vis.left - cornerHit)..(vis.left + cornerHit) && y in (vis.top - cornerHit)..(vis.top + cornerHit) -> CropDragTarget.CORNER_TL
                                                        x in (vis.right - cornerHit)..(vis.right + cornerHit) && y in (vis.top - cornerHit)..(vis.top + cornerHit) -> CropDragTarget.CORNER_TR
                                                        x in (vis.left - cornerHit)..(vis.left + cornerHit) && y in (vis.bottom - cornerHit)..(vis.bottom + cornerHit) -> CropDragTarget.CORNER_BL
                                                        x in (vis.right - cornerHit)..(vis.right + cornerHit) && y in (vis.bottom - cornerHit)..(vis.bottom + cornerHit) -> CropDragTarget.CORNER_BR
                                                        x in (vis.left - edgeHit)..(vis.left + edgeHit) && y in (vis.top + cornerHit)..(vis.bottom - cornerHit) -> CropDragTarget.EDGE_L
                                                        x in (vis.right - edgeHit)..(vis.right + edgeHit) && y in (vis.top + cornerHit)..(vis.bottom - cornerHit) -> CropDragTarget.EDGE_R
                                                        y in (vis.top - edgeHit)..(vis.top + edgeHit) && x in (vis.left + cornerHit)..(vis.right - cornerHit) -> CropDragTarget.EDGE_T
                                                        y in (vis.bottom - edgeHit)..(vis.bottom + edgeHit) && x in (vis.left + cornerHit)..(vis.right - cornerHit) -> CropDragTarget.EDGE_B
                                                        x in vis.left..vis.right && y in vis.top..vis.bottom -> CropDragTarget.BODY
                                                        else -> CropDragTarget.NONE
                                                    }
                                                },
                                                onDrag = { change, dragAmount ->
                                                    val s = latest
                                                    var dx = dragAmount.x / effScale
                                                    var dy = dragAmount.y / effScale
                                                    if (s.flipHorizontal) dx = -dx
                                                    if (s.flipVertical) dy = -dy
                                                    if (s.rotationDegrees != 0f) {
                                                        val rad = Math.toRadians(-s.rotationDegrees.toDouble())
                                                        val c = cos(rad).toFloat()
                                                        val sn = sin(rad).toFloat()
                                                        val rdx = dx * c - dy * sn
                                                        val rdy = dx * sn + dy * c
                                                        dx = rdx; dy = rdy
                                                    }
                                                    when (dragTrg) {
                                                        CropDragTarget.CORNER_TL -> viewModel.dispatchCropAction(CropAction.MoveCorner(Corner.TOP_LEFT, dx, dy))
                                                        CropDragTarget.CORNER_TR -> viewModel.dispatchCropAction(CropAction.MoveCorner(Corner.TOP_RIGHT, dx, dy))
                                                        CropDragTarget.CORNER_BL -> viewModel.dispatchCropAction(CropAction.MoveCorner(Corner.BOTTOM_LEFT, dx, dy))
                                                        CropDragTarget.CORNER_BR -> viewModel.dispatchCropAction(CropAction.MoveCorner(Corner.BOTTOM_RIGHT, dx, dy))
                                                        CropDragTarget.EDGE_L -> viewModel.dispatchCropAction(CropAction.MoveEdge(CropEdge.LEFT, dx, dy))
                                                        CropDragTarget.EDGE_R -> viewModel.dispatchCropAction(CropAction.MoveEdge(CropEdge.RIGHT, dx, dy))
                                                        CropDragTarget.EDGE_T -> viewModel.dispatchCropAction(CropAction.MoveEdge(CropEdge.TOP, dx, dy))
                                                        CropDragTarget.EDGE_B -> viewModel.dispatchCropAction(CropAction.MoveEdge(CropEdge.BOTTOM, dx, dy))
                                                        CropDragTarget.BODY -> viewModel.dispatchCropAction(CropAction.MoveBody(dx, dy))
                                                        CropDragTarget.NONE -> {}
                                                    }
                                                    change.consume()
                                                },
                                                onDragEnd = { dragTrg = CropDragTarget.NONE }
                                            )
                                        }
                                    }
                                    isNormal -> {
                                        // 普通模式：缩放/平移 + 双击恢复
                                        Modifier
                                            .pointerInput("zoom") {
                                                detectTransformGestures { _, pan, zoom, _ ->
                                                    viewModel.updateTransform(
                                                        (state.userScale * zoom).coerceIn(0.5f, 3f),
                                                        state.panOffsetX + pan.x,
                                                        state.panOffsetY + pan.y
                                                    )
                                                }
                                            }
                                            .pointerInput("tap") {
                                                detectTapGestures(onDoubleTap = { viewModel.resetTransform() })
                                            }
                                    }
                                    else -> Modifier
                                }
                            )
                    ) {
                        // ——— 旋转+翻转 Transform（图像中心为 pivot）———
                        val needXform = state.rotationDegrees != 0f || state.flipHorizontal || state.flipVertical
                        if (needXform) {
                            withTransform({
                                // 以图像中心为 pivot 做旋转和翻转
                                val pivot = Offset(imgCX, imgCY)
                                rotate(state.rotationDegrees, pivot)
                                scale(
                                    if (state.flipHorizontal) -1f else 1f,
                                    if (state.flipVertical) -1f else 1f,
                                    pivot
                                )
                            }) {
                                drawImage(
                                    image = img,
                                    dstOffset = IntOffset(rx.toInt(), ry.toInt()),
                                    dstSize = IntSize(dispW.toInt(), dispH.toInt())
                                )
                                if (state.cropMode && state.cropSubMode == CropSubMode.CROP) {
                                    drawCropOverlay(state, rx, ry, dispW, dispH, effScale)
                                }
                            }
                        } else {
                            drawImage(
                                image = img,
                                dstOffset = IntOffset(rx.toInt(), ry.toInt()),
                                dstSize = IntSize(dispW.toInt(), dispH.toInt())
                            )
                            if (state.cropMode && state.cropSubMode == CropSubMode.CROP) {
                                drawCropOverlay(state, rx, ry, dispW, dispH, effScale)
                            }
                        }
                        // ——— 绘制文字对象（不受旋转/翻转影响）———
                        val textItems = latest.textItems
                        if (textItems.isNotEmpty()) {
                            drawIntoCanvas { canvas ->
                                for (item in textItems) {
                                    val sx = rx + item.x * effScale
                                    val sy = ry + item.y * effScale
                                    val scaledSize = item.fontSize * effScale * 2.5f
                                    val paint = android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        textSize = scaledSize
                                        color = item.color.toArgb()
                                    }
                                    // item.y = 文字 top-left → 换算为 baseline
                                    val fm = paint.fontMetrics
                                    val baselineY = sy - fm.top
                                    canvas.nativeCanvas.drawText(item.text, sx, baselineY, paint)

                                    // 正在编辑的文字项：绘制选中边框
                                    if (item.id == latest.pendingTextId && item.text.isNotEmpty()) {
                                        val tw = paint.measureText(item.text)
                                        val th = fm.bottom - fm.top
                                        val borderPaint = android.graphics.Paint().apply {
                                            this.color = android.graphics.Color.argb(180, 74, 144, 217)
                                            style = android.graphics.Paint.Style.STROKE
                                            strokeWidth = 2f
                                            pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 4f), 0f)
                                        }
                                        canvas.nativeCanvas.drawRect(sx - 4f, sy - 4f, sx + tw + 4f, sy + th + 4f, borderPaint)
                                    }
                                }
                            }
                        }
                        // ——— 绘制涂鸦笔迹 ———
                        val allStrokes = latest.doodleStrokes.toList() + listOfNotNull(latest.currentDoodleStroke)
                        if (allStrokes.isNotEmpty()) {
                            drawIntoCanvas { canvas ->
                                DoodleEngine.renderStrokes(canvas.nativeCanvas, allStrokes, rx, ry, effScale)
                            }
                        }
                    }
                }
            } else if (state.isLoading || !state.isBitmapReady) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// ==================== 裁剪视觉坐标 ====================

/**
 * 裁剪框在屏幕上的可视化矩形（考虑 flip）。
 * 用于 hit-test 和 overlay 绘制。
 */
private data class VisualRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun cropVisualRect(
    state: ImageEditState,
    rx: Float, ry: Float,
    dispW: Float,
    scale: Float
): VisualRect {
    val rawL = state.cropRectLeft
    val rawR = state.cropRectRight
    val rawT = state.cropRectTop
    val rawB = state.cropRectBottom
    return if (state.flipHorizontal) {
        // 翻转：位图 (0,0) 在屏幕右侧
        VisualRect(
            left = rx + dispW - rawR * scale,
            top = ry + rawT * scale,
            right = rx + dispW - rawL * scale,
            bottom = ry + rawB * scale
        )
    } else {
        VisualRect(
            left = rx + rawL * scale,
            top = ry + rawT * scale,
            right = rx + rawR * scale,
            bottom = ry + rawB * scale
        )
    }
}

// ==================== 逆变换（用于 hit-test） ====================

/**
 * 将屏幕触摸坐标逆变换为图像坐标系（抵消旋转+翻转）。
 * 这样 hit-test 可以在原始位图坐标空间中完成。
 */
private fun inverseTransformTouch(
    touchX: Float, touchY: Float,
    imgCenterX: Float, imgCenterY: Float,
    rotationDegrees: Float, flipH: Boolean, flipV: Boolean
): Pair<Float, Float> {
    var x = touchX - imgCenterX
    var y = touchY - imgCenterY
    // 逆序：先 undo scale（翻转），再 undo rotate
    if (flipH) x = -x
    if (flipV) y = -y
    if (rotationDegrees != 0f) {
        val rad = Math.toRadians(-rotationDegrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val rx = x * c - y * s
        val ry = x * s + y * c
        x = rx; y = ry
    }
    return Pair(x + imgCenterX, y + imgCenterY)
}

// ==================== 裁剪叠加绘制 ====================

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropOverlay(
    state: ImageEditState,
    rx: Float, ry: Float,
    dw: Float, dh: Float,
    scale: Float
) {
    val vis = cropVisualRect(state, rx, ry, dw, scale)
    val cl = vis.left; val ct = vis.top; val cr = vis.right; val cb = vis.bottom

    // 半透明遮罩（四个方向）
    val dim = Color(0x88000000)
    if (ct > ry) drawRect(dim, Offset(rx, ry), Size(dw, ct - ry))                     // 上
    if (cb < ry + dh) drawRect(dim, Offset(rx, cb), Size(dw, (ry + dh) - cb))          // 下
    if (cl > rx) drawRect(dim, Offset(rx, ct), Size(cl - rx, cb - ct))                 // 左
    if (cr < rx + dw) drawRect(dim, Offset(cr, ct), Size((rx + dw) - cr, cb - ct))     // 右

    // 裁剪框边框（3px 醒目蓝色）
    drawRect(CropBorder, Offset(cl, ct), Size(cr - cl, cb - ct), style = Stroke(3f))

    // 三分线网格
    val tw = (cr - cl) / 3f
    val th = (cb - ct) / 3f
    for (i in 1..2) {
        drawLine(CropBorder.copy(alpha = 0.25f), Offset(cl + tw * i, ct), Offset(cl + tw * i, cb), strokeWidth = 1f)
        drawLine(CropBorder.copy(alpha = 0.25f), Offset(cl, ct + th * i), Offset(cr, ct + th * i), strokeWidth = 1f)
    }

    // 四角手柄（10px 半径，白底 + 蓝边框）
    val hr = 10f
    listOf(Offset(cl, ct), Offset(cr, ct), Offset(cl, cb), Offset(cr, cb)).forEach {
        drawCircle(CropHandleColor.copy(alpha = 0.9f), hr, it)
        drawCircle(CropBorder, hr, it, style = Stroke(2f))
    }

    // 四边中点手柄（6px 半径）
    listOf(
        Offset((cl + cr) / 2f, ct),
        Offset((cl + cr) / 2f, cb),
        Offset(cl, (ct + cb) / 2f),
        Offset(cr, (ct + cb) / 2f)
    ).forEach {
        drawCircle(CropHandleColor, 6f, it)
    }
}

// ==================== 裁剪工具栏 ====================

@Composable
private fun CropToolbar(
    ratio: CropRatio,
    subMode: CropSubMode,
    rotationDegrees: Float,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    onRatio: (CropRatio) -> Unit,
    onSubModeChanged: (CropSubMode) -> Unit,
    onRotationChanged: (Float) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onResetRotation: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // ==== 裁剪子模式：比例 Chips ====
        if (subMode == CropSubMode.CROP) {
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CropRatio.entries) { r ->
                    FilterChip(
                        selected = ratio == r,
                        onClick = { onRatio(r) },
                        label = { Text(r.label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
            HorizontalDivider()
        }

        // ==== 旋转子模式：角度滑块 + 预设按钮 ====
        if (subMode == CropSubMode.ROTATE) {
            // 角度滑块
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${rotationDegrees.toInt()}°",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(42.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = rotationDegrees,
                    onValueChange = onRotationChanged,
                    valueRange = -180f..180f,
                    modifier = Modifier.weight(1f)
                )
            }
            // 预设按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRotateLeft, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("↺ 90°", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onRotateRight, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("↻ 90°", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onFlipH,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (flipHorizontal) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("↔ 水平", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onFlipV,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (flipVertical) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("↕ 垂直", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onResetRotation, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("重置", style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider()
        }

        // ==== 底部共享行：✕ 左 · 模式切换 中 · ✓ 右 ====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // 左侧：取消
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.CenterStart)
            ) {
                Icon(Icons.Default.Close, "取消", Modifier.size(24.dp), tint = Color(0xFF666666))
            }
            // 中间：模式切换
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = subMode == CropSubMode.CROP,
                    onClick = { onSubModeChanged(CropSubMode.CROP) },
                    label = { Text("✂️ 裁剪", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.height(34.dp)
                )
                FilterChip(
                    selected = subMode == CropSubMode.ROTATE,
                    onClick = { onSubModeChanged(CropSubMode.ROTATE) },
                    label = { Text("↺ 旋转", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.height(34.dp)
                )
            }
            // 右侧：确认
            Surface(
                onClick = onConfirm,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, "完成", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// ==================== 子组件 ====================

@Composable
private fun ToolButton(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp).padding(vertical = 6.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

// ==================== 加字面板 ====================

@Composable
private fun TextPanel(
    text: String,
    color: Color,
    fontSize: Float,
    onTextChanged: (String) -> Unit,
    onColorChanged: (Color) -> Unit,
    onSizeChanged: (Float) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("添加文字", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        // 文字输入
        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("请输入文字") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )
        // 颜色选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("颜色：", style = MaterialTheme.typography.labelMedium)
            TEXT_COLOR_OPTIONS.forEach { (c, _) ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(
                            if (color == c) Modifier.border(2.dp, Color.White, CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                        )
                        .clickable { onColorChanged(c) }
                )
            }
        }
        // 字体大小
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("大小：${fontSize.toInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(60.dp))
            Slider(
                value = fontSize,
                onValueChange = onSizeChanged,
                valueRange = 12f..72f,
                modifier = Modifier.weight(1f)
            )
        }
        // 按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = onDone, enabled = text.isNotBlank()) { Text("完成") }
        }
    }
}

// ==================== 滤镜面板 ====================

@Composable
private fun FilterPanel(
    currentFilter: FilterType,
    sourceBitmap: Bitmap?,
    onFilterSelected: (FilterType) -> Unit
) {
    // 基于当前 bitmap 生成缩略图 — bitmap 不变时不重新计算
    val thumbnails = remember(sourceBitmap) {
        sourceBitmap?.let { bmp ->
            FilterType.entries.map { type ->
                type to withContextSync(bmp, type)
            }
        } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("滤镜", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(thumbnails) { (type, thumb) ->
                val isSelected = currentFilter == type
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .then(
                            if (isSelected) Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp)
                            ) else Modifier
                        )
                        .padding(6.dp)
                        .clickable { onFilterSelected(type) }
                ) {
                    // 缩略图
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = type.label,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                type.label.take(1),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 同步生成缩略图（在 remember 闭包中使用） */
private fun withContextSync(bmp: Bitmap, type: FilterType): Bitmap? {
    return try {
        FilterEngine.createThumbnail(bmp, type)
    } catch (_: Exception) { null }
}

// ==================== 涂鸦面板 ====================

@Composable
private fun DoodlePanel(
    color: Color,
    width: Float,
    onColorChanged: (Color) -> Unit,
    onWidthChanged: (Float) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("涂鸦", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        // 颜色选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("颜色：", style = MaterialTheme.typography.labelMedium)
            DOODLE_COLOR_OPTIONS.forEach { (c, _) ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(
                            if (color == c) Modifier.border(2.dp, Color.White, CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                        )
                        .clickable { onColorChanged(c) }
                )
            }
        }
        // 画笔粗细
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("粗细：${width.toInt()}px", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(68.dp))
            Slider(
                value = width,
                onValueChange = onWidthChanged,
                valueRange = 2f..30f,
                modifier = Modifier.weight(1f)
            )
        }
        // 按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = onDone) { Text("完成") }
        }
    }
}

@Composable
private fun WatermarkPanel(
    text: String,
    position: WatermarkPosition,
    opacity: Float,
    isLoading: Boolean,
    onText: (String) -> Unit,
    onPos: (WatermarkPosition) -> Unit,
    onOp: (Float) -> Unit,
    onApply: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("水印设置", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            text, onText, Modifier.fillMaxWidth(),
            placeholder = { Text("输入水印文字") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )
        Text("位置", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WatermarkPosition.entries.take(3).forEach { p ->
                FilterChip(selected = position == p, onClick = { onPos(p) }, label = { Text(p.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WatermarkPosition.entries.drop(3).forEach { p ->
                FilterChip(selected = position == p, onClick = { onPos(p) }, label = { Text(p.label) })
            }
        }
        Text("透明度: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(opacity, onOp, valueRange = 0.1f..1f, modifier = Modifier.fillMaxWidth())
        Button(onClick = onApply, Modifier.fillMaxWidth(), enabled = !isLoading && text.isNotBlank()) {
            if (isLoading) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(6.dp))
            }
            Text("应用水印")
        }
    }
}
