package com.example.aicreationassistant.ui.imageedit

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicreationassistant.domain.crop.CropAction
import com.example.aicreationassistant.domain.filter.FilterEngine
import com.example.aicreationassistant.domain.filter.FilterType
import com.example.aicreationassistant.domain.doodle.DoodleEngine
import com.example.aicreationassistant.domain.doodle.DoodleStroke
import com.example.aicreationassistant.domain.model.TextItem
import com.example.aicreationassistant.domain.crop.CropConstraints
import com.example.aicreationassistant.domain.crop.CropEngine
import com.example.aicreationassistant.domain.crop.CropRect
import com.example.aicreationassistant.util.WatermarkPosition
import com.example.aicreationassistant.util.addWatermark
import com.example.aicreationassistant.util.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

enum class EditorTool(val label: String, val icon: String) {
    CROP("裁剪", "✂️"), FILTER("滤镜", "🎨"), TEXT("加字", "📝"),
    WATERMARK("水印", "💧"), DOODLE("涂鸦", "✏️")
}

enum class CropRatio(val label: String, val ratio: Float) {
    FREE("自由", 0f), SQUARE("1:1", 1f), R43("4:3", 4f/3f), R169("16:9", 16f/9f), R32("3:2", 3f/2f), ORIGINAL("原图", -1f)
}

enum class CropSubMode(val label: String) { CROP("裁剪"), ROTATE("旋转") }

data class ImageEditState(
    val sourceUri: String = "",
    val displayUri: Uri? = null,
    // 位图加载
    val bitmap: Bitmap? = null,
    val bitmapWidth: Int = 0,
    val bitmapHeight: Int = 0,
    val isBitmapReady: Boolean = false,
    // 画布尺寸
    val canvasWidth: Float = 0f,
    val canvasHeight: Float = 0f,
    // 初始缩放（baseScale = 图片正好铺满画布的比例）
    val baseScale: Float = 1f,
    // 用户交互的附加缩放
    val userScale: Float = 1f,
    // 平移偏移（相对于居中位置）
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    // 工具
    val selectedTool: EditorTool? = null,
    // 裁剪模式
    val cropMode: Boolean = false,
    val cropSubMode: CropSubMode = CropSubMode.CROP,  // 当前子模式：裁剪 or 旋转
    val cropRectLeft: Float = 0f,
    val cropRectTop: Float = 0f,
    val cropRectRight: Float = 0f,
    val cropRectBottom: Float = 0f,
    val rotationDegrees: Float = 0f,      // -180~180，图片旋转角度
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    // 裁剪比例
    val cropRatio: CropRatio = CropRatio.FREE,
    // 滤镜
    val filterType: FilterType = FilterType.ORIGINAL,
    val filteredBitmap: Bitmap? = null,
    // 加字
    val textItems: List<TextItem> = emptyList(),
    val pendingTextId: String? = null,  // 当前正在编辑的文字项 ID
    val editingText: String = "",
    val editingTextColor: Color = Color.White,
    val editingTextSize: Float = 32f,
    // 涂鸦
    val doodleStrokes: List<DoodleStroke> = emptyList(),
    val currentDoodleStroke: DoodleStroke? = null,  // 正在绘制中的笔迹
    val doodleColor: Color = Color.Red,
    val doodleWidth: Float = 8f,
    // 水印
    val watermarkText: String = "",
    val watermarkPosition: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val watermarkOpacity: Float = 0.5f,
    // 通用
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

class ImageEditViewModel : ViewModel() {

    private val _state = MutableStateFlow(ImageEditState())
    val state: StateFlow<ImageEditState> = _state.asStateFlow()

    fun init(sourceUri: String) {
        _state.update {
            ImageEditState(sourceUri = sourceUri, displayUri = Uri.parse(sourceUri))
        }
        loadBitmap(sourceUri)
    }

    private fun loadBitmap(uriStr: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriStr)
                    val ctx = com.example.aicreationassistant.AiCreationApp.instance
                    // 1. 先获取原始尺寸
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    val origW = opts.outWidth; val origH = opts.outHeight
                    if (origW <= 0 || origH <= 0) {
                        _state.update { it.copy(isLoading = false, error = "无法解析图片尺寸") }
                        return@withContext
                    }
                    // 2. 加载缩略版位图（最大边 ≤ 2048）
                    val sampleSize = calculateSampleSize(origW, origH, 2048)
                    val loadOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val bitmap = ctx.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, loadOpts)
                    }
                    if (bitmap == null) {
                        _state.update { it.copy(isLoading = false, error = "加载图片失败") }
                        return@withContext
                    }
                    // 3. 使用实际 bitmap 尺寸（非原始尺寸），统一坐标系
                    val bw = bitmap.width; val bh = bitmap.height
                    _state.update {
                        it.copy(
                            bitmap = bitmap,
                            bitmapWidth = bw, bitmapHeight = bh,
                            isBitmapReady = true,
                            isLoading = false,
                            userScale = 1f, panOffsetX = 0f, panOffsetY = 0f,
                            filterType = FilterType.ORIGINAL, filteredBitmap = null,
                            textItems = emptyList(), doodleStrokes = emptyList()
                        )
                    }
                    // 4. 有画布尺寸则立即计算 baseScale
                    val cw = _state.value.canvasWidth; val ch = _state.value.canvasHeight
                    if (cw > 0 && ch > 0) recalcBaseScale(bw, bh, cw, ch)
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, error = "加载图片失败: ${e.message}") }
                }
            }
        }
    }

    /** 设置画布尺寸并重新计算 baseScale */
    fun setCanvasSize(width: Float, height: Float) {
        _state.update { it.copy(canvasWidth = width, canvasHeight = height) }
        if (_state.value.isBitmapReady) {
            recalcBaseScale(_state.value.bitmapWidth, _state.value.bitmapHeight, width, height)
        }
    }

    private fun recalcBaseScale(bmpW: Int, bmpH: Int, canvasW: Float, canvasH: Float) {
        if (bmpW <= 0 || bmpH <= 0 || canvasW <= 0 || canvasH <= 0) return
        val s = min(canvasW / bmpW, canvasH / bmpH)
        _state.update { it.copy(baseScale = s) }
    }

    // ==================== 缩放拖动 ====================

    fun updateTransform(userScale: Float, panX: Float, panY: Float) {
        _state.update {
            it.copy(
                userScale = userScale.coerceIn(0.5f, 3f),
                panOffsetX = panX,
                panOffsetY = panY
            )
        }
    }

    fun resetTransform() {
        _state.update { it.copy(userScale = 1f, panOffsetX = 0f, panOffsetY = 0f) }
    }

    // ==================== 工具 ====================

    fun selectTool(tool: EditorTool?) {
        if (tool == EditorTool.CROP) {
            enterCropMode()
            return
        }
        if (tool == EditorTool.FILTER) {
            _state.update {
                it.copy(
                    cropMode = false,
                    rotationDegrees = 0f, flipHorizontal = false, flipVertical = false,
                    selectedTool = if (it.selectedTool == EditorTool.FILTER) null else EditorTool.FILTER
                )
            }
            return
        }
        if (tool == EditorTool.DOODLE) {
            val isSelecting = _state.value.selectedTool != EditorTool.DOODLE
            _state.update {
                it.copy(selectedTool = if (isSelecting) EditorTool.DOODLE else null)
            }
            return
        }
        if (tool == EditorTool.TEXT) {
            val s = _state.value
            val isSelecting = s.selectedTool != EditorTool.TEXT
            if (isSelecting) {
                // 进入加字模式 → 立即在图片中央创建待编辑文字项
                val item = TextItem(
                    text = "",
                    x = s.bitmapWidth / 2f,
                    y = s.bitmapHeight / 2f,
                    fontSize = s.editingTextSize,
                    color = s.editingTextColor
                )
                _state.update {
                    it.copy(
                        selectedTool = EditorTool.TEXT,
                        textItems = it.textItems + item,
                        pendingTextId = item.id,
                        editingText = ""
                    )
                }
            } else {
                // 再次点击 → 取消
                cancelTextEditing()
            }
            return
        }
        _state.update { it.copy(selectedTool = if (it.selectedTool == tool) null else tool) }
    }

    // ==================== 滤镜 ====================

    fun selectFilter(type: FilterType) {
        val s = _state.value
        val sourceBmp = s.bitmap ?: return
        _state.update { it.copy(filterType = type, filteredBitmap = null) }
        if (type == FilterType.ORIGINAL) return
        viewModelScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                FilterEngine.applyFilter(sourceBmp, type)
            }
            _state.update { it.copy(filteredBitmap = filtered) }
        }
    }

    // ==================== 加字 ====================

    fun updateEditingText(text: String) {
        _state.update { it.copy(editingText = text) }
        syncPendingItem { item -> item.copy(text = text) }
    }

    fun updateEditingTextColor(color: Color) {
        _state.update { it.copy(editingTextColor = color) }
        syncPendingItem { item -> item.copy(color = color) }
    }

    fun updateEditingTextSize(size: Float) {
        _state.update { it.copy(editingTextSize = size) }
        syncPendingItem { item -> item.copy(fontSize = size) }
    }

    private fun syncPendingItem(transform: (TextItem) -> TextItem) {
        val pid = _state.value.pendingTextId ?: return
        _state.update { s ->
            s.copy(textItems = s.textItems.map { if (it.id == pid) transform(it) else it })
        }
    }

    fun confirmAddText() {
        val s = _state.value
        // 如果文字为空，移除待编辑项
        if (s.editingText.isBlank()) {
            val pid = s.pendingTextId
            _state.update {
                it.copy(
                    textItems = if (pid != null) it.textItems.filter { t -> t.id != pid } else it.textItems,
                    pendingTextId = null,
                    editingText = "",
                    selectedTool = null
                )
            }
        } else {
            _state.update { it.copy(pendingTextId = null, editingText = "", selectedTool = null) }
        }
    }

    fun cancelTextEditing() {
        val pid = _state.value.pendingTextId
        _state.update {
            it.copy(
                textItems = if (pid != null) it.textItems.filter { t -> t.id != pid } else it.textItems,
                pendingTextId = null,
                editingText = "",
                selectedTool = null
            )
        }
    }

    fun updateTextPosition(id: String, x: Float, y: Float) {
        val s = _state.value
        // 约束在图片范围内（粗略：文字不会超出图片边界）
        val cx = x.coerceIn(0f, s.bitmapWidth.toFloat())
        val cy = y.coerceIn(0f, s.bitmapHeight.toFloat())
        _state.update {
            it.copy(textItems = it.textItems.map { item ->
                if (item.id == id) item.copy(x = cx, y = cy) else item
            })
        }
    }

    fun removeTextItem(id: String) {
        _state.update { it.copy(textItems = it.textItems.filter { t -> t.id != id }) }
    }

    // ==================== 涂鸦 ====================

    fun updateDoodleColor(color: Color) { _state.update { it.copy(doodleColor = color) } }
    fun updateDoodleWidth(width: Float) { _state.update { it.copy(doodleWidth = width) } }

    /** 按下开始新笔迹（坐标已在位图空间中） */
    fun startDoodleStroke(x: Float, y: Float) {
        val s = _state.value
        val stroke = DoodleStroke(
            points = listOf(Offset(x, y)),
            color = s.doodleColor,
            strokeWidth = s.doodleWidth
        )
        _state.update { it.copy(currentDoodleStroke = stroke) }
    }

    /** 拖拽中添加路径点 */
    fun addDoodlePoint(x: Float, y: Float) {
        val cur = _state.value.currentDoodleStroke ?: return
        _state.update {
            it.copy(currentDoodleStroke = cur.copy(points = cur.points + Offset(x, y)))
        }
    }

    /** 抬起结束当前笔迹 */
    fun endDoodleStroke() {
        val cur = _state.value.currentDoodleStroke ?: return
        if (cur.points.size >= 2) {
            _state.update {
                it.copy(
                    doodleStrokes = it.doodleStrokes + cur,
                    currentDoodleStroke = null
                )
            }
        } else {
            _state.update { it.copy(currentDoodleStroke = null) }
        }
    }

    fun cancelDoodle() {
        _state.update {
            it.copy(
                doodleStrokes = emptyList(),
                currentDoodleStroke = null,
                selectedTool = null
            )
        }
    }

    fun confirmDoodle() {
        _state.update { it.copy(selectedTool = null) }
    }

    // ==================== 裁剪 — 统一 Action 分发 ====================

    /**
     * UI 层将所有裁剪拖拽操作统一通过此方法分发。
     * 不再由 UI 直接修改 cropRect，而是由 CropEngine 统一计算。
     */
    fun dispatchCropAction(action: CropAction) {
        val s = _state.value
        if (!s.cropMode) return

        val currentRect = CropRect(s.cropRectLeft, s.cropRectTop, s.cropRectRight, s.cropRectBottom)
        val constraints = buildConstraints(s)
        val newRect = CropEngine.process(action, currentRect, constraints)

        _state.update {
            it.copy(
                cropRectLeft = newRect.left,
                cropRectTop = newRect.top,
                cropRectRight = newRect.right,
                cropRectBottom = newRect.bottom
            )
        }
    }

    fun setCropRatio(ratio: CropRatio) {
        val s = _state.value
        _state.update { it.copy(cropRatio = ratio) }

        // 以全图尺寸为基准 + 保持居中 → 始终生成该比例下的最大裁剪框
        val targetRatio = resolveRatio(ratio, s)
        if (targetRatio != null && targetRatio > 0 && s.cropMode) {
            val fullRect = CropRect(0f, 0f, s.bitmapWidth.toFloat(), s.bitmapHeight.toFloat())
            val adjusted = CropEngine.adjustToRatio(fullRect, targetRatio)
            val constraints = buildConstraints(_state.value)
            val clamped = CropEngine.clamp(adjusted, constraints)
            _state.update {
                it.copy(
                    cropRectLeft = clamped.left,
                    cropRectTop = clamped.top,
                    cropRectRight = clamped.right,
                    cropRectBottom = clamped.bottom
                )
            }
        }
    }

    fun enterCropMode() {
        val s = _state.value
        if (!s.isBitmapReady) return
        val w = s.bitmapWidth.toFloat()
        val h = s.bitmapHeight.toFloat()
        // 初始裁剪框内缩 8%，让遮罩和边框清晰可见
        val inset = 0.08f
        val insetX = w * inset; val insetY = h * inset
        _state.update {
            it.copy(
                cropMode = true,
                cropSubMode = CropSubMode.CROP,
                selectedTool = null,
                cropRectLeft = insetX, cropRectTop = insetY,
                cropRectRight = w - insetX, cropRectBottom = h - insetY,
                rotationDegrees = 0f, flipHorizontal = false, flipVertical = false
            )
        }
        // 如果有预设比例，以内缩框为基准应用之
        val newState = _state.value
        val targetRatio = resolveRatio(s.cropRatio, newState)
        if (targetRatio != null && targetRatio > 0) {
            val baseRect = CropRect(newState.cropRectLeft, newState.cropRectTop, newState.cropRectRight, newState.cropRectBottom)
            val adjusted = CropEngine.adjustToRatio(baseRect, targetRatio)
            val constraints = buildConstraints(_state.value)
            val clamped = CropEngine.clamp(adjusted, constraints)
            _state.update {
                it.copy(
                    cropRectLeft = clamped.left,
                    cropRectTop = clamped.top,
                    cropRectRight = clamped.right,
                    cropRectBottom = clamped.bottom
                )
            }
        }
    }

    fun setCropSubMode(mode: CropSubMode) {
        _state.update { it.copy(cropSubMode = mode) }
    }

    fun exitCropMode(apply: Boolean) {
        if (apply) {
            applyCrop()
        } else {
            _state.update { it.copy(cropMode = false, rotationDegrees = 0f, flipHorizontal = false, flipVertical = false) }
        }
    }

    fun setRotation(degrees: Float) {
        _state.update { it.copy(rotationDegrees = degrees.coerceIn(-180f, 180f)) }
    }

    fun resetRotationAndFlip() {
        _state.update { it.copy(rotationDegrees = 0f, flipHorizontal = false, flipVertical = false) }
    }

    /** 每次调用在当前角度上累加 90°（逆时针） */
    fun rotateCounterClockwise90() {
        val cur = _state.value.rotationDegrees
        val next = ((cur + 90f + 180f) % 360f).let { if (it > 180f) it - 360f else it }
        _state.update { it.copy(rotationDegrees = next) }
    }

    /** 每次调用在当前角度上累加 -90°（顺时针） */
    fun rotateClockwise90() {
        val cur = _state.value.rotationDegrees
        val next = ((cur - 90f + 180f) % 360f).let { if (it > 180f) it - 360f else it }
        _state.update { it.copy(rotationDegrees = next) }
    }

    fun toggleFlipHorizontal() {
        _state.update { it.copy(flipHorizontal = !it.flipHorizontal) }
    }

    fun toggleFlipVertical() {
        _state.update { it.copy(flipVertical = !it.flipVertical) }
    }

    // ==================== 内部辅助 ====================

    /** 将 CropRatio 枚举解析为实际的 Float 比例值 */
    private fun resolveRatio(ratio: CropRatio, state: ImageEditState): Float? {
        return when (ratio) {
            CropRatio.FREE -> null
            CropRatio.ORIGINAL -> {
                if (state.bitmapWidth > 0 && state.bitmapHeight > 0)
                    state.bitmapWidth.toFloat() / state.bitmapHeight.toFloat()
                else null
            }
            else -> ratio.ratio
        }
    }

    /** 从当前 state 构造 CropConstraints */
    private fun buildConstraints(state: ImageEditState): CropConstraints {
        return CropConstraints(
            maxWidth = state.bitmapWidth.toFloat(),
            maxHeight = state.bitmapHeight.toFloat(),
            aspectRatio = resolveRatio(state.cropRatio, state)
        )
    }

    // ==================== 裁剪执行 ====================

    /**
     * 统一导出管线：原始 bitmap → 裁剪 → 旋转 → 翻转 → 输出。
     * 裁剪在原始坐标空间中，旋转+翻转应用在裁剪结果上。
     */
    private fun applyCrop() {
        val s = _state.value
        val bmp = s.bitmap ?: return
        val l = s.cropRectLeft.toInt().coerceAtLeast(0)
        val t = s.cropRectTop.toInt().coerceAtLeast(0)
        val r = s.cropRectRight.toInt().coerceAtMost(s.bitmapWidth)
        val bot = s.cropRectBottom.toInt().coerceAtMost(s.bitmapHeight)
        val cropW = r - l; val cropH = bot - t
        if (cropW <= 0 || cropH <= 0) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 阶段 1：裁剪（原始坐标空间）
                    var result = Bitmap.createBitmap(bmp, l, t, cropW, cropH)
                    // 阶段 2：旋转 + 翻转（按 crop → rotate → flip 顺序）
                    val needTransform = s.rotationDegrees != 0f || s.flipHorizontal || s.flipVertical
                    if (needTransform) {
                        val cx = result.width / 2f; val cy = result.height / 2f
                        val matrix = android.graphics.Matrix()
                        // postScale = Flip 最后应用 → 等价于：先旋转再翻转
                        matrix.postScale(
                            if (s.flipHorizontal) -1f else 1f,
                            if (s.flipVertical) -1f else 1f,
                            cx, cy
                        )
                        // postRotate = Rotate 在 Scale 之前 → 先旋转再翻转
                        matrix.postRotate(s.rotationDegrees, cx, cy)
                        val transformed = Bitmap.createBitmap(result, 0, 0, result.width, result.height, matrix, true)
                        result.recycle()
                        result = transformed
                    }
                    // 阶段 3：写入缓存
                    val ctx = com.example.aicreationassistant.AiCreationApp.instance
                    val dir = File(ctx.cacheDir, "shared")
                    dir.mkdirs()
                    val outFile = File(dir, "crop_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(outFile).use { result.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    result.recycle()
                    val uri = Uri.fromFile(outFile)
                    _state.update {
                        it.copy(
                            displayUri = uri, cropMode = false,
                            rotationDegrees = 0f, flipHorizontal = false, flipVertical = false,
                            userScale = 1f, panOffsetX = 0f, panOffsetY = 0f
                        )
                    }
                    loadBitmap(uri.toString())
                } catch (e: Exception) {
                    _state.update { it.copy(error = "裁剪失败: ${e.message}") }
                }
            }
        }
    }

    // ==================== 水印 ====================

    fun updateWatermarkText(text: String) { _state.update { it.copy(watermarkText = text) } }
    fun updateWatermarkPosition(pos: WatermarkPosition) { _state.update { it.copy(watermarkPosition = pos) } }
    fun updateWatermarkOpacity(op: Float) { _state.update { it.copy(watermarkOpacity = op) } }

    fun applyWatermark(context: Context) {
        val bmp = _state.value.bitmap ?: return
        val text = _state.value.watermarkText.trim()
        if (text.isBlank()) { _state.update { it.copy(error = "请输入水印文字") }; return }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                try {
                    val watermarked = bmp.addWatermark(text, _state.value.watermarkPosition, _state.value.watermarkOpacity)
                    val file = File(context.cacheDir, "shared/edited_${System.currentTimeMillis()}.jpg")
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { watermarked.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    watermarked.recycle()
                    val uri = Uri.fromFile(file)
                    _state.update {
                        it.copy(displayUri = uri, isLoading = false, watermarkText = "",
                            userScale = 1f, panOffsetX = 0f, panOffsetY = 0f)
                    }
                    loadBitmap(uri.toString())
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, error = "水印失败: ${e.message}") }
                }
            }
        }
    }

    // ==================== 保存 ====================

    fun saveToGallery(context: Context): Uri? {
        val s = _state.value
        val src = s.filteredBitmap ?: s.bitmap ?: return null
        // 如果有文字对象或涂鸦，绘制到 Bitmap 副本上再保存
        val hasOverlay = s.textItems.isNotEmpty() || s.doodleStrokes.isNotEmpty()
        val bmp = if (hasOverlay) {
            val copy = src.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(copy)
            if (s.doodleStrokes.isNotEmpty()) {
                DoodleEngine.renderStrokesToBitmap(copy, s.doodleStrokes)
            }
            if (s.textItems.isNotEmpty()) {
                drawTextItemsOnCanvas(canvas, s.textItems)
            }
            copy
        } else src
        return try {
            val savedUri: Uri?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "AI编辑_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AI创作助手")
                }
                savedUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                savedUri?.also { context.contentResolver.openOutputStream(it)?.use { os -> bmp.compress(Bitmap.CompressFormat.JPEG, 95, os) } }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory("${Environment.DIRECTORY_PICTURES}/AI创作助手")
                dir.mkdirs()
                val file = File(dir, "AI编辑_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                savedUri = Uri.fromFile(file)
            }
            if (bmp !== src) bmp.recycle()
            savedUri
        } catch (_: Exception) { null }
    }

    /** 在 Bitmap Canvas 上绘制所有文字对象（导出用） */
    private fun drawTextItemsOnCanvas(canvas: Canvas, items: List<TextItem>) {
        for (item in items) {
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = item.fontSize * 2.5f  // sp → bitmap px 近似换算
                color = item.color.toArgb()
            }
            canvas.drawText(item.text, item.x, item.y, paint)
        }
    }

    fun markSaved() { _state.update { it.copy(isSaved = true) } }
    fun clearError() { _state.update { it.copy(error = null) } }

    private fun calculateSampleSize(w: Int, h: Int, maxDim: Int): Int {
        var s = 1
        while (w / s > maxDim || h / s > maxDim) s *= 2
        return s
    }
}
