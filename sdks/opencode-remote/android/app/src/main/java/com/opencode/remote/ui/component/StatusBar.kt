package com.opencode.remote.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.WsState
import com.opencode.remote.ui.theme.StatusBusy
import com.opencode.remote.ui.theme.StatusError
import com.opencode.remote.ui.theme.StatusIdle

// 连接状态指示条
@Composable
fun StatusBar(state: WsState) {
    val color = when (state) {
        is WsState.Connected -> StatusIdle
        is WsState.Connecting -> StatusBusy
        is WsState.Error -> StatusError
        is WsState.Disconnected -> StatusError
    }
    val label = when (state) {
        is WsState.Connected -> "✅ 已连接"
        is WsState.Connecting -> "⏳ 连接中…"
        is WsState.Error -> "❌ 错误: ${state.msg}"
        is WsState.Disconnected -> "⚠️ 未连接"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
