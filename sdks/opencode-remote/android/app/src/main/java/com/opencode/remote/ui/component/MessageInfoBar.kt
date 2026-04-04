package com.opencode.remote.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.MessageInfoData

// 消息开销信息条：显示 token 用量和费用
@Composable
fun MessageInfoBar(info: MessageInfoData) {
    val tokens = info.tokens
    val cost = info.cost

    if (tokens == null && (cost == null || cost <= 0)) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tokens != null) {
                Text(
                    "📊 输入 ${tokens.input} · 输出 ${tokens.output}" +
                            if (tokens.reasoning > 0) " · 推理 ${tokens.reasoning}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (cost != null && cost > 0) {
                Text(
                    "💰 $${String.format("%.4f", cost)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
