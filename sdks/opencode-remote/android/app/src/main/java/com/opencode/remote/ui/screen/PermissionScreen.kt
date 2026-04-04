package com.opencode.remote.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.ui.component.PermissionCard
import com.opencode.remote.viewmodel.RemoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(vm: RemoteViewModel, onBack: () -> Unit) {
    val permissions by vm.permissions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限请求 (${permissions.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (permissions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无权限请求")
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(permissions, key = { it.id }) { perm ->
                    PermissionCard(
                        permission = perm,
                        onReply = { response ->
                            vm.replyPermission(perm.sessionID, perm.id, response)
                        }
                    )
                }
            }
        }
    }
}
