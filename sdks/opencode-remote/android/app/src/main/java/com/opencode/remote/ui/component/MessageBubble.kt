package com.opencode.remote.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencode.remote.ui.theme.ReasoningBg
import com.opencode.remote.viewmodel.MessagePart

// 消息气泡组件：支持普通文本和可折叠的推理内容
@Composable
fun MessageBubble(part: MessagePart) {
    if (part.type == "reasoning") {
        // 推理内容默认折叠，点击展开/收起
        ReasoningBubble(part)
    } else {
        TextBubble(part)
    }
}

// 普通文本消息
@Composable
private fun TextBubble(part: MessagePart) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            part.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(14.dp),
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}

// 推理内容：可折叠，默认只显示摘要
@Composable
private fun ReasoningBubble(part: MessagePart) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = ReasoningBg),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    if (expanded) "💭 推理过程 ▼" else "💭 推理过程 ▶",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Text(
                    part.text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 折叠时显示摘要预览
            if (!expanded && part.text.isNotBlank()) {
                Text(
                    part.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
