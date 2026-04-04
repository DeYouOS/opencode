package com.opencode.remote.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.ui.theme.StatusBusy
import com.opencode.remote.ui.theme.StatusError
import com.opencode.remote.ui.theme.StatusIdle
import com.opencode.remote.ui.theme.StatusRetry
import com.opencode.remote.viewmodel.ToolInfo

@Composable
fun ToolCallCard(tool: ToolInfo) {
    val color = when (tool.status) {
        "completed" -> StatusIdle
        "running" -> StatusBusy
        "error" -> StatusError
        else -> StatusRetry
    }
    val icon = when (tool.status) {
        "completed" -> "✅"
        "running" -> "⏳"
        "error" -> "❌"
        else -> "⏱"
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("$icon ${tool.tool}", style = MaterialTheme.typography.titleSmall)
            if (tool.title != null) {
                Text(tool.title, style = MaterialTheme.typography.bodySmall)
            }
            if (tool.error != null) {
                Text(tool.error, color = StatusError, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
