package com.opencode.remote.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.remote.data.MessageInfoData

// 消息开销：单行紧凑显示 token 和费用
@Composable
fun MessageInfoBar(info: MessageInfoData) {
    val tokens = info.tokens
    val cost = info.cost
    if (tokens == null && (cost == null || cost <= 0)) return

    val parts = mutableListOf<String>()
    if (tokens != null) {
        parts.add("↑${tokens.input} ↓${tokens.output}")
        if (tokens.reasoning > 0) parts.add("💭${tokens.reasoning}")
    }
    if (cost != null && cost > 0) parts.add("$${String.format("%.3f", cost)}")

    Text(
        parts.joinToString("  "),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
