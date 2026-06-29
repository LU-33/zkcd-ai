package com.example.aicreationassistant.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 获取图片元数据信息（用于两段式图像描述）
 */
fun Uri.getImageInfo(context: Context): String {
    val info = StringBuilder()

    // 文件名
    val fileName = getFileName(context)
    info.append("文件名：$fileName\n")

    // 文件大小
    context.contentResolver.openInputStream(this)?.use { stream ->
        val size = stream.available()
        info.append("文件大小：${size / 1024}KB\n")
    }

    // 图片尺寸
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(this)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
        info.append("尺寸：${options.outWidth}×${options.outHeight}\n")
        info.append("格式：${options.outMimeType ?: "未知"}")
    }

    return info.toString()
}

/**
 * 将Uri对应的图片转为Base64编码字符串，自动缩放至maxDimension以内
 */
fun Uri.toBase64String(context: Context, maxDimension: Int = Constants.MAX_IMAGE_DIMENSION): String {
    val inputStream = context.contentResolver.openInputStream(this)
        ?: throw IllegalStateException("无法读取图片")

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeStream(inputStream, null, options)
    inputStream.close()

    // 计算缩放比例
    var sampleSize = 1
    val (width, height) = options.outWidth to options.outHeight
    while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    val decodeStream = context.contentResolver.openInputStream(this)
        ?: throw IllegalStateException("无法读取图片")
    val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
    decodeStream.close()

    if (bitmap == null) throw IllegalStateException("图片解码失败")

    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    bitmap.recycle()
    outputStream.close()

    return base64
}

/**
 * 从Uri加载Bitmap
 */
fun Uri.toBitmap(context: Context, maxDimension: Int = 4096): Bitmap? {
    val inputStream = context.contentResolver.openInputStream(this) ?: return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(inputStream, null, options)
    inputStream.close()

    var sampleSize = 1
    val (w, h) = options.outWidth to options.outHeight
    while (w / sampleSize > maxDimension || h / sampleSize > maxDimension) {
        sampleSize *= 2
    }

    val stream = context.contentResolver.openInputStream(this) ?: return null
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
    stream.close()
    return bitmap
}

/**
 * 给Bitmap添加文字水印
 */
fun Bitmap.addWatermark(
    text: String,
    position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    opacity: Float = 0.5f,
    textSize: Float = 48f
): Bitmap {
    val result = this.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        this.alpha = (opacity * 255).toInt()
        this.color = android.graphics.Color.WHITE
        // Draw shadow
        setShadowLayer(4f, 2f, 2f, android.graphics.Color.argb(128, 0, 0, 0))
    }

    val padding = 32f
    val textWidth = paint.measureText(text)
    val textHeight = paint.textSize

    val x = when (position) {
        WatermarkPosition.TOP_LEFT -> padding
        WatermarkPosition.TOP_RIGHT -> result.width - textWidth - padding
        WatermarkPosition.CENTER -> (result.width - textWidth) / 2
        WatermarkPosition.BOTTOM_LEFT -> padding
        WatermarkPosition.BOTTOM_RIGHT -> result.width - textWidth - padding
        WatermarkPosition.BOTTOM_CENTER -> (result.width - textWidth) / 2
    }

    val y = when (position) {
        WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> textHeight + padding
        WatermarkPosition.CENTER -> (result.height + textHeight) / 2
        WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT, WatermarkPosition.BOTTOM_CENTER ->
            result.height - padding
    }

    canvas.drawText(text, x, y, paint)
    return result
}

/**
 * 从Uri获取文件名
 */
fun Uri.getFileName(context: Context): String {
    var name = "unknown"
    context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) {
            name = cursor.getString(index)
        }
    }
    return name
}

/**
 * 截断文本
 */
fun String.truncate(maxLength: Int): String {
    return if (this.length > maxLength) this.take(maxLength) + "…" else this
}

/**
 * 格式化时间戳为相对时间字符串
 */
fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 604_800_000 -> "${diff / 86_400_000}天前"
        else -> {
            val sdf = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
            sdf.format(java.util.Date(this))
        }
    }
}

enum class WatermarkPosition(val label: String) {
    TOP_LEFT("左上"),
    TOP_RIGHT("右上"),
    CENTER("居中"),
    BOTTOM_LEFT("左下"),
    BOTTOM_RIGHT("右下"),
    BOTTOM_CENTER("底部居中")
}
