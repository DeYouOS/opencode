package com.opencode.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 浅色主题配色方案
private val Scheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF455A64),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F0),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF666666),
    outline = Color(0xFFE0E0E0),
    error = Color(0xFFD32F2F),
    onError = Color.White,
)

// 状态颜色
val StatusIdle = Color(0xFF4CAF50)
val StatusBusy = Color(0xFFFF9800)
val StatusError = Color(0xFFD32F2F)
val StatusRetry = Color(0xFFFFC107)

// 工具卡片背景色
val ToolBg = Color(0xFFF5F5F5)

// 推理区域背景色
val ReasoningBg = Color(0xFFFFF8E1)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
