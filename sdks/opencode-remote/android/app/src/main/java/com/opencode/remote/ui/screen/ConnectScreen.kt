package com.opencode.remote.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.WsState
import com.opencode.remote.data.loadConfig
import com.opencode.remote.viewmodel.RemoteViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(vm: RemoteViewModel, onConnected: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    val state by vm.client.state.collectAsState()

    LaunchedEffect(Unit) {
        val cfg = ctx.loadConfig()
        url = cfg.url
        token = cfg.token
    }

    // 连接成功后自动返回上一页
    LaunchedEffect(state) {
        if (state is WsState.Connected) {
            kotlinx.coroutines.delay(300)
            onConnected()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器设置") },
                navigationIcon = {
                    IconButton(onClick = onConnected) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Relay 服务器配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("服务器地址") },
                placeholder = { Text("ws://your-server:3100/ws") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(24.dp))

            when (state) {
                is WsState.Connecting -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在连接...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is WsState.Connected -> Text(
                    "已连接",
                    color = Color(0xFF4CAF50),
                    style = MaterialTheme.typography.bodySmall
                )
                is WsState.Error -> Text(
                    (state as WsState.Error).msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                else -> {}
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { scope.launch { vm.connect(url, token) } },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = url.isNotBlank() && token.isNotBlank() && state !is WsState.Connecting,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存并连接")
            }
        }
    }
}
