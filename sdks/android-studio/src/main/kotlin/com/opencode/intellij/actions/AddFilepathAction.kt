package com.opencode.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.opencode.intellij.editor.OpencodeVirtualFile
import com.opencode.intellij.terminal.OpencodeTerminalManager
import com.opencode.intellij.util.FileContext

/**
 * 将当前文件路径和选区引用发送到 opencode 终端。
 * 优先通过编辑器标签页中的 OpencodeVirtualFile 端口走 HTTP API，
 * 回退到终端工具窗口中的终端。
 * 快捷键：Ctrl+Alt+K
 */
class AddFilepathAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ref = FileContext.active(project) ?: return
        val manager = OpencodeTerminalManager.getInstance(project)

        // 优先查找编辑器标签页中的 opencode
        val vf = FileEditorManager.getInstance(project)
            .openFiles.filterIsInstance<OpencodeVirtualFile>().firstOrNull()
        if (vf != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                manager.postAppendPrompt(vf.port, ref)
            }
            FileEditorManager.getInstance(project).openFile(vf, true)
            return
        }

        // 回退到终端工具窗口
        manager.sendFileRef(ref)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}
