package com.opencode.intellij.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * FileEditorProvider：拦截 OpencodeVirtualFile，
 * 在编辑器主区域（和代码文件并排的标签页）创建终端编辑器。
 */
class OpencodeEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile) = file is OpencodeVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return OpencodeEditor(project, file as OpencodeVirtualFile)
    }

    override fun getEditorTypeId() = "OpencodeTerminalEditor"

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
