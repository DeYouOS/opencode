package com.opencode.remote.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.PermissionData

// 权限请求卡片
@Composable
fun PermissionCard(permission: PermissionData, onReply: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(permission.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "类型: ${permission.kind}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onReply("once") },
                    shape = RoundedCornerShape(8.dp)
                ) { Text("允许一次") }
                Button(
                    onClick = { onReply("always") },
                    shape = RoundedCornerShape(8.dp)
                ) { Text("始终允许") }
                OutlinedButton(
                    onClick = { onReply("reject") },
                    shape = RoundedCornerShape(8.dp)
                ) { Text("拒绝") }
            }
        }
    }
}
