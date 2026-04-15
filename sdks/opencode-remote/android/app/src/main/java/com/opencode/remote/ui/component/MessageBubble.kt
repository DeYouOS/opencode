package com.opencode.remote.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opencode.remote.viewmodel.MessagePart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// 消息气泡：普通文本直接显示，reasoning 逐字打印动画
@Composable
fun MessageBubble(part: MessagePart, showReasoning: Boolean) {
    if (part.type == "reasoning") {
        if (!showReasoning) return
        TypewriterBubble(part.text)
    } else {
        TextBubble(part.text)
    }
}

// 普通文本气泡
@Composable
private fun TextBubble(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(14.dp),
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
    }
}

// 推理内容：逐字打印效果，灰色背景
@Composable
private fun TypewriterBubble(fullText: String) {
    // 不用 fullText 做 key，避免 delta 更新时重置动画
    var charCount by remember { mutableStateOf(0) }
    // 记住上一次的文本长度，检测增量
    var prevLength by remember { mutableStateOf(0) }

    // 当有新增文本时，启动追加动画
    LaunchedEffect(fullText.length) {
        if (fullText.length > prevLength) {
            // 有新增内容，从当前位置继续动画到新长度
            val delayMs = if (fullText.length < 100) 30L else if (fullText.length < 500) 15L else 8L
            val target = fullText.length
            while (charCount < target) {
                delay(delayMs)
                charCount = (charCount + 1).coerceAtMost(target)
            }
        } else if (fullText.length < charCount) {
            // 文本被截断（不应该发生），直接同步
            charCount = fullText.length
        }
        prevLength = fullText.length
    }

    val displayedText = fullText.take(charCount)

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(displayedText, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp))
    }
}
