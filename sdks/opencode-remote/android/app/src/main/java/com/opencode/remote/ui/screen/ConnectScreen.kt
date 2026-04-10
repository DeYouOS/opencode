package com.opencode.remote.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

    LaunchedEffect(state) {
        if (state is WsState.Connected) onConnected()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "OpenCode Remote",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "远程控制你的 AI 编程助手",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("服务器地址") },
                placeholder = { Text("ws://your-server:3100") },
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
                is WsState.Connecting -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
                is WsState.Error -> Text(
                    (state as WsState.Error).msg,
                    color = MaterialTheme.colorScheme.error
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
                Text("连接")
            }
        }
    }
}
