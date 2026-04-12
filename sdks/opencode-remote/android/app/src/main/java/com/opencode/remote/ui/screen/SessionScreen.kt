package com.opencode.remote.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.ModelRef
import com.opencode.remote.data.ProviderInfo
import com.opencode.remote.ui.component.MessageBubble
import com.opencode.remote.ui.component.MessageInfoBar
import com.opencode.remote.ui.component.PermissionCard
import com.opencode.remote.ui.component.ToolCallCard
import com.opencode.remote.ui.theme.StatusBusy
import com.opencode.remote.viewmodel.RemoteViewModel
import com.opencode.remote.viewmodel.TimelineItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: RemoteViewModel, sessionID: String, onBack: () -> Unit, onSession: (String) -> Unit) {
    val sessions by vm.sessions.collectAsState()
    val timelineMap by vm.timeline.collectAsState()
    val todoMap by vm.todos.collectAsState()
    val infoMap by vm.msgInfo.collectAsState()
    val providers by vm.providers.collectAsState()
    val selectedModel by vm.selectedModel.collectAsState()
    val session = sessions.find { it.id == sessionID }
    val items = timelineMap[sessionID].orEmpty().sortedBy { it.seq }
    val todos = todoMap[sessionID].orEmpty()
    val info = infoMap[sessionID]
    var input by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 新建会话后自动跳转到新会话
    LaunchedEffect(Unit) {
        vm.createdSession.collect { newID -> onSession(newID) }
    }

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
                    IconButton(onClick = { vm.createSession() }) {
                        Icon(Icons.Default.Add, "新建会话")
                    }
                    if (providers.isNotEmpty()) {
                        IconButton(onClick = { showModelPicker = true }) {
                            Icon(Icons.Default.Settings, "模型选择")
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
                    if (session?.status == "busy") {
                        Button(
                            onClick = { vm.abortSession(sessionID) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("停止生成", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
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
                    is TimelineItem.Question -> {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                            elevation = CardDefaults.cardElevation(1.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                item.data.questions.forEachIndexed { idx, q ->
                                    if (idx > 0) Spacer(Modifier.height(8.dp))
                                    Text(
                                        q.header.take(30),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(q.question, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(6.dp))
                                    q.options.forEach { opt ->
                                        Text(
                                            "• ${opt.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item.data.questions.firstOrNull()?.options?.firstOrNull()?.let { first ->
                                        Button(
                                            onClick = {
                                                vm.replyQuestion(
                                                    item.data.sessionID,
                                                    item.data.id,
                                                    first.label
                                                )
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) { Text("选择", style = MaterialTheme.typography.labelMedium) }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            vm.rejectQuestion(item.data.sessionID, item.data.id)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) { Text("跳过", style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }
                    is TimelineItem.ActionErr -> {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            elevation = CardDefaults.cardElevation(1.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "⚠ 操作失败: ${item.data.actionType}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.data.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
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

    if (showModelPicker) {
        ModelPickerDialog(
            providers = providers,
            selected = selectedModel,
            onSelect = { vm.selectModel(it); showModelPicker = false },
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun ModelPickerDialog(
    providers: List<ProviderInfo>,
    selected: ModelRef?,
    onSelect: (ModelRef?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    val isDefault = selected == null
                    Card(
                        onClick = { onSelect(null) },
                        colors = CardDefaults.cardColors(containerColor = if (isDefault) Color(0xFFE3F2FD) else Color.White)
                    ) {
                        Text("默认", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                for (p in providers) {
                    item {
                        Text(p.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(p.models) { m ->
                        val ref = ModelRef(p.id, m.id)
                        val isSelected = selected?.providerID == p.id && selected.modelID == m.id
                        Card(
                            onClick = { onSelect(ref) },
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, style = MaterialTheme.typography.bodyMedium)
                                    Text("${m.context / 1000}k ctx", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (m.reasoning) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE8F5E9)) {
                                        Text("推理", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
