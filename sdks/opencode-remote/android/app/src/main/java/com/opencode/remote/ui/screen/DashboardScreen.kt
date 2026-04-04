package com.opencode.remote.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val instance by vm.instance.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("会话列表")
                        // 显示实例信息（项目名/目录）
                        if (instance != null) {
                            Text(
                                "${instance!!.project} · ${instance!!.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
                    Text("暂无会话", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions) { session ->
                        // 每个会话用卡片展示
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onSession(session.id) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(1.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 状态圆点
                                val color = when (session.status) {
                                    "busy" -> StatusBusy
                                    "retry" -> StatusRetry
                                    else -> StatusIdle
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = color,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        session.title.ifBlank { session.id.take(8) },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    val label = when (session.status) {
                                        "busy" -> "⚡ 工作中"
                                        "retry" -> "🔄 重试中"
                                        else -> "空闲"
                                    }
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
