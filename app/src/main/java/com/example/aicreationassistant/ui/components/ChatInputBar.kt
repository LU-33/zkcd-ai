package com.example.aicreationassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.aicreationassistant.util.Constants

/**
 * 对话输入栏 — 底部固定输入区域
 * 用于在多轮对话模式下发送 follow-up 消息
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean = true,
    hint: String = "告诉 AI 如何修改…",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { text ->
                    if (text.length <= Constants.MAX_PROMPT_LENGTH) {
                        onValueChange(text)
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text(hint) },
                minLines = 1,
                maxLines = 4,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (value.trim().isNotBlank() && enabled) {
                            onSend()
                        }
                    }
                ),
                supportingText = if (value.length > 100) {
                    { Text("${value.length}/${Constants.MAX_PROMPT_LENGTH}") }
                } else null
            )

            // 发送按钮
            if (enabled) {
                IconButton(
                    onClick = onSend,
                    enabled = value.trim().isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "发送",
                        tint = if (value.trim().isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                    )
                }
            } else {
                // 加载中显示小 spinner
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
