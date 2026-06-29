package com.example.aicreationassistant.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FormatToggle(
    isMarkdown: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        FilterChip(
            selected = !isMarkdown,
            onClick = { if (isMarkdown) onToggle() },
            label = { Text("纯文本") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = isMarkdown,
            onClick = { if (!isMarkdown) onToggle() },
            label = { Text("Markdown") }
        )
    }
}
