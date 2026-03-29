package com.opencode.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.opencode.intellij.editor.OpencodeVirtualFile

/**
 * 强制新建 opencode 编辑器标签页。
 * 快捷键：Ctrl+Shift+Esc
 */
class OpenNewTerminalAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val port = (16384..65535).random()
        FileEditorManager.getInstance(project).openFile(OpencodeVirtualFile(port), true)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}
