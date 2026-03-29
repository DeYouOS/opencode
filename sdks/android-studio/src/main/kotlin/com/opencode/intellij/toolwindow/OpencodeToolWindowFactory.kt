package com.opencode.intellij.toolwindow

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory
import com.opencode.intellij.editor.OpencodeVirtualFile
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 侧边栏图标入口。每次点击图标都会在编辑器主区域打开 opencode 终端，
 * 通过监听 ToolWindow 激活事件实现，面板自动收起。
 */
class OpencodeToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel()
        panel.add(JLabel("opencode"))
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        // 监听每次 ToolWindow 被激活，立即打开编辑器标签页并收起面板
        project.messageBus.connect().subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(tw: ToolWindow) {
                    if (tw.id != "opencode") return
                    invokeLater {
                        openInEditor(project)
                        tw.hide()
                    }
                }
            }
        )
    }

    private fun openInEditor(project: Project) {
        val fem = FileEditorManager.getInstance(project)
        val existing = fem.openFiles.filterIsInstance<OpencodeVirtualFile>().firstOrNull()
        if (existing != null) {
            fem.openFile(existing, true)
            return
        }
        val port = (16384..65535).random()
        fem.openFile(OpencodeVirtualFile(port), true)
    }
}
