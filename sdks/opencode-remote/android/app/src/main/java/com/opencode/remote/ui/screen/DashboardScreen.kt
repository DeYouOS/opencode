package com.opencode.remote.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.WsState
import com.opencode.remote.ui.component.StatusBar
import com.opencode.remote.ui.theme.StatusBusy
import com.opencode.remote.ui.theme.StatusIdle
import com.opencode.remote.ui.theme.StatusRetry
import com.opencode.remote.viewmodel.RemoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: RemoteViewModel,
    onSession: (String) -> Unit,
    onPermissions: () -> Unit
) {
    val sessions by vm.sessions.collectAsState()
    val permissions by vm.permissions.collectAsState()
    val state by vm.client.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("会话列表") },
                actions = {
                    if (permissions.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text("${permissions.size}") } }) {
                            IconButton(onClick = onPermissions) {
                                Icon(Icons.Default.Security, "权限请求")
                            }
                        }
                    }
                    IconButton(onClick = { vm.createSession() }) {
                        Icon(Icons.Default.Add, "新建会话")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            StatusBar(state)

            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无会话", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn {
                    items(sessions) { session ->
                        ListItem(
                            headlineContent = { Text(session.title.ifBlank { session.id.take(8) }) },
                            supportingContent = {
                                val label = when (session.status) {
                                    "busy" -> "工作中"
                                    "retry" -> "重试中"
                                    else -> "空闲"
                                }
                                Text(label)
                            },
                            leadingContent = {
                                val color = when (session.status) {
                                    "busy" -> StatusBusy
                                    "retry" -> StatusRetry
                                    else -> StatusIdle
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = color,
                                    modifier = Modifier.size(12.dp)
                                ) {}
                            },
                            modifier = Modifier.clickable { onSession(session.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
