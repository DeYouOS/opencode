package com.opencode.intellij.editor

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import javax.swing.Icon

/**
 * opencode 专用虚拟文件。
 * 通过 FileEditorManager.openFile 打开时，
 * OpencodeEditorProvider 会拦截并在编辑器主区域创建终端标签页。
 *
 * @param port opencode CLI 监听的 HTTP 端口
 */
class OpencodeVirtualFile(val port: Int) : LightVirtualFile("opencode", OpencodeFileType, "") {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpencodeVirtualFile) return false
        return port == other.port
    }

    override fun hashCode() = port

    override fun isWritable() = false
}

/**
 * 自定义文件类型，仅用于让 FileEditorProvider 识别。
 */
object OpencodeFileType : FileType {
    override fun getName() = "opencode"
    override fun getDescription() = "opencode Terminal"
    override fun getDefaultExtension() = ""
    override fun getIcon(): Icon? = null
    override fun isBinary() = true
    override fun isReadOnly() = true
}
