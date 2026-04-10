package com.opencode.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.opencode.intellij.editor.OpencodeVirtualFile

/**
 * 打开 opencode 编辑器标签页。
 * 如果已有则聚焦，否则新建。
 * 快捷键：Ctrl+Esc
 */
class OpenTerminalAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fem = FileEditorManager.getInstance(project)

        // 查找已有的 opencode 标签页
        val existing = fem.openFiles.filterIsInstance<OpencodeVirtualFile>().firstOrNull()
        if (existing != null) {
            fem.openFile(existing, true)
            return
        }

        // 新建
        val port = (16384..65535).random()
        fem.openFile(OpencodeVirtualFile(port), true)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}
