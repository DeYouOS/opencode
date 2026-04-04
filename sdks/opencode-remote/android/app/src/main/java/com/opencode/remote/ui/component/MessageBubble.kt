package com.opencode.remote.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.remote.viewmodel.MessagePart

@Composable
fun MessageBubble(part: MessagePart) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (part.type == "reasoning")
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            if (part.type == "reasoning") {
                Text("💭 推理", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(2.dp))
            }
            Text(part.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
