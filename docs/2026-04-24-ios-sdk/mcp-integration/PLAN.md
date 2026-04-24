# iOS SDK + MCP 集成方案

**日期**：2026-04-24
**状态**：待开始

---

## 一、通信架构

```
┌──────────────────┐         USB (iproxy)          ┌──────────────────┐
│    Mac (MCP)     │  ←── localhost:8080 ──→      │  iOS 设备 (SDK)   │
│                  │                               │  GCDWebServer     │
│  MCP Server      │                               │  port: 8080       │
└──────────────────┘                               └──────────────────┘
```

### 端口分配

| 平台 | localPort | devicePort | 隧道方式 |
|------|-----------|------------|---------|
| Android | 8080 | 8080 | `adb forward` |
| iOS | 8080 | 8080 | `iproxy` |

**注意**：同时连接 Android 和 iOS 时，需分端口：
- Android：本地 8080（`adb forward`）
- iOS：本地 8081（`iproxy 8081 8080`）

---

## 二、配置文件（接入项目根目录）

```json
// client-tools.json
{
  "platform": "ios",        // "ios" | "android"
  "localPort": 8080,        // MCP 连接本地端口
  "devicePort": 8080       // 设备上 SDK 监听端口
}
```

---

## 三、MCP 改造点

### 3.1 sdk-client.ts

从配置文件读取 `localPort`，替代硬编码的 `CLIENT_TOOLS_PORT` 环境变量。

```typescript
// 改动点
const PORT = config?.localPort ?? process.env.CLIENT_TOOLS_PORT ?? "8080";
const BASE_URL = `http://127.0.0.1:${PORT}`;
```

### 3.2 index.ts

读取 `client-tools.json`，根据 `platform` 决定隧道方式：

- `platform === "ios"`：自动后台执行 `iproxy`
- `platform === "android"`：自动执行 `adb forward`

```typescript
// 改动点
if (config.platform === "ios") {
  execSync("lsof -i :8080 | grep iproxy || (iproxy 8080 8080 &)", { stdio: "ignore" });
} else if (config.platform === "android") {
  execSync("adb forward tcp:8080 tcp:8080", { stdio: "ignore" });
}
```

### 3.3 配置文件搜索路径

从当前工作目录向上查找 `client-tools.json`，支持以下场景：

```
cd ~/project/ios-app    → 查找 ~/project/ios-app/client-tools.json
cd ~/project/ios-app/src → 查找 ~/project/ios-app/client-tools.json
```

---

## 四、iproxy 前置依赖

```bash
brew install libimobiledevice
```

需要 USB 连接 iOS 设备。

---

## 五、待实现

- [ ] MCP sdk-client.ts 支持读取 client-tools.json
- [ ] MCP index.ts 支持 iOS 平台自动 iproxy
- [ ] MCP index.ts 支持端口分离（同时 Android + iOS）
- [ ] 更新 client-tools.json 文档
- [ ] 更新 PLAN.md 中的通信架构章节
