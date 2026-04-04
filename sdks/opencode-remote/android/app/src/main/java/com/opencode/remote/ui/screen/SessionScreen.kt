package com.opencode.remote.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.ui.component.MessageBubble
import com.opencode.remote.ui.component.ToolCallCard
import com.opencode.remote.viewmodel.RemoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: RemoteViewModel, sessionID: String, onBack: () -> Unit) {
    val sessions by vm.sessions.collectAsState()
    val parts by vm.messages.collectAsState()
    val toolMap by vm.tools.collectAsState()
    val todoMap by vm.todos.collectAsState()
    val session = sessions.find { it.id == sessionID }
    val msgs = parts[sessionID].orEmpty()
    val tools = toolMap[sessionID].orEmpty()
    val todos = todoMap[sessionID].orEmpty()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.title ?: sessionID.take(8)) },
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
                }
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息…") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            vm.sendMessage(sessionID, input.trim())
                            input = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, "发送")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(msgs) { part ->
                MessageBubble(part)
            }
            items(tools) { tool ->
                ToolCallCard(tool)
            }
            if (todos.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("任务列表", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            todos.forEach { todo ->
                                val icon = when (todo.status) {
                                    "completed" -> "✅"
                                    "in_progress" -> "🔄"
                                    "cancelled" -> "❌"
                                    else -> "⬜"
                                }
                                Text("$icon ${todo.content}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
