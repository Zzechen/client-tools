# Android SDK + Demo 设计规格

**日期**：2026-04-18  
**范围**：packages/android/sdk/ + packages/android/demo/

---

## 目标

实现 Android SDK，为运行时提供以下能力：
- 运行时修改视图属性（margin、padding、宽高）
- 页面切换事件回调
- WebView 叠加设计参考（透明度可调，拖拽控制）
- 运行时查询指定 id 的结构化信息（基础 + 样式）

**核心特性**：
- ContentProvider 自动初始化，零侵入业务代码
- 纯 HTTP API 接口，供 MCP 远程调用
- DecorView 树遍历，自动发现所有视图
- SSE 事件推送页面切换事件

**不包含**：
- JSON 解析和布局生成（由业务代码负责）
- View 的动态创建（SDK 只操作现有 View 树）

---

## 架构

### 模块划分

```
packages/android/sdk/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    ├── kotlin/com/clienttools/sdk/
    │   ├── ClientToolsSDK.kt              # 主入口、ContentProvider
    │   ├── http/
    │   │   ├── HttpServer.kt              # Nanohttpd Server
    │   │   ├── ApiHandler.kt              # REST 端点处理
    │   │   └── EventManager.kt            # SSE 事件推送
    │   ├── runtime/
    │   │   ├── ViewTreeTraversal.kt       # DecorView 遍历
    │   │   ├── ViewModifier.kt            # 运行时修改属性
    │   │   ├── ViewQueryService.kt        # 查询结构化信息
    │   │   └── OverlayManager.kt          # WebView 叠加层
    │   ├── listener/
    │   │   └── PageChangeListener.kt      # 页面切换监听
    │   └── model/
    │       ├── ViewInfo.kt                # 视图信息 DTO
    │       └── ModifyRequest.kt           # 修改请求 DTO
    └── res/
        ├── layout/overlay_container.xml   # WebView 容器布局
        └── values/attrs.xml               # 自定义属性

packages/android/demo/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    ├── kotlin/com/clienttools/demo/
    │   ├── MainActivity.kt                # Compose 首页列表
    │   ├── screens/
    │   │   ├── LoginScreen.kt             # 登录页示例 screen
    │   │   ├── FormScreen.kt              # 表单页示例 screen
    │   │   └── ...                        # 后续新增 screen
    │   └── TestScreenHost.kt              # 测试页容器（包含 XML layout）
    └── res/
        ├── layout/
        │   ├── login_screen.xml           # 登录页 View 层级（供 SDK 测试）
        │   ├── form_screen.xml            # 表单页 View 层级（供 SDK 测试）
        │   └── ...
        └── values/strings.xml
```

### 核心组件

| 组件 | 职责 | 关键方法 |
|------|------|---------|
| **ClientToolsSDK** | 初始化、对外 API | `init()`, `getViewInfo()`, `modify()`, `showOverlay()` |
| **HttpServer** | HTTP 服务 | `start()`, `stop()` |
| **ApiHandler** | REST 路由 | `/api/nodes/{id}`, `/api/modify`, `/api/overlay/*` |
| **EventManager** | SSE 事件 | `subscribe()`, `publishPageChange()` |
| **ViewTreeTraversal** | 递归遍历 DecorView | `findViewById()`, `traverseTree()` |
| **ViewModifier** | 属性修改 | `modifyMargin()`, `modifyPadding()`, `modifySize()` |
| **ViewQueryService** | 查询结构化信息 | `getViewInfo(id)` 返回 ViewInfo |
| **OverlayManager** | WebView 叠加 | `show()`, `hide()`, `setOpacity()` |
| **PageChangeListener** | 页面事件 | 监听 Activity 生命周期，发送 pageName（完整类名） |

### 数据结构

```kotlin
@Serializable
data class ViewInfo(
    val id: String,
    val type: String,                    // 对应 KMP NodeType
    val screenX: Float,
    val screenY: Float,
    val widthDp: Float,
    val heightDp: Float,
    val attrs: NodeAttrs?,               // 使用 KMP 的 sealed class
    val visibility: Int,                 // VISIBLE, INVISIBLE, GONE
    val isEnabled: Boolean
)

@Serializable
data class ModifyRequest(
    val id: String,
    val props: ViewProps                 // 直接使用 KMP 的 ViewProps
)
```

---

## 技术选择

| 方面 | 选择 | 理由 |
|------|------|------|
| **HTTP Server** | Nanohttpd | 轻量级（单 jar），端口 8080，支持 SSE |
| **View 遍历** | 主动遍历 DecorView 树 | 零侵入业务代码，O(n) DFS 可接受 |
| **初始化** | ContentProvider | 自动调用，无需业务代码显式初始化 |
| **WebView 叠加** | WindowManager 浮窗 | 支持拖拽、透明度调整，需要 SYSTEM_ALERT_WINDOW 权限 |
| **事件推送** | SSE（Server-Sent Events） | 实时、单向、简洁 |

---

## API 设计

### REST 端点

| 方法 | 端点 | 请求体 | 响应 | 说明 |
|------|------|--------|------|------|
| GET | `/api/nodes/{id}` | 无 | ViewInfo (JSON) | 查询指定 id 的视图信息 |
| POST | `/api/modify` | ModifyRequest (JSON) | `{"ok": true}` | 修改指定 id 视图属性 |
| POST | `/api/overlay/show` | `{"url": "..."}` | `{"ok": true}` | 显示 WebView 叠加 |
| POST | `/api/overlay/hide` | 无 | `{"ok": true}` | 隐藏叠加 |
| POST | `/api/overlay/opacity` | `{"opacity": 0.5}` | `{"ok": true}` | 调整透明度 (0.0-1.0) |
| GET | `/api/events` | 无（SSE） | 事件流 | 订阅页面切换事件 (text/event-stream) |

### Java API（业务代码调用）

```kotlin
// 获取视图信息
val info: ViewInfo = ClientToolsSDK.getViewInfo("text_1")

// 修改视图
ClientToolsSDK.modify(ModifyRequest("text_1", ViewProps(marginTopDiffDp = 10f)))

// 显示叠加
ClientToolsSDK.showOverlay(url = "file:///sdcard/design.html", opacity = 0.5f)

// 隐藏叠加
ClientToolsSDK.hideOverlay()

// 监听页面切换
ClientToolsSDK.addPageChangeListener { pageName, timestamp ->
    Log.d("PageChange", "$pageName at $timestamp")
}
```

---

## 数据流

### 运行时查询流程

```
MCP: GET /api/nodes/text_1
        ↓
ApiHandler.handleGetNode("text_1")
        ↓
ViewTreeTraversal.findViewById("text_1")
    ↓ (遍历 DecorView 树)
找到 View
        ↓
ViewQueryService.getViewInfo(view)
    ↓ (提取基础信息 + 样式)
构建 ViewInfo
        ↓
JSON 序列化（使用 KMP Json.encodeToString()）
        ↓
HTTP 200 + ViewInfo JSON
```

### 运行时修改流程

```
MCP: POST /api/modify
     Body: ModifyRequest(id="text_1", props=ViewProps(marginTopDiffDp=10f))
        ↓
ApiHandler.handleModify()
        ↓
ViewTreeTraversal.findViewById(id)
    ↓ (支持 RecyclerView item，批量修改)
找到所有匹配的 View
        ↓
ViewModifier.apply(view, props)
    ↓ (调用 View.setMarginDp()、View.setPadding()、View.getLayoutParams().width)
应用修改
        ↓
HTTP 200 + {"ok": true}
```

### 页面切换事件流

```
Activity.onResume()
        ↓
PageChangeListener.onActivityResumed(activity)
        ↓
EventManager.publishPageChange(pageName, timestamp)
        ↓
SSE 客户端接收 "data: {\"event\":\"page_changed\",\"pageName\":\"com.clienttools.demo.screens.LoginScreen\",\"timestamp\":\"...\"}"
        ↓
MCP 推送给 AI
```

**注**：pageName 为 Activity 的完整类名路径（包名 + 类名）

---

## Demo 应用

**目标**：展示 SDK 完整能力，支持与 MCP 端到端测试

**架构**：Jetpack Compose 首页列表 + 多个测试页面，每个测试页展示不同的 View 层级结构

### UI 结构

```
MainActivity (Compose)
├── 列表项：Login Screen
├── 列表项：Form Screen
├── 列表项：Image Gallery Screen
└── ... （后续添加更多测试页时直接增加列表项）

各测试页面示例 - LoginScreen 的 XML View 层级：
├── header_container (id: header)
│   └── title (id: text_1) - TEXT
├── avatar_image (id: img_1) - IMAGE
├── form_list (id: list_1) - LIST
│   ├── item_1 (id: input_1) - CONTAINER
│   └── item_2 (id: input_2) - CONTAINER
└── button_container (id: button_group)
    └── login_btn (id: btn_login)

```

### 功能清单

- ✅ MainActivity 使用 Compose 列表，每项对应一个测试页入口
- ✅ 各测试页使用 XML 布局，包含 View 层级便于 SDK 测试
- ✅ ContentProvider 自动启动 HTTP Server（端口 8080）
- ✅ 页面切换时发送 PageChangedEvent
- ✅ 支持实时查询每个 View 的信息（GET /api/nodes/{id}）
- ✅ 支持实时修改 margin、padding、宽高（POST /api/modify）
- ✅ 显示 WebView 叠加设计参考（POST /api/overlay/show）

---

## 集成点

### 与 KMP shared 的集成
- 导入 KMP shared `.aar` 包（自动编译到 SDK）
- 使用 KMP 的数据类：Node、NodeAttrs、DeviceInfo、ApiResponse、ViewProps、ModifyViewRequest、PageChangedEvent
- 使用 KMP 的 Json：`Json.encodeToString(viewInfo)`、`Json.decodeFromString(requestJson)`

### 与 MCP 的集成
- MCP Server 通过 ADB forward 访问 SDK HTTP Server：`adb forward tcp:8080 tcp:8080`
- 调用 `GET http://localhost:8080/api/nodes/{id}` 查询视图信息
- 调用 `POST http://localhost:8080/api/modify` 下发修改请求
- 订阅 `GET http://localhost:8080/api/events`（SSE）接收页面事件

---

## 测试计划

| 测试类型 | 场景 | 验证点 |
|---------|------|--------|
| **单元测试** | ViewTreeTraversal | 遍历逻辑、id 查找准确性、重复 id 处理 |
| | ViewModifier | margin、padding、宽高 修改正确应用 |
| | ViewQueryService | 样式信息（fontSize、color 等）提取准确性 |
| **集成测试** | HttpServer | 端点响应码、JSON 序列化、错误处理 |
| | SSE 事件 | 事件格式、订阅机制、多客户端并发 |
| | OverlayManager | WebView 加载、拖拽交互、权限请求 |
| **端到端测试** | Demo + MCP | 查询、修改、叠加、事件完整流程 |
| | RecyclerView | 滚动时重新应用待修改属性 |

---

## 风险与缓解

| 风险 | 影响 | 缓解方案 |
|------|------|---------|
| DecorView 树遍历性能 | 查询延迟 | 缓存热点 View，批量操作合并，超时控制 |
| View id 重复 | 修改错误的 View | 从上到下优先匹配，文档明确规则 |
| WebView 叠加权限 | 无法显示叠加 | 运行时请求 SYSTEM_ALERT_WINDOW，失败时提示用户 |
| RecyclerView item 更新 | 修改被覆盖 | View 回收时重新应用，维护待修改队列 |
| Activity 销毁后查询 | NPE | 检查 View 存活性，返回 404 |

---

## 约束

- **最低 SDK**：Android API 26（8.0）
- **KMP 模块**：依赖 packages/shared/.aar（预编译）
- **HTTP 端口**：8080（与 tech-plan.md 一致）
- **线程安全**：UI 修改在主线程，HTTP 请求在线程池
- **权限**：INTERNET、SYSTEM_ALERT_WINDOW（运行时请求）
