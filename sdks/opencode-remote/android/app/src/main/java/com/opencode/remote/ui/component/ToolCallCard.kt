package com.opencode.remote.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.remote.ui.theme.StatusBusy
import com.opencode.remote.ui.theme.StatusError
import com.opencode.remote.ui.theme.StatusIdle
import com.opencode.remote.ui.theme.ToolBg
import com.opencode.remote.viewmodel.ToolInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 工具调用卡片：根据工具类型分模板渲染
@Composable
fun ToolCallCard(tool: ToolInfo) {
    var expanded by remember { mutableStateOf(true) }

    val accent = when (tool.status) {
        "completed" -> StatusIdle
        "running" -> StatusBusy
        "error" -> StatusError
        else -> Color(0xFF9E9E9E)
    }
    val icon = toolIcon(tool.tool, tool.status)
    val hasDetail = tool.input != null || !tool.output.isNullOrBlank() || !tool.error.isNullOrBlank()

    Card(
        Modifier.fillMaxWidth().clickable(enabled = hasDetail) { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = ToolBg),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            // 左侧状态色条
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(12.dp).weight(1f)) {
                // 标题行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$icon ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        toolLabel(tool.tool),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(tool.status, accent)
                    if (hasDetail) {
                        Text(
                            if (expanded) " ▼" else " ▶",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 紧凑摘要行（折叠/展开都显示）
                ToolSummary(tool)

                // 展开的详情
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(Modifier.padding(top = 8.dp)) {
                        ToolDetail(tool)
                        if (!tool.error.isNullOrBlank()) {
                            Text(
                                "❗ ${tool.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusError,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 根据工具类型返回图标
private fun toolIcon(tool: String, status: String): String {
    if (status == "error") return "❌"
    return when (tool) {
        "bash" -> "⚡"
        "read" -> "📄"
        "write" -> "✏️"
        "edit", "multiedit", "apply_patch" -> "🔧"
        "grep" -> "🔍"
        "glob" -> "📁"
        "list" -> "📂"
        "lsp" -> "🧠"
        "task" -> "🤖"
        "webfetch", "web_search_exa", "websearch" -> "🌐"
        "todowrite" -> "📋"
        "codesearch" -> "🔎"
        "skill" -> "🎯"
        "question" -> "❓"
        "plan_enter", "plan_exit" -> "📝"
        "invalid" -> "⚠️"
        else -> when (status) {
            "completed" -> "✅"
            "running" -> "⏳"
            else -> "⏱"
        }
    }
}

private fun toolLabel(tool: String): String = when (tool) {
    "bash" -> "终端命令"
    "read" -> "读取文件"
    "write" -> "写入文件"
    "edit" -> "编辑文件"
    "multiedit" -> "批量编辑"
    "apply_patch" -> "应用补丁"
    "grep" -> "搜索内容"
    "glob" -> "搜索文件"
    "list" -> "列出目录"
    "lsp" -> "LSP 分析"
    "task" -> "子任务"
    "webfetch" -> "网页获取"
    "websearch", "web_search_exa" -> "网络搜索"
    "codesearch" -> "代码搜索"
    "todowrite" -> "任务列表"
    "skill" -> "技能加载"
    "question" -> "用户提问"
    "plan_enter" -> "进入规划"
    "plan_exit" -> "退出规划"
    "invalid" -> "参数错误"
    else -> tool
}

// 从 input JSON 中安全读取字符串字段
private fun JsonObject.str(key: String): String? =
    get(key)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

// 紧凑摘要：始终可见，单行预览关键信息
@Composable
private fun ToolSummary(tool: ToolInfo) {
    val input = tool.input ?: return
    val summary = when (tool.tool) {
        "bash" -> input.str("command")
        "read" -> input.str("filePath")?.let { p ->
            val offset = input.str("offset")
            if (offset != null) "$p:$offset" else p
        }
        "write" -> input.str("filePath")
        "edit", "multiedit" -> input.str("filePath")
        "grep" -> {
            val pattern = input.str("pattern") ?: ""
            val inc = input.str("include")
            if (inc != null) "/$pattern/ ($inc)" else "/$pattern/"
        }
        "glob" -> input.str("pattern")
        "lsp" -> {
            val op = input.str("operation") ?: ""
            val fp = input.str("filePath")?.substringAfterLast("/") ?: ""
            val line = input.str("line")
            "$op $fp${if (line != null) ":$line" else ""}"
        }
        "task" -> input.str("description") ?: input.str("subagent_type")
        "list" -> input.str("path")
        "codesearch" -> input.str("query")
        "websearch" -> input.str("query")
        "skill" -> input.str("name")
        "apply_patch" -> input.str("patchText")?.take(60)
        "question" -> {
            val count = tool.input?.get("questions")?.jsonArray?.size ?: 0
            "${count}个问题"
        }
        "plan_enter" -> "进入规划模式"
        "plan_exit" -> "退出规划模式"
        "invalid" -> input.str("error")?.take(60)
        "webfetch", "web_search_exa" -> input.str("url") ?: input.str("query")
        else -> tool.title
    } ?: tool.title ?: return

    Text(
        summary,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// 展开后的详情：按工具类型分模板
@Composable
private fun ToolDetail(tool: ToolInfo) {
    when (tool.tool) {
        "bash" -> BashDetail(tool)
        "read" -> ReadDetail(tool)
        "write" -> WriteDetail(tool)
        "edit", "multiedit" -> EditDetail(tool)
        "grep" -> GrepDetail(tool)
        "glob" -> GlobDetail(tool)
        "lsp" -> LspDetail(tool)
        "task" -> TaskDetail(tool)
        "todowrite" -> TodoWriteDetail(tool)
        "list" -> ListDetail(tool)
        "codesearch" -> CodesearchDetail(tool)
        "apply_patch" -> PatchDetail(tool)
        "skill" -> SkillDetail(tool)
        "question" -> QuestionToolDetail(tool)
        "plan_enter", "plan_exit" -> OutputOnlyDetail(tool)
        "invalid" -> InvalidToolDetail(tool)
        "webfetch", "web_search_exa", "websearch" -> WebDetail(tool)
        else -> GenericDetail(tool)
    }
}

// ── bash ──
@Composable
private fun BashDetail(tool: ToolInfo) {
    val cmd = tool.input?.str("command")
    val desc = tool.input?.str("description")
    if (desc != null) {
        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF555555))
        Spacer(Modifier.height(4.dp))
    }
    if (cmd != null) CodeBlock(cmd, Color(0xFF1A1A1A), Color(0xFFE8E8E8))
    OutputBlock(tool.output)
}

// ── read ──
@Composable
private fun ReadDetail(tool: ToolInfo) {
    val path = tool.input?.str("filePath") ?: return
    FilePath(path)
    val offset = tool.input?.str("offset")
    val limit = tool.input?.str("limit")
    if (offset != null || limit != null) {
        val range = buildString {
            if (offset != null) append("行 $offset 起")
            if (limit != null) { if (isNotEmpty()) append("，"); append("最多 $limit 行") }
        }
        Text(range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutputBlock(tool.output, maxLines = 30)
}

// ── write ──
@Composable
private fun WriteDetail(tool: ToolInfo) {
    val path = tool.input?.str("filePath") ?: return
    FilePath(path)
    OutputBlock(tool.output)
}

// ── edit / multiedit ──
@Composable
private fun EditDetail(tool: ToolInfo) {
    val path = tool.input?.str("filePath") ?: return
    FilePath(path)
    val old = tool.input?.str("oldString")
    val new = tool.input?.str("newString")
    if (old != null && new != null) {
        Spacer(Modifier.height(4.dp))
        DiffBlock(old, new)
    }
    OutputBlock(tool.output)
}

// ── grep ──
@Composable
private fun GrepDetail(tool: ToolInfo) {
    val pattern = tool.input?.str("pattern")
    val path = tool.input?.str("path")
    val include = tool.input?.str("include")
    if (pattern != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("模式")
            Spacer(Modifier.width(4.dp))
            InlineCode(pattern)
        }
    }
    if (include != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Label("过滤")
            Spacer(Modifier.width(4.dp))
            InlineCode(include)
        }
    }
    if (path != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Label("目录")
            Spacer(Modifier.width(4.dp))
            Text(path, style = mono11, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    OutputBlock(tool.output, maxLines = 30)
}

// ── glob ──
@Composable
private fun GlobDetail(tool: ToolInfo) {
    val pattern = tool.input?.str("pattern")
    val path = tool.input?.str("path")
    if (pattern != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("模式")
            Spacer(Modifier.width(4.dp))
            InlineCode(pattern)
        }
    }
    if (path != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Label("目录")
            Spacer(Modifier.width(4.dp))
            Text(path, style = mono11, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    OutputBlock(tool.output, maxLines = 30)
}

// ── lsp ──
@Composable
private fun LspDetail(tool: ToolInfo) {
    val op = tool.input?.str("operation") ?: ""
    val fp = tool.input?.str("filePath") ?: ""
    val line = tool.input?.str("line")
    val char = tool.input?.str("character")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("操作")
        Spacer(Modifier.width(4.dp))
        InlineCode(op)
    }
    FilePath(fp)
    if (line != null) {
        Text(
            "位置: $line${if (char != null) ":$char" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    OutputBlock(tool.output, maxLines = 20)
}

// ── task ──
@Composable
private fun TaskDetail(tool: ToolInfo) {
    val desc = tool.input?.str("description")
    val agent = tool.input?.str("subagent_type")
    val prompt = tool.input?.str("prompt")
    if (agent != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("代理")
            Spacer(Modifier.width(4.dp))
            InlineCode(agent)
        }
    }
    if (desc != null) {
        Text(desc, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
    }
    if (prompt != null) {
        Spacer(Modifier.height(4.dp))
        CodeBlock(prompt, Color(0xFF333333), Color(0xFFF0F0F0), maxLines = 10)
    }
    OutputBlock(tool.output, maxLines = 20)
}

// ── todowrite ──
@Composable
private fun TodoWriteDetail(tool: ToolInfo) {
    val todosArr = tool.input?.get("todos")?.jsonArray ?: return
    Column(Modifier.fillMaxWidth()) {
        todosArr.forEach { elem ->
            val obj = elem.jsonObject
            val content = obj["content"]?.jsonPrimitive?.content ?: ""
            val status = obj["status"]?.jsonPrimitive?.content ?: "pending"
            val priority = obj["priority"]?.jsonPrimitive?.content
            val icon = when (status) {
                "completed" -> "✅"
                "in_progress" -> "🔄"
                "cancelled" -> "❌"
                else -> "⬜"
            }
            val priColor = when (priority) {
                "high" -> StatusError
                "medium" -> Color(0xFFFFA000)
                else -> null
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(icon, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (status == "completed") Color(0xFF999999) else Color(0xFF333333)
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (priColor != null) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = priColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            priority ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = priColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── list ──
@Composable
private fun ListDetail(tool: ToolInfo) {
    val path = tool.input?.str("path")
    if (path != null) FilePath(path)
    OutputBlock(tool.output, maxLines = 30)
}

// ── codesearch ──
@Composable
private fun CodesearchDetail(tool: ToolInfo) {
    val query = tool.input?.str("query")
    if (query != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("查询")
            Spacer(Modifier.width(4.dp))
            InlineCode(query)
        }
    }
    OutputBlock(tool.output, maxLines = 30)
}

// ── apply_patch ──
@Composable
private fun PatchDetail(tool: ToolInfo) {
    val patch = tool.input?.str("patchText")
    if (patch != null) {
        CodeBlock(patch.take(2000), Color(0xFF333333), Color(0xFFF0F0F0), maxLines = 20)
    }
    OutputBlock(tool.output)
}

// ── skill ──
@Composable
private fun SkillDetail(tool: ToolInfo) {
    val name = tool.input?.str("name")
    if (name != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("技能")
            Spacer(Modifier.width(4.dp))
            InlineCode(name)
        }
    }
    OutputBlock(tool.output)
}

// ── question ──
@Composable
private fun QuestionToolDetail(tool: ToolInfo) {
    val questions = tool.input?.get("questions")?.jsonArray
    if (questions != null) {
        questions.forEachIndexed { i, q ->
            val obj = q.jsonObject
            val header = obj["header"]?.jsonPrimitive?.content ?: ""
            val question = obj["question"]?.jsonPrimitive?.content ?: ""
            Text("${i + 1}. $header", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(question, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
    OutputBlock(tool.output)
}

// ── invalid ──
@Composable
private fun InvalidToolDetail(tool: ToolInfo) {
    val error = tool.input?.str("error")
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = StatusError)
    }
    OutputBlock(tool.output)
}

// ── output only ──
@Composable
private fun OutputOnlyDetail(tool: ToolInfo) {
    OutputBlock(tool.output)
}

// ── webfetch / search ──
@Composable
private fun WebDetail(tool: ToolInfo) {
    val url = tool.input?.str("url")
    val query = tool.input?.str("query")
    if (url != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("URL")
            Spacer(Modifier.width(4.dp))
            Text(url, style = mono11, color = Color(0xFF1565C0), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (query != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("查询")
            Spacer(Modifier.width(4.dp))
            InlineCode(query)
        }
    }
    OutputBlock(tool.output, maxLines = 20)
}

// ── 通用 fallback ──
@Composable
private fun GenericDetail(tool: ToolInfo) {
    if (tool.input != null) {
        val pretty = try {
            Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), tool.input)
        } catch (_: Exception) { tool.input.toString() }
        CodeBlock(pretty, Color(0xFF333333), Color(0xFFEEEEEE))
    }
    OutputBlock(tool.output)
}

// ── 共享基础组件 ──

private val mono11 = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    lineHeight = 16.sp
)

@Composable
private fun StatusChip(status: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            when (status) { "completed" -> "完成"; "running" -> "运行中"; "error" -> "错误"; else -> status },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun Label(text: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE0E0E0)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF555555),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun InlineCode(text: String) {
    Text(
        text, style = mono11, color = Color(0xFF333333),
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun FilePath(path: String) {
    val name = path.substringAfterLast("/")
    val dir = path.substringBeforeLast("/", "")
    Text(
        buildAnnotatedString {
            if (dir.isNotEmpty()) {
                withStyle(SpanStyle(color = Color(0xFF999999), fontSize = 10.sp)) { append("$dir/") }
            }
            withStyle(SpanStyle(color = Color(0xFF333333), fontWeight = FontWeight.Medium, fontSize = 11.sp)) { append(name) }
        },
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 2.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

// 代码块：深色文字 + 浅色背景
@Composable
private fun CodeBlock(code: String, fg: Color, bg: Color, maxLines: Int = 15) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            code, style = mono11.copy(color = fg),
            modifier = Modifier.padding(8.dp).horizontalScroll(rememberScrollState()),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 输出结果块
@Composable
private fun OutputBlock(output: String?, maxLines: Int = 15) {
    if (output.isNullOrBlank()) return
    Column(Modifier.padding(top = 6.dp)) {
        Label("输出")
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            color = Color(0xFFEEEEEE),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                output, style = mono11.copy(color = Color(0xFF444444)),
                modifier = Modifier.padding(8.dp),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 简易 diff 视图：红色删除行 + 绿色新增行
@Composable
private fun DiffBlock(old: String, new: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFAFAFA),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            old.lines().take(8).forEach { line ->
                Text(
                    "- $line",
                    style = mono11.copy(color = Color(0xFFB71C1C)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (old.lines().size > 8) {
                Text("  ... (${old.lines().size - 8} more)", style = mono11.copy(color = Color(0xFF999999)))
            }
            Spacer(Modifier.height(2.dp))
            new.lines().take(8).forEach { line ->
                Text(
                    "+ $line",
                    style = mono11.copy(color = Color(0xFF1B5E20)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (new.lines().size > 8) {
                Text("  ... (${new.lines().size - 8} more)", style = mono11.copy(color = Color(0xFF999999)))
            }
        }
    }
}
