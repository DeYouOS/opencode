package com.opencode.intellij.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.ui.TerminalWidget
import com.opencode.intellij.terminal.OpencodeTerminalManager
import com.opencode.intellij.util.FileContext
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * 在编辑器主区域内嵌 opencode 终端的 FileEditor 实现。
 * 打开后自动创建 shell widget 并执行 opencode --port {port}。
 */
class OpencodeEditor(
    private val project: Project,
    private val file: OpencodeVirtualFile
) : UserDataHolderBase(), FileEditor {

    companion object {
        private val log = logger<OpencodeEditor>()
    }

    private val panel = JPanel(BorderLayout())
    private var widget: TerminalWidget? = null

    init {
        try {
            val dir = project.basePath ?: ""
            val termManager = TerminalToolWindowManager.getInstance(project)
            val w = termManager.createShellWidget(dir, "opencode", false, false)
            widget = w
            panel.add(w.component, BorderLayout.CENTER)

            val manager = OpencodeTerminalManager.getInstance(project)
            manager.markExternal(w, file.port)
            w.sendCommandToExecute("opencode --port ${file.port}")

            // 后台等待就绪后发送文件上下文
            val ref = FileContext.active(project)
            if (ref != null) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    if (manager.waitForReady(file.port)) {
                        manager.postAppendPrompt(file.port, "In $ref")
                    }
                }
            }
        } catch (e: Throwable) {
            log.warn("创建 opencode 编辑器终端失败", e)
        }
    }

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent? = widget?.component

    override fun getName() = "opencode"

    override fun setState(state: FileEditorState) {}

    override fun isModified() = false

    override fun isValid() = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getFile(): VirtualFile = file

    /** 释放终端 widget 资源，通过 IntelliJ Disposer 框架清理 */
    override fun dispose() {
        widget?.let {
            try {
                Disposer.dispose(it)
            } catch (_: Throwable) {}
        }
    }
}
