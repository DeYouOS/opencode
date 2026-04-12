package com.opencode.remote.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.opencode.remote.data.WsState
import com.opencode.remote.data.loadConfig
import com.opencode.remote.ui.component.StatusBar
import com.opencode.remote.ui.theme.StatusBusy
import com.opencode.remote.viewmodel.RemoteViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: RemoteViewModel,
    onSession: (String) -> Unit,
    onPermissions: () -> Unit,
    onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by vm.sessions.collectAsState()
    val terminals by vm.terminals.collectAsState()
    val permissions by vm.permissions.collectAsState()
    val state by vm.client.state.collectAsState()

    // 启动时自动连接
    LaunchedEffect(Unit) {
        val cfg = ctx.loadConfig()
        vm.connect(cfg.url, cfg.token)
    }

    // 每个 session 对应的目录
    fun dirFor(sid: String): String {
        for (t in terminals.values) {
            if (t.sessions.any { it.id == sid }) return t.directory.substringAfterLast("/")
        }
        return ""
    }

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
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color(0xFFFAFAFA)
    ) { padding ->
        Column(Modifier.padding(padding)) {
            StatusBar(state)

            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state is WsState.Connecting) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("正在连接...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (state is WsState.Error) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("连接失败", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Text((state as WsState.Error).msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val cfg = ctx.loadConfig()
                                    vm.connect(cfg.url, cfg.token)
                                }
                            }) { Text("重试") }
                        }
                    } else {
                        Text("暂无会话", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val busy = session.status == "busy"
                        val dir = dirFor(session.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSession(session.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = when (session.status) {
                                    "busy" -> StatusBusy
                                    "retry" -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                },
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.title.ifBlank { "未命名会话" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (dir.isNotBlank()) {
                                    Text(
                                        dir,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (busy) {
                                Text("⚡", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
