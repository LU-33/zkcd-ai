package com.example.aicreationassistant.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aicreationassistant.domain.model.CreationType

// ==================== 调色板 ====================

private val GradientStart = Color(0xFF6366F1)   // Indigo
private val GradientMid = Color(0xFF7C3AED)     // Violet
private val GradientEnd = Color(0xFF9333EA)     // Purple

private val CardSocial = Color(0xFF6366F1)       // 朋友圈 — 紫色
private val CardProduct = Color(0xFF0EA5E9)      // 商品 — 蓝色
private val CardImage = Color(0xFFF59E0B)        // 图片 — 暖橙

private val CardSocialBg = Color(0xFFF0EEFF)     // 浅紫背景
private val CardProductBg = Color(0xFFE5F4FF)    // 浅蓝背景
private val CardImageBg = Color(0xFFFFF8EB)      // 浅橙背景

private val SurfaceWhite = Color(0xFFFAFAFE)

// ==================== 首页 ====================

@Composable
fun HomeScreen(
    onCreationCardTap: (CreationType) -> Unit,
    onImageDescTap: () -> Unit,
    onProductDescTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(SurfaceWhite)
    ) {
        // ═══════════ Header — 渐变背景 + AI 元素 ═══════════
        HeaderSection()

        Spacer(modifier = Modifier.height(20.dp))

        // ═══════════ 创作类型 — 三张卡片 ═══════════
        SectionTitle("创作类型")

        Spacer(modifier = Modifier.height(12.dp))

        // 朋友圈文案
        FeatureCard(
            emoji = "💬",
            title = CreationType.SOCIAL_MEDIA.displayName,
            description = "一句话生成爆款朋友圈内容",
            accentColor = CardSocial,
            bgColor = CardSocialBg,
            onClick = { onCreationCardTap(CreationType.SOCIAL_MEDIA) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 商品描述
        FeatureCard(
            emoji = "🛒",
            title = CreationType.PRODUCT_DESC.displayName,
            description = "专业电商文案快速生成",
            accentColor = CardProduct,
            bgColor = CardProductBg,
            onClick = onProductDescTap
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 图片理解
        FeatureCard(
            emoji = "🖼",
            title = CreationType.IMAGE_DESC.displayName,
            description = "AI 识别图片内容并生成描述",
            accentColor = CardImage,
            bgColor = CardImageBg,
            onClick = onImageDescTap
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ═══════════ 小贴士 ═══════════
        TipsCard()

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==================== Header ====================

@Composable
private fun HeaderSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to GradientStart,
                    0.5f to GradientMid,
                    1f to GradientEnd
                )
            )
            .padding(top = 36.dp, bottom = 20.dp)
    ) {
        // 装饰光斑（右上角）
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // AI 机器人表情 + 装饰星星
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("✨", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🤖", fontSize = 26.sp)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("✨", fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 问候语
            Text(
                text = "你好，今天想创作点什么？",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // AI 引擎标签
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EngineTag("DeepSeek")
                Spacer(modifier = Modifier.width(8.dp))
                EngineTag("Qwen VL")
            }
        }
    }
}

@Composable
private fun EngineTag(name: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Text(
            text = "⚡ $name 驱动",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== 分区标题 ====================

@Composable
private fun SectionTitle(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.weight(1f))
        // 装饰线
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GradientStart)
        )
    }
}

// ==================== 功能卡片 ====================

@Composable
private fun FeatureCard(
    emoji: String,
    title: String,
    description: String,
    accentColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧文字区
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                // "开始创作" 按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "开始创作",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " →",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // 右侧插画区
            Surface(
                modifier = Modifier.size(62.dp),
                shape = RoundedCornerShape(14.dp),
                color = bgColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 30.sp)
                }
            }
        }
    }
}

// ==================== 小贴士卡片 ====================

@Composable
private fun TipsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F3FF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("💡", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "创作小贴士",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = GradientStart
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "AI 可以帮你生成朋友圈文案、专业商品文案描述、图片识别描述等多种内容，输入关键词即可快速获得灵感 ✨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        }
    }
}
