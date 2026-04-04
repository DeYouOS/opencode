package com.opencode.remote.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.PermissionData

@Composable
fun PermissionCard(permission: PermissionData, onReply: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(permission.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("类型: ${permission.kind}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onReply("once") }) { Text("允许一次") }
                Button(onClick = { onReply("always") }) { Text("始终允许") }
                OutlinedButton(onClick = { onReply("reject") }) { Text("拒绝") }
            }
        }
    }
}
