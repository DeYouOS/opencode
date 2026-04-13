package com.opencode.remote.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opencode.remote.viewmodel.MessagePart

// 消息气泡：reasoning 由 showReasoning 控制是否显示
@Composable
fun MessageBubble(part: MessagePart, showReasoning: Boolean) {
    if (part.type == "reasoning") {
        if (showReasoning) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                elevation = CardDefaults.cardElevation(0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(part.text, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp))
            }
        }
        // 不显示时完全隐藏，不占空间
    } else {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(part.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp),
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
        }
    }
}

// 文本消息气泡
@Composable
private fun TextBubble(part: MessagePart, bgColor: Color = Color.White, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            part.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(14.dp),
            color = textColor,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}
