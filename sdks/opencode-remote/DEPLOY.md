# OpenCode Remote 部署信息

## 架构

```
Plugin (OpenCode 本地) ←→ Relay Server (远程中继) ←→ Phone App (Android)
```

- **plugin/** — OpenCode 插件，连接 relay server 转发事件
- **server/** — Bun/Hono 中继服务器，WebSocket 双向转发
- **shared/** — 通信协议定义（Plugin ↔ Relay ↔ Phone）
- **android/** — Kotlin/Compose 手机客户端

## Relay Server

| 项目        | 值                                                                                                             |
| ----------- | -------------------------------------------------------------------------------------------------------------- |
| 服务器 IP   | `101.34.243.224`                                                                                               |
| SSH 用户    | `root`                                                                                                         |
| SSH 密码    | `Aa12345678`                                                                                                   |
| 部署路径    | `/opt/opencode-relay/`                                                                                         |
| server 路径 | `/opt/opencode-relay/server/`                                                                                  |
| shared 路径 | `/opt/opencode-relay/shared/`                                                                                  |
| 运行端口    | `3100`                                                                                                         |
| WS 端点     | `ws://101.34.243.224:3100/ws`                                                                                  |
| RELAY_TOKEN | `opencode-remote-2024`                                                                                         |
| 运行时      | Bun (`/usr/local/bin/bun`)                                                                                     |
| 心跳间隔    | 15 秒（plugin/phone 超时自动断开）                                                                             |
| 启动命令    | `RELAY_TOKEN=opencode-remote-2024 PORT=3100 nohup /usr/local/bin/bun run src/index.ts > /tmp/relay.log 2>&1 &` |

### 事件机制

| 事件                          | 方向    | 触发时机                                       |
| ----------------------------- | ------- | ---------------------------------------------- |
| `event.instance.info`         | → Phone | plugin 连接后、App 刷新时推送终端会话列表      |
| `event.instance.disconnected` | → Phone | plugin 断开时通知 App 移除对应终端             |
| `event.instance.sync`         | → Phone | phone 连接后推送当前所有在线 plugin instanceId |
| `event.command.list`          | → Phone | plugin 连接后推送可用斜杠命令列表              |

### 推送更新

只推送改动的文件（通常是 `server/src/` 和 `shared/`）：

```bash
# 推送改动的源码文件
sshpass -p 'Aa12345678' scp -o StrictHostKeyChecking=no \
  sdks/opencode-remote/server/src/<changed-file>.ts \
  root@101.34.243.224:/opt/opencode-relay/server/src/

sshpass -p 'Aa12345678' scp -o StrictHostKeyChecking=no \
  sdks/opencode-remote/shared/protocol.ts \
  root@101.34.243.224:/opt/opencode-relay/shared/

# 重启服务
sshpass -p 'Aa12345678' ssh -o StrictHostKeyChecking=no root@101.34.243.224 \
  'kill $(lsof -t -i:3100) 2>/dev/null; sleep 2; \
   cd /opt/opencode-relay/server && \
   RELAY_TOKEN=opencode-remote-2024 PORT=3100 \
   nohup /usr/local/bin/bun run src/index.ts > /tmp/relay.log 2>&1 & \
   sleep 1; tail -3 /tmp/relay.log'
```

### 健康检查

```bash
# 检查进程
sshpass -p 'Aa12345678' ssh -o StrictHostKeyChecking=no root@101.34.243.224 \
  'ps aux | grep "bun run" | grep -v grep; ss -tlnp | grep 3100'

# 查看日志
sshpass -p 'Aa12345678' ssh -o StrictHostKeyChecking=no root@101.34.243.224 \
  'tail -20 /tmp/relay.log'
```

## Android App

| 项目         | 值                                                      |
| ------------ | ------------------------------------------------------- |
| 包名         | `com.opencode.remote`                                   |
| 应用签名     | debug 签名（开发阶段）                                  |
| release 签名 | `app/release.jks`，alias `opencode`，密码 `opencode123` |

### 构建与安装

```bash
# 构建 debug APK
cd sdks/opencode-remote/android && ./gradlew assembleDebug

# 安装到本地 USB 设备
adb -s 3b7c279b install -r app/build/outputs/apk/debug/app-debug.apk

# 构建 release APK
./gradlew assembleRelease

# release 签名冲突时需先卸载再装
adb uninstall com.opencode.remote
adb install app/build/outputs/apk/release/app-release.apk
```

## OpenCode Plugin

已安装到 `/home/DeYouOS/opencode/.opencode/opencode.jsonc`，配置如下：

```jsonc
{
  "plugin": [
    {
      "module": "/home/DeYouOS/opencode/sdks/opencode-remote/plugin",
      "options": {
        "relay_url": "ws://101.34.243.224:3100/ws",
        "relay_token": "opencode-remote-2024",
      },
    },
  ],
}
```

插件依赖的 `@opencode-ai/plugin` 和 `@opencode-ai/sdk` 已在 `plugin/node_modules/` 中本地安装。

重启 OpenCode 后插件自动加载。
