package com.example.aicreationassistant.ui.history

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.aicreationassistant.domain.model.CreationType
import java.text.SimpleDateFormat
import java.util.*

// ==================== 设计令牌（与首页/收藏统一）====================

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

private val CreationColors = mapOf(
    CreationType.SOCIAL_MEDIA to Color(0xFF6366F1),
    CreationType.PRODUCT_DESC to Color(0xFF0EA5E9),
    CreationType.IMAGE_DESC to Color(0xFFF59E0B)
)
private val CreationEmoji = mapOf(
    CreationType.SOCIAL_MEDIA to "💬",
    CreationType.PRODUCT_DESC to "🛒",
    CreationType.IMAGE_DESC to "🖼"
)

// ==================== 日期分组 ====================

private enum class TimeGroup(val label: String) {
    TODAY("今天"), YESTERDAY("昨天"), THIS_WEEK("本周"), EARLIER("更早")
}

private fun groupItems(items: List<ContentItem>): Map<TimeGroup, List<ContentItem>> {
    val now = Calendar.getInstance()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val yesterdayStart = todayStart.clone() as Calendar
    yesterdayStart.add(Calendar.DAY_OF_MONTH, -1)
    val weekStart = todayStart.clone() as Calendar
    weekStart.add(Calendar.DAY_OF_MONTH, -6) // 包含今天共7天

    return items.groupBy { item ->
        val itemCal = Calendar.getInstance().apply { timeInMillis = item.createdAt }
        when {
            itemCal >= todayStart -> TimeGroup.TODAY
            itemCal >= yesterdayStart -> TimeGroup.YESTERDAY
            itemCal >= weekStart -> TimeGroup.THIS_WEEK
            else -> TimeGroup.EARLIER
        }
    }
}

/** 判断两个时间戳是否在同一天 */
private fun isSameDay(millis1: Long, millis2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

// ==================== 展平列表模型 ====================

private sealed class FlatItem(val key: String) {
    class Header(val label: String, val count: Int) : FlatItem("h_$label")
    class Content(val item: com.example.aicreationassistant.domain.model.ContentItem) : FlatItem("c_${item.id}")
}

// ==================== 历史页 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit
) {
    val app = AiCreationApp.instance
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(app.serviceLocator.contentRepository)
    )
    val allItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf<CreationType?>(null) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 日期选择器
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedDateMillis = null
                    showDatePicker = false
                }) { Text("清除") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 筛选（关键字 + 类型 + 日期）
    val filteredItems = remember(allItems, selectedTab, searchQuery, selectedDateMillis) {
        allItems.filter { item ->
            (selectedTab == null || item.creationType == selectedTab) &&
            (searchQuery.isBlank() ||
                item.title?.contains(searchQuery, ignoreCase = true) == true ||
                item.content.contains(searchQuery, ignoreCase = true)) &&
            (selectedDateMillis == null || isSameDay(item.createdAt, selectedDateMillis!!))
        }
    }

    // 按时间分组
    val grouped = remember(filteredItems) { groupItems(filteredItems) }
    val counts = remember(allItems) {
        CreationType.entries.associateWith { type -> allItems.count { it.creationType == type } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGray)
    ) {
        // ═══════════ 1. Header ═══════════
        HistoryHeader(itemCount = allItems.size)

        // ═══════════ 2. 搜索栏 ═══════════
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            hasDateFilter = selectedDateMillis != null,
            onDateFilterClick = { showDatePicker = true }
        )

        // ═══════════ 3. 分类 Tabs + 编辑按钮 ═══════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item { TabChip("全部", counts.values.sum(), selectedTab == null) { selectedTab = null } }
                CreationType.entries.forEach { type ->
                    item {
                        TabChip(
                            type.displayName,
                            counts[type] ?: 0,
                            selectedTab == type
                        ) { selectedTab = if (selectedTab == type) null else type }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        // ═══════════ 4. 时间轴列表（展平为 header + item） ═══════════
        if (filteredItems.isEmpty()) {
            EmptyHistory(
                hasSearch = searchQuery.isNotBlank() || selectedTab != null,
                modifier = Modifier.weight(1f)
            )
        } else {
            // 将分组数据展平为列表，以便在 LazyColumn 中使用
            val flatList = remember(grouped) {
                buildList {
                    TimeGroup.entries.forEach { group ->
                        val itemsInGroup = grouped[group] ?: return@forEach
                        if (itemsInGroup.isNotEmpty()) {
                            add(FlatItem.Header(group.label, itemsInGroup.size))
                            itemsInGroup.forEach { item -> add(FlatItem.Content(item)) }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(flatList, key = { it.key }) { flat ->
                    when (flat) {
                        is FlatItem.Header -> TimelineHeader(flat.label, flat.count)
                        is FlatItem.Content -> {
                            HistoryCard(
                                item = flat.item,
                                onClick = { onNavigateToDetail(flat.item.id) },
                                onDelete = {
                                    viewModel.deleteHistory(flat.item.id)
                                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

// ==================== 1. Header ====================

@Composable
private fun HistoryHeader(itemCount: Int) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "创作历史",
                    fontSize = 28.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "共 $itemCount 条创作记录",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // 右侧日历+时钟插画
            Surface(
                modifier = Modifier.size(110.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📅", fontSize = 85.sp)
                    }
                }
            }
        }
    }
}

// ==================== 2. 搜索栏（复用收藏页样式）====================

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hasDateFilter: Boolean = false,
    onDateFilterClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .offset(y = (-8).dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = CardWhite
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, "搜索", tint = TextMuted, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索历史记录", color = TextMuted, fontSize = 14.sp) },
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
            // 清除按钮
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "清除", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
            // 日期筛选按钮
            IconButton(onClick = onDateFilterClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (hasDateFilter) Icons.Default.DateRange else Icons.Outlined.DateRange,
                    contentDescription = "按日期筛选",
                    tint = if (hasDateFilter) Primary else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

// ==================== Tab ====================

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

// ==================== 时间轴分组标题 ====================

@Composable
private fun TimelineHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = PrimaryLight
        ) {
            Text(
                "$count 条",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                fontSize = 12.sp,
                color = Primary,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // 小装饰线
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Primary.copy(alpha = 0.3f))
        )
    }
}

// ==================== 历史卡片 ====================

@Composable
private fun HistoryCard(
    item: ContentItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120))
    val context = LocalContext.current  // 提前捕获，避免在 onClick 中调用 @Composable

    val accentColor = CreationColors[item.creationType] ?: Primary
    val emoji = CreationEmoji[item.creationType] ?: "💬"
    val timeStr = remember(item) {
        val cal = Calendar.getInstance().apply { timeInMillis = item.createdAt }
        val now = Calendar.getInstance()
        when {
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                "今天 ${sdf.format(Date(item.createdAt))}"
            }
            else -> {
                val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                sdf.format(Date(item.createdAt))
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
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
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 内容摘要（1行）
                Text(
                    text = item.content.take(60).replace("\n", " ").replace("#", ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右侧操作区
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 箭头
                Icon(
                    Icons.Default.ChevronRight, "详情",
                    tint = TextMuted, modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 更多
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
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
                            onClick = {
                                menuExpanded = false
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, item.content)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "分享"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = Color(0xFFE53935)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null,
                                tint = Color(0xFFE53935), modifier = Modifier.size(18.dp)) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// ==================== 空状态 ====================

@Composable
private fun EmptyHistory(hasSearch: Boolean, modifier: Modifier = Modifier) {
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
                        Icons.Outlined.History, null,
                        tint = Primary, modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (hasSearch) "没有找到匹配的记录" else "暂无历史记录",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (hasSearch) "试试其他关键词" else "开始你的第一次创作吧 ✨",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
