package com.example.aicreationassistant.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aicreationassistant.domain.model.CreationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreationCardTap: (CreationType) -> Unit,
    onImageDescTap: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI 创作助手",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 欢迎标题
            Text(
                text = "选择创作类型",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "AI 帮你轻松创作各类内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3 张功能卡片
            CreationCard(
                title = CreationType.SOCIAL_MEDIA.displayName,
                description = CreationType.SOCIAL_MEDIA.description,
                icon = Icons.Default.ChatBubble,
                gradientColors = listOf(
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6)
                ),
                onClick = { onCreationCardTap(CreationType.SOCIAL_MEDIA) }
            )

            CreationCard(
                title = CreationType.PRODUCT_DESC.displayName,
                description = CreationType.PRODUCT_DESC.description,
                icon = Icons.Default.ShoppingCart,
                gradientColors = listOf(
                    Color(0xFF0EA5E9),
                    Color(0xFF38BDF8)
                ),
                onClick = { onCreationCardTap(CreationType.PRODUCT_DESC) }
            )

            CreationCard(
                title = CreationType.IMAGE_DESC.displayName,
                description = CreationType.IMAGE_DESC.description,
                icon = Icons.Default.Image,
                gradientColors = listOf(
                    Color(0xFFF59E0B),
                    Color(0xFFF97316)
                ),
                onClick = onImageDescTap
            )
        }
    }
}

@Composable
fun CreationCard(
    title: String,
    description: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            // 渐变背景
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        Modifier.defaultMinSize(minHeight = 120.dp)
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                // Background gradient
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            Modifier.background(
                                Brush.horizontalGradient(gradientColors)
                            )
                        )
                )
            }

            // 内容
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.White.copy(alpha = 0.25f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
