package com.opencode.intellij.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.net.HttpURLConnection
import java.net.URI

/**
 * 项目级服务，管理 opencode 终端标签页的生命周期。
 *
 * 封装了终端的创建、复用、聚焦、命令发送和 HTTP 通信逻辑，
 * 使 Action 类保持轻量。
 */
@Service(Service.Level.PROJECT)
class OpencodeTerminalManager(private val project: Project) {

    companion object {
        private const val TAB_NAME = "opencode"

        /** 标记 Content 为 opencode 终端的 UserData Key */
        private val OPENCODE_KEY = Key.create<Boolean>("opencode.terminal")

        /** 存储该终端对应的 HTTP 端口 */
        private val PORT_KEY = Key.create<Int>("opencode.terminal.port")

        private val log = logger<OpencodeTerminalManager>()

        fun getInstance(project: Project): OpencodeTerminalManager =
            project.getService(OpencodeTerminalManager::class.java)
    }

    /**
     * 查找已有的 opencode 终端标签页并聚焦。
     * 如果不存在则创建新的。
     */
    fun openOrFocus() {
        val existing = locate()
        if (existing != null) {
            focus(existing)
            return
        }
        open()
    }

    /**
     * 无条件创建新的 opencode 终端标签页。
     */
    fun open() {
        val dir = project.basePath ?: return
        // 随机端口，范围 16384..65535
        val port = (16384..65535).random()
        val manager = TerminalToolWindowManager.getInstance(project)

        try {
            val widget = manager.createShellWidget(dir, TAB_NAME, true, true)
            mark(manager, widget, port)

            // 发送启动命令
            widget.sendCommandToExecute("opencode --port $port")

            // 后台等待 opencode HTTP 就绪，然后发送文件上下文
            val ref = com.opencode.intellij.util.FileContext.active(project)
            if (ref != null) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    if (waitReady(port)) {
                        appendPrompt(port, "In $ref")
                    }
                }
            }
        } catch (e: Throwable) {
            log.warn("创建 opencode 终端失败", e)
        }
    }

    /**
     * 向当前活跃的 opencode 终端追加文件路径引用。
     * 如果终端有关联的 HTTP 端口，通过 API 追加；否则直接发送文本。
     *
     * @param ref 文件引用字符串，如 @path/to/File.kt#L10-20
     */
    fun sendFileRef(ref: String) {
        val terminal = locate() ?: return
        val port = terminal.content.getUserData(PORT_KEY)
        if (port != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                appendPrompt(port, ref)
            }
        } else {
            // 没有端口信息时，直接通过 ttyConnector 写入
            try {
                terminal.widget.ttyConnector?.write(ref)
            } catch (e: Throwable) {
                log.warn("直接写入终端失败", e)
            }
        }
        focus(terminal)
    }

    /**
     * 供 ToolWindowFactory 调用：标记外部创建的 widget 并记录端口。
     */
    fun markExternal(widget: TerminalWidget, port: Int) {
        val manager = TerminalToolWindowManager.getInstance(project)
        mark(manager, widget, port)
    }

    /**
     * 供 ToolWindowFactory 调用：等待 opencode HTTP 就绪。
     */
    fun waitForReady(port: Int) = waitReady(port)

    /**
     * 供 ToolWindowFactory 调用：通过 HTTP 追加 prompt。
     */
    fun postAppendPrompt(port: Int, text: String) = appendPrompt(port, text)

    // ---- 内部方法 ----

    private data class Terminal(val widget: TerminalWidget, val content: Content)

    /**
     * 在所有终端 widget 中查找带有 opencode 标记的终端。
     */
    private fun locate(): Terminal? = try {
        val manager = TerminalToolWindowManager.getInstance(project)
        manager.terminalWidgets.asSequence().mapNotNull { widget ->
            val content = manager.getContainer(widget)?.content ?: return@mapNotNull null
            val marked = content.getUserData(OPENCODE_KEY) == true || content.displayName == TAB_NAME
            if (!marked) return@mapNotNull null
            Terminal(widget, content)
        }.firstOrNull()
    } catch (e: Throwable) {
        log.warn("查找 opencode 终端失败", e)
        null
    }

    /**
     * 聚焦指定的终端标签页：选中 Content、激活工具窗口、请求焦点。
     */
    private fun focus(terminal: Terminal) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            try {
                val manager = TerminalToolWindowManager.getInstance(project)
                val window = manager.toolWindow
                    ?: ToolWindowManager.getInstance(project)
                        .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
                    ?: return@invokeLater

                val cm = window.contentManager
                if (cm.selectedContent != terminal.content) {
                    cm.setSelectedContent(terminal.content, true)
                }
                window.activate({ terminal.widget.requestFocus() }, true)
            } catch (e: Throwable) {
                log.warn("聚焦 opencode 终端失败", e)
            }
        }
    }

    /**
     * 标记新创建的 widget 为 opencode 终端，并保存端口信息。
     */
    private fun mark(manager: TerminalToolWindowManager, widget: TerminalWidget, port: Int) {
        try {
            manager.getContainer(widget)?.content?.also { content ->
                content.putUserData(OPENCODE_KEY, true)
                content.putUserData(PORT_KEY, port)
                content.displayName = TAB_NAME
            }
        } catch (e: Throwable) {
            log.warn("标记 opencode 终端失败", e)
        }
    }

    /**
     * 轮询等待 opencode HTTP 服务就绪。
     * 最多等待 10 次（每次 300ms），共 3 秒。
     *
     * @param port opencode 监听的 HTTP 端口
     * @return 是否成功连接
     */
    private fun waitReady(port: Int): Boolean {
        repeat(10) {
            try {
                val conn = URI("http://localhost:$port/path").toURL()
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 200
                conn.readTimeout = 200
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) return true
            } catch (_: Throwable) {
                // 尚未就绪，继续等待
            }
            Thread.sleep(300)
        }
        return false
    }

    /**
     * 通过 HTTP POST 向 opencode TUI 追加提示文本。
     *
     * @param port opencode 监听的 HTTP 端口
     * @param text 要追加的文本内容
     */
    private fun appendPrompt(port: Int, text: String) {
        try {
            val conn = URI("http://localhost:$port/tui/append-prompt").toURL()
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.outputStream.use { out ->
                out.write("""{"text":"${text.replace("\"", "\\\"")}"}""".toByteArray())
            }
            conn.responseCode // 触发请求
        } catch (e: Throwable) {
            log.warn("向 opencode 追加 prompt 失败: $text", e)
        }
    }
}
