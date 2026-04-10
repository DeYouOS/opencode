package com.opencode.remote.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opencode.remote.ui.component.MessageBubble
import com.opencode.remote.ui.component.MessageInfoBar
import com.opencode.remote.ui.component.PermissionCard
import com.opencode.remote.ui.component.ToolCallCard
import com.opencode.remote.ui.theme.StatusBusy
import com.opencode.remote.viewmodel.RemoteViewModel
import com.opencode.remote.viewmodel.TimelineItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: RemoteViewModel, sessionID: String, onBack: () -> Unit) {
    val sessions by vm.sessions.collectAsState()
    val timelineMap by vm.timeline.collectAsState()
    val todoMap by vm.todos.collectAsState()
    val infoMap by vm.msgInfo.collectAsState()
    val session = sessions.find { it.id == sessionID }
    // 按 seq 排序确保时间线顺序正确
    val items = timelineMap[sessionID].orEmpty().sortedBy { it.seq }
    val todos = todoMap[sessionID].orEmpty()
    val info = infoMap[sessionID]
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 时间线更新时自动滚动到底部
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.title ?: sessionID.take(8))
                        // 显示会话状态和 token 开销摘要
                        val subtitle = buildString {
                            when (session?.status) {
                                "busy" -> append("⚡ 工作中")
                                "retry" -> append("🔄 重试中")
                                else -> append("空闲")
                            }
                            if (info?.tokens != null) {
                                append(" · ${info.tokens.input + info.tokens.output}tok")
                            }
                            if (info?.cost != null && info.cost > 0) {
                                append(" · $${String.format("%.4f", info.cost)}")
                            }
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (session?.status == "busy") StatusBusy else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (session?.status == "busy") {
                        IconButton(onClick = { vm.abortSession(sessionID) }) {
                            Icon(Icons.Default.Stop, "中止", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 2.dp,
                color = Color.White
            ) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入消息…", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                vm.sendMessage(sessionID, input.trim())
                                input = ""
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "发送", modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        containerColor = Color(0xFFFAFAFA)
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 按时间线顺序交织渲染消息、工具、任务列表、开销信息
            items(items, key = { "${it::class.simpleName}_${it.seq}" }) { item ->
                when (item) {
                    is TimelineItem.Msg -> MessageBubble(item.part)
                    is TimelineItem.Tool -> ToolCallCard(item.info)
                    is TimelineItem.Info -> MessageInfoBar(item.info)
                    is TimelineItem.Perm -> PermissionCard(item.data) { response ->
                        vm.replyPermission(item.data.sessionID, item.data.id, response)
                    }
                    is TimelineItem.Todo -> {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(1.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("📋 任务列表", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
                                item.items.forEach { todo ->
                                    val icon = when (todo.status) {
                                        "completed" -> "✅"
                                        "in_progress" -> "🔄"
                                        "cancelled" -> "❌"
                                        else -> "⬜"
                                    }
                                    val pri = when (todo.priority) {
                                        "high" -> " 🔴"
                                        "medium" -> " 🟡"
                                        else -> ""
                                    }
                                    Text(
                                        "$icon ${todo.content}$pri",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 如果时间线里没有 Todo 但有独立的 todos 数据，补充显示
            if (todos.isNotEmpty() && items.none { it is TimelineItem.Todo }) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("📋 任务列表", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            todos.forEach { todo ->
                                val icon = when (todo.status) {
                                    "completed" -> "✅"
                                    "in_progress" -> "🔄"
                                    "cancelled" -> "❌"
                                    else -> "⬜"
                                }
                                Text(
                                    "$icon ${todo.content}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
