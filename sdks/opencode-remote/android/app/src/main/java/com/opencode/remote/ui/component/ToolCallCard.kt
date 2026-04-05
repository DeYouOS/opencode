package com.opencode.remote.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.remote.ui.theme.StatusBusy
import com.opencode.remote.ui.theme.StatusError
import com.opencode.remote.ui.theme.StatusIdle
import com.opencode.remote.ui.theme.ToolBg
import com.opencode.remote.viewmodel.ToolInfo
import kotlinx.serialization.json.Json

// 工具调用卡片：可展开查看输入/输出详情
@Composable
fun ToolCallCard(tool: ToolInfo) {
    var expanded by remember { mutableStateOf(true) }

    // 根据工具状态选择左边框颜色
    val accent = when (tool.status) {
        "completed" -> StatusIdle
        "running" -> StatusBusy
        "error" -> StatusError
        else -> Color(0xFF9E9E9E)
    }
    val icon = when (tool.status) {
        "completed" -> "✅"
        "running" -> "⏳"
        "error" -> "❌"
        else -> "⏱"
    }
    // 判断是否有可展开的详情内容
    val hasDetail = tool.input != null || !tool.output.isNullOrBlank() || !tool.error.isNullOrBlank()

    Card(
        Modifier.fillMaxWidth().clickable(enabled = hasDetail) { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = ToolBg),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            // 左侧状态色条
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(Modifier.padding(12.dp).weight(1f)) {
                // 标题行：状态图标 + 工具名 + 展开箭头
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$icon ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        tool.tool,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    // 状态标签
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            tool.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (hasDetail) {
                        Text(
                            if (expanded) " ▼" else " ▶",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 副标题
                if (tool.title != null) {
                    Text(
                        tool.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // 展开的详情区域
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(Modifier.padding(top = 8.dp)) {
                        // 输入参数（JSON 美化输出）
                        if (tool.input != null) {
                            val pretty = try {
                                Json { prettyPrint = true }.encodeToString(
                                    kotlinx.serialization.json.JsonObject.serializer(), tool.input
                                )
                            } catch (_: Exception) { tool.input.toString() }
                            DetailSection("输入", pretty)
                        }
                        // 输出结果
                        if (!tool.output.isNullOrBlank()) {
                            DetailSection("输出", tool.output)
                        }
                        // 错误信息
                        if (!tool.error.isNullOrBlank()) {
                            Text(
                                "❗ ${tool.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusError,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 详情区块：标题 + 等宽字体代码内容，带浅灰背景
@Composable
private fun DetailSection(label: String, content: String) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            color = Color(0xFFEEEEEE),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                ),
                modifier = Modifier.padding(8.dp),
                maxLines = 20,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
