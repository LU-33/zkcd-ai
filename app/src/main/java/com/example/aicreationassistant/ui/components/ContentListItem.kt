package com.example.aicreationassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aicreationassistant.domain.model.ContentItem
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.util.toRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentListItem(
    item: ContentItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (icon, tintColor) = when (item.creationType) {
        CreationType.SOCIAL_MEDIA -> Icons.Default.ChatBubble to Color(0xFF6366F1)
        CreationType.PRODUCT_DESC -> Icons.Default.ShoppingCart to Color(0xFF0EA5E9)
        CreationType.IMAGE_DESC -> Icons.Default.Image to Color(0xFFF59E0B)
    }

    var promptExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 商品描述的多张图片 / 图片描述的图片
    val productImages = if (item.creationType == CreationType.PRODUCT_DESC && !item.imageUri.isNullOrBlank()) {
        item.imageUri.split(",").filter { it.isNotBlank() }
    } else emptyList()
    val imageDescUri = if (item.creationType == CreationType.IMAGE_DESC && !item.imageUri.isNullOrBlank()) {
        item.imageUri
    } else null

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // === 顶部：图标 + 标题 + 时间 ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = tintColor.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tintColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title ?: item.creationType.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.createdAt.toRelativeTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "删除",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // === 原始输入（可展开/收起） ===
            if (item.originalPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val isLong = item.originalPrompt.length > 60
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "原始输入",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (isLong) {
                        TextButton(
                            onClick = { promptExpanded = !promptExpanded },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                if (promptExpanded) "收起 ▲" else "展开 ▼",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Text(
                    text = item.originalPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (promptExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // === 商品图片（仅商品描述） ===
            if (productImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    productImages.take(4).forEach { uriStr ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(android.net.Uri.parse(uriStr))
                                .crossfade(true)
                                .build(),
                            contentDescription = "商品图片",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (productImages.size > 4) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "+${productImages.size - 4}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // === 图片描述的单张图片 ===
            if (imageDescUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(android.net.Uri.parse(imageDescUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = "图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // === 内容预览 ===
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.content.take(80).replace("\n", " ").replace("#", ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
