package com.opencode.intellij.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * 从当前编辑器中提取文件路径和选区信息，
 * 生成 opencode 能识别的 @路径#L行号 格式引用。
 */
object FileContext {

    /**
     * 获取当前活跃编辑器的文件引用。
     * 包含相对路径和选区行号（如果有选区的话）。
     *
     * @param project 当前项目
     * @return 文件引用字符串，如 "@src/Main.kt#L10-20"；无活跃编辑器时返回 null
     */
    fun active(project: Project): String? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val file = editor.virtualFile ?: return null
        val rel = relative(project, file) ?: return null
        return format(rel, editor)
    }

    /**
     * 计算文件相对于项目内容根的路径。
     * 如果文件不在任何内容根下，则相对于项目根目录。
     *
     * @param project 当前项目
     * @param file 目标虚拟文件
     * @return 相对路径字符串；无法计算时返回 null
     */
    private fun relative(project: Project, file: VirtualFile): String? {
        // 尝试找到文件所属的内容根
        val root = ProjectRootManager.getInstance(project)
            .contentRoots
            .firstOrNull { file.path.startsWith(it.path) }

        val base = root?.path ?: project.basePath ?: return null
        return try {
            Path.of(base).relativize(Path.of(file.path)).toString()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * 将相对路径和编辑器选区组合成 opencode 文件引用格式。
     *
     * 格式规则：
     * - 无选区：@path/to/File.kt
     * - 单行选区：@path/to/File.kt#L42
     * - 多行选区：@path/to/File.kt#L10-20
     *
     * @param path 相对路径
     * @param editor 编辑器实例
     * @return 格式化的文件引用
     */
    private fun format(path: String, editor: Editor): String {
        val ref = StringBuilder("@").append(path)

        val model = editor.selectionModel
        if (!model.hasSelection()) return ref.toString()

        val doc = editor.document
        // IntelliJ 行号从 0 开始，opencode 从 1 开始
        val start = doc.getLineNumber(model.selectionStart) + 1
        val end = doc.getLineNumber(model.selectionEnd) + 1

        ref.append("#L").append(start)
        if (end > start) {
            ref.append("-").append(end)
        }

        return ref.toString()
    }
}
