# opencode Android Studio / IntelliJ Plugin

将 [opencode](https://opencode.ai) AI 编码代理集成到 Android Studio 和 JetBrains IDE 中。

## 前提条件

需要先安装 [opencode CLI](https://opencode.ai)。

## 功能

- **快速启动** — `Ctrl+Esc` 打开 opencode 终端，已有终端则聚焦
- **新建会话** — `Ctrl+Shift+Esc` 强制新建 opencode 终端标签页
- **上下文感知** — 自动将当前编辑器文件和选区共享给 opencode
- **文件引用** — `Ctrl+Alt+K` 插入文件引用（如 `@File.kt#L37-42`）

## 兼容性

- Android Studio Meerkat (2024.3) 及更高版本
- IntelliJ IDEA 2024.3 及更高版本
- 所有基于 IntelliJ Platform 2024.3+ 的 JetBrains IDE

## 开发

```bash
# 克隆并进入插件目录
cd sdks/android-studio

# 构建插件
./gradlew buildPlugin

# 在沙盒 IDE 中运行调试
./gradlew runIde
```

### 修改代码

1. 修改 `src/main/kotlin/` 下的源码
2. 运行 `./gradlew runIde` 启动带插件的调试 IDE
3. 在调试 IDE 中测试功能

## 支持

遇到问题或有反馈，请在 https://github.com/anomalyco/opencode/issues 创建 issue。
