package com.example.aicreationassistant.ui.favorites

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aicreationassistant.AiCreationApp
import com.example.aicreationassistant.domain.model.ContentItem
import com.example.aicreationassistant.domain.model.ContentType
import com.example.aicreationassistant.domain.model.CreationType
import com.example.aicreationassistant.util.toRelativeTime

// ==================== 设计令牌 ====================

private val Primary = Color(0xFF6366F1)
private val PrimaryLight = Color(0xFFF0EEFF)
private val GradientStart = Color(0xFF6366F1)
private val GradientMid = Color(0xFF7C3AED)
private val GradientEnd = Color(0xFF9333EA)
private val BgGray = Color(0xFFF8F9FD)
private val CardWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1C1B1F)
private val TextSecondary = Color(0xFF6B6A75)
private val TextMuted = Color(0xFF999999)
private val DividerColor = Color(0xFFF0F0F4)

private val CreationColors = mapOf(
    CreationType.SOCIAL_MEDIA to Color(0xFF6366F1),
    CreationType.PRODUCT_DESC to Color(0xFF0EA5E9),
    CreationType.IMAGE_DESC to Color(0xFFF59E0B)
)
private val CreationIcons = mapOf(
    CreationType.SOCIAL_MEDIA to Icons.Default.ChatBubble,
    CreationType.PRODUCT_DESC to Icons.Default.ShoppingCart,
    CreationType.IMAGE_DESC to Icons.Default.Image
)
private val CreationEmoji = mapOf(
    CreationType.SOCIAL_MEDIA to "💬",
    CreationType.PRODUCT_DESC to "🛒",
    CreationType.IMAGE_DESC to "🖼"
)

// ==================== 收藏页 ====================

@Composable
fun FavoritesScreen(
    onNavigateToDetail: (Long) -> Unit
) {
    val app = AiCreationApp.instance
    val viewModel: FavoritesViewModel = viewModel(
        factory = FavoritesViewModelFactory(app.serviceLocator.contentRepository)
    )
    val allItems by viewModel.favorites.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 搜索 & 筛选状态（纯 UI 层）
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf<CreationType?>(null) }

    // 按类型筛选 + 搜索
    val filteredItems = remember(allItems, selectedTab, searchQuery) {
        allItems.filter { item ->
            (selectedTab == null || item.creationType == selectedTab) &&
            (searchQuery.isBlank() ||
                item.title?.contains(searchQuery, ignoreCase = true) == true ||
                item.content.contains(searchQuery, ignoreCase = true))
        }
    }

    // 各分类计数
    val counts = remember(allItems) {
        CreationType.entries.associateWith { type -> allItems.count { it.creationType == type } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGray)
    ) {
        // ═══════════ 1. Header ═══════════
        FavoritesHeader(itemCount = allItems.size)

        // ═══════════ 2. 搜索栏 ═══════════
        SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })

        // ═══════════ 3. 分类 Tabs ═══════════
        CategoryTabs(
            selectedTab = selectedTab,
            counts = counts,
            onTabSelected = { selectedTab = if (selectedTab == it) null else it }
        )

        // ═══════════ 4. 列表 ═══════════
        if (filteredItems.isEmpty()) {
            EmptyFavorites(
                hasSearch = searchQuery.isNotBlank() || selectedTab != null,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = filteredItems, key = { it.id }) { item ->
                    FavoritesCard(
                        item = item,
                        onClick = { onNavigateToDetail(item.id) },
                        onDelete = {
                            viewModel.deleteFavorite(item.id)
                            Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, item.content)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "分享"))
                        }
                    )
                }
            }
        }
    }
}

// ==================== 1. Header ====================

@Composable
private fun FavoritesHeader(itemCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(
                Brush.verticalGradient(
                    0f to GradientStart,
                    0.5f to GradientMid,
                    1f to GradientEnd
                )
            )
    ) {
        // 装饰光斑
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = (-20).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
        // 小星点
        Text("✦", fontSize = 14.sp, color = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.align(Alignment.TopStart).offset(x = 30.dp, y = 50.dp))
        Text("✧", fontSize = 10.sp, color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-60).dp, y = 80.dp))

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "我的收藏",
                    fontSize = 28.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "共 $itemCount 项收藏内容",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // 右侧插画区
            Surface(
                modifier = Modifier.size(140.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 80.sp)
                        Text("⭐", fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

// ==================== 2. 搜索栏 ====================

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .offset(y = (-8).dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = CardWhite
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search, "搜索",
                tint = TextMuted, modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索收藏内容", color = TextMuted, fontSize = 14.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "清除", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
}

// ==================== 3. 分类 Tabs ====================

@Composable
private fun CategoryTabs(
    selectedTab: CreationType?,
    counts: Map<CreationType, Int>,
    onTabSelected: (CreationType?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item { TabChip("全部", counts.values.sum(), selectedTab == null) { onTabSelected(null) } }
        CreationType.entries.forEach { type ->
            item {
                TabChip(
                    type.displayName,
                    counts[type] ?: 0,
                    selectedTab == type
                ) { onTabSelected(type) }
            }
        }
    }

    Spacer(modifier = Modifier.height(-1.dp))
}

@Composable
private fun TabChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Primary else Color.White,
        tonalElevation = if (selected) 0.dp else 1.dp
    ) {
        Text(
            text = "$label ($count)",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) Color.White else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ==================== 4. 收藏卡片 ====================

@Composable
private fun FavoritesCard(
    item: ContentItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120))

    val accentColor = CreationColors[item.creationType] ?: Primary
    val icon = CreationIcons[item.creationType] ?: Icons.Default.ChatBubble
    val emoji = CreationEmoji[item.creationType] ?: "💬"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // === 顶部：icon + 标题 + 时间 + 更多 ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 左侧 icon
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 22.sp)
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 中间内容
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title ?: item.creationType.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Tag + 时间
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accentColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = item.creationType.displayName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.createdAt.toRelativeTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // 右侧更多
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "更多",
                            tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("分享") },
                            leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuExpanded = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text("取消收藏", color = Color(0xFFE53935)) },
                            leadingIcon = { Icon(Icons.Default.Favorite, null,
                                tint = Color(0xFFE53935), modifier = Modifier.size(18.dp)) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }

            // === 图片缩略图（IMAGE_DESC 类型）===
            if (item.creationType == CreationType.IMAGE_DESC && !item.imageUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(item.imageUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = "图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // === 内容摘要 ===
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.content.take(100).replace("\n", " ").replace("#", ""),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
    }
}

// ==================== 空状态 ====================

@Composable
private fun EmptyFavorites(hasSearch: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = PrimaryLight
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.FavoriteBorder, null,
                        tint = Primary, modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (hasSearch) "没有找到匹配的收藏" else "暂无收藏内容",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (hasSearch) "试试其他关键词" else "创作精彩内容并收藏吧 ✨",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
