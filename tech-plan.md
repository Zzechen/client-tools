# AI Coding 客户端页面开发增强套件 — 技术规划

> 创建时间：2026-04-17

---

## 目标

让 AI 能高质量完成「设计稿 → Android 运行时」的实现，并提供设计稿与运行时的视觉核对与循环修正能力。

---

## 系统概览

整个工具套件分为两个阶段：

**阶段一：预处理 + 编码**
设计稿（HTML/CSS）→ Playwright 渲染 → 结构化 JSON → AI 生成 Android 代码

**阶段二：运行时核对 + 循环修正**
App 运行 → WebView 叠加设计稿 → 用户对齐锚点 → AI 获取差异 → SDK 修改 View → 循环直到验收通过

---

## 模块设计

### 模块 1：设计稿预处理（Python）

**输入：** HTML/CSS 设计稿文件

**输入参数：**
- `--input`：HTML/CSS 设计稿文件路径
- `--viewport`：设计稿宽度（px），如 `375`、`390`，高度自适应内容
- `--device-dp`：目标设备 dp 宽度，如 `360`、`393`
- `--anchor`：锚点节点 id（由用户指定，应选视觉位置稳定的元素，如页面顶部标题、固定 header）

**处理步骤：**
1. 用 Playwright（无头 Chromium）按指定 viewport 宽度加载并渲染 HTML
2. 计算 px→dp 换算比例：`scale = device-dp / viewport`
3. 遍历所有 DOM 节点，自动生成唯一语义化 id（如 `text_title`、`img_avatar`、`list_feed`），注入到节点上
4. 调用 `getBoundingClientRect()` 获取每个节点渲染后的真实位置和尺寸，乘以 scale 转换为 dp
5. 计算所有节点相对锚点的偏移量（dx, dy）和尺寸
6. 提取节点的样式属性（字体、颜色、padding 等）
7. 输出结构化 JSON 文档

**输出格式（JSON）：**
```json
{
  "viewport": 375,
  "anchor": {
    "id": "text_title",
    "edge": "top"
  },
  "nodes": [
    {
      "id": "text_title",
      "type": "text",
      "abs": { "x": 100, "y": 200, "width": 240, "height": 36 },
      "rel": { "dx": 0, "dy": 0, "width": 240, "height": 36 },
      "attrs": { "fontSize": 16, "color": "#333", "fontWeight": "bold" }
    },
    {
      "id": "img_avatar",
      "type": "image",
      "abs": { "x": 80, "y": 160, "width": 40, "height": 40 },
      "rel": { "dx": -20, "dy": -40, "width": 40, "height": 40 },
      "attrs": {}
    }
  ]
}
```

**节点类型映射：**
| DOM 标签 | type 值 | 对应 Android View |
|---------|---------|-----------------|
| `<p>`, `<span>`, `<h*>` | `text` | TextView |
| `<img>` | `image` | ImageView |
| `<ul>`, `<ol>` | `list` | RecyclerView |
| 其他 | `container` | ViewGroup（不参与核对）|

---

### 模块 2：AI 编码实现

AI 以模块 1 输出的 JSON 为基准，生成对应的 Android 布局代码（XML + Kotlin/Java）。

**唯一约束：所有 View（包括中间容器层）都必须设置 `android:id`**，以便核对和运行时属性调整（如容器整体位移）。

命名遵循 Android 开发习惯，加业务/页面前缀，例如：
- `@+id/login_text_title`
- `@+id/profile_img_avatar`

---

### 模块 3：客户端 SDK（Android）

**初始化：零侵入接入**
- 使用 `ContentProvider` 自动初始化，接入方无需在 `Application` 或 `Activity` 中添加任何代码
- 仅需在 `build.gradle` 中添加依赖，SDK 自动注册并启动

**控制面板常驻配置：**
- 在 `AndroidManifest.xml` 中通过 `meta-data` 控制面板是否常驻：
```xml
<meta-data
    android:name="client_tools_panel_persistent"
    android:value="false" />  <!-- 默认 false，由 AI 按需触发 -->
```

SDK 通过监听 `ActivityLifecycle` → 获取 `DecorView` → 遍历 View 树，实现以下能力：

#### 3.1 WebView 叠加（可选，AI 主动触发）

**HTML 推送与存储：**
- AI 通过 `POST /webview/push-html` 将 HTML 内容推送给 SDK，同时指定 `tag`（如页面名 `login`、`home`）和时间戳
- 时间戳格式：`MMdd-HHmm`（如 `0417-1423`），短且人可读
- SDK 保存到本地：`<storage>/<tag>/<tag>_<timestamp>.html`
- WebView 加载本地 HTML 文件，宽度 100% 填满屏幕，高度自适应

**显示/隐藏：**
- SDK 提供显示/隐藏接口，由 AI 在需要时调用触发，不常驻
- 显示时在当前 Activity 上层叠加透明 WebView
- 对齐完成后，AI 调用隐藏接口关闭 WebView

**悬浮控制面板：**
- 入口为一个悬浮在当前 Activity 上的小图标，点击展开为看板
- 入口图标和展开看板均可拖动，位置自由调整
- 看板内部按功能区划分，每个区域支持独立折叠：
  - **WebView 区**：当前加载 HTML 的 tag + 时间戳、显示/隐藏 WebView
  - **调整区**：上下左右偏移步进按钮、透明度滑块（默认 0.5）
  - **文件区**：按 tag 分组展示已保存的 HTML 文件（显示时间戳），点击切换加载
  - **信息区**：当前 Activity 名称、SDK 状态
- 常驻模式（`meta-data` 配置为 `true`）下每个 Activity 自动显示入口；非常驻模式由 AI 通过接口触发显示

#### 3.2 View 树结构化数据获取

SDK 提供两种粒度的查询接口：

**全量获取：** 遍历 DecorView 下所有有 id 的节点，返回完整列表

**按 id 查询：** 传入单个或多个 View id，返回对应节点的结构化数据

AI 在校对时按从左到右、从上到下的顺序逐个（或逐批）查询，实现局部渐进校对，而非一次性处理全页。

输出结构化数据：

```json
{
  "views": [
    {
      "id": "login_text_title",
      "type": "TextView",
      "abs": { "x": 98, "y": 198, "width": 244, "height": 38 },
      "attrs": { "textSize": 16, "textColor": "#333333" }
    }
  ]
}
```

#### 3.3 DOM 树结构化数据获取

SDK 提供两种粒度的查询接口（与 3.2 对齐）：

**全量获取：** 通过 WebView 注入 JS，获取所有有 id 的 DOM 节点信息

**按 id 查询：** 传入单个或多个节点 id，返回对应节点数据

**坐标计算：** DOM 节点坐标 = `getBoundingClientRect()` 结果 + WebView 当前在屏幕上的偏移量（含用户手动调整的上下左右偏移），确保与 View 树的屏幕绝对坐标系一致，可直接对比。

输出格式与 3.2 相同，`type` 使用设计稿中的 type 值。

#### 3.4 运行时 View 属性修改

- 通过 View id 定位目标 View，修改后触发重新布局（`requestLayout()`）
- 支持修改属性：`marginTop`、`marginBottom`、`marginLeft`、`marginRight`、`paddingTop`、`paddingBottom`、`paddingLeft`、`paddingRight`、`width`、`height`
- **批量修改：** 当目标 View 类型为 RecyclerView 的 item 时（即 View 树中存在多个相同 id 的节点），SDK 自动查找并修改所有同名 id 的 View，保持列表各项一致
- 修改协议（后续定义）

---

### 模块 4：差异计算与节点匹配

**锚点选择：** 由用户在预处理阶段手动指定，应选择页面中视觉位置稳定、不随内容变化移位的元素（如页面顶部标题、固定 header 等）。

**节点匹配规则：**

1. AI 读取 DOM 树数据和 View 树数据
2. 根据上下文（节点 type、相对位置、业务语义）自动判断对应关系，无硬编码算法
3. 没有 id 的 View 不参与匹配；`container` 类型节点参与位置/尺寸核对，不核对样式属性

**差异计算维度（按节点类型）：**

| type | 核对属性 |
|------|---------|
| `text` | x、y、width、height、fontSize、color |
| `image` | x、y、width、height |
| `list` | x、y、width、height、item 间距（必选，验收阈值默认 1dp，可配置）|
| 通用 | x、y、width、height |

**验收标准：**
- 位置误差 ≤ 2dp（可配置）
- 尺寸误差 ≤ 2dp（可配置）
- 样式属性按类型自定义阈值

---

### 模块 5：AI 校正循环

**流程：**

```
按从左到右、从上到下顺序取下一个节点 id
        ↓
  按 id 获取 DOM 节点数据 + View 节点数据
        ↓
    AI 节点匹配 + 计算差异
        ↓
  差异在阈值内？ → 是 → 该节点通过，继续下一个
        ↓ 否
  AI 决定调整属性和调整量
        ↓
  SDK 修改 View（findViewById + 公开 API）
        ↓
  超过最大轮次？ → 是 → 报告未收敛，人工介入
        ↓ 否
  重新获取该节点数据，继续校对
        ↓
  ——— 所有节点局部校对完成 ———
        ↓
  全屏验收：全量获取 DOM + View 数据，整体差异检查
        ↓
  全部通过？ → 是 → 验收通过，结束
        ↓ 否
  对未通过节点重新进入局部校对循环
```

**收敛控制：**
- 最大循环轮次：默认 10 轮（可配置）
- 每轮调整后必须重新获取 View 树数据，不允许基于旧数据叠加计算
- 若连续 2 轮差异无改善，提前终止并报告

---

## HTTP 协议设计

### 通用 Response 结构

```json
{
  "code": 0,
  "message": "success",
  "sdkVersion": 3,
  "device": {
    "screenWidthDp": 360,
    "screenHeightDp": 800,
    "density": 3.0,
    "orientation": "portrait"
  },
  "data": { ... }
}
```

- `code`：0 为成功，非 0 为错误
- `message`：错误时返回描述
- `sdkVersion`：当前 SDK 版本号（纯数字递增）
- `device`：设备基本信息，每次响应携带
  - `screenWidthDp` / `screenHeightDp`：屏幕尺寸（dp）
  - `density`：屏幕密度（dpi / 160）
  - `orientation`：`portrait` / `landscape`
- `data`：业务数据

### 版本兼容性

MCP 维护两个版本号配置：

```json
{
  "androidVersion": 3,
  "iosVersion": 2
}
```

每次请求后对比 `sdkVersion` 与对应平台版本号，不一致时提示升级版本较低的一方。

### 节点数据结构（KMP 唯一源）

```kotlin
// 共享 data class
data class Node(
    val id: String,
    val type: NodeType,       // TEXT, IMAGE, LIST, CONTAINER
    val screenX: Float,       // 相对屏幕左上角，dp
    val screenY: Float,       // 相对屏幕左上角，dp
    val widthDp: Float,       // dp
    val heightDp: Float,      // dp
    val attrs: NodeAttrs?
)

sealed class NodeAttrs
data class TextAttrs(
    val fontSize: Float,
    val color: String,
    val fontWeight: String
) : NodeAttrs()

data class ImageAttrs(
    val scaleType: String
) : NodeAttrs()

data class ListAttrs(
    val itemSpacing: Float,   // dp，必选
    val orientation: String   // VERTICAL / HORIZONTAL
) : NodeAttrs()

data class ContainerAttrs(
    val paddingTop: Float,
    val paddingBottom: Float,
    val paddingLeft: Float,
    val paddingRight: Float
) : NodeAttrs()
```

DOM 节点和 View 节点使用同一结构，`screenX/screenY` 均为相对屏幕左上角的绝对坐标（dp），可直接对比。

### View 修改请求体

```json
{
  "id": "login_text_title",
  "props": {
    "marginTop": 8,
    "paddingLeft": 16,
    "widthDp": 240
  }
}
```

### 页面切换推送事件（SSE）

```json
{
  "event": "page_changed",
  "activityName": "com.example.LoginActivity",
  "timestamp": "0417-1423"
}
```

---

## AI 与 SDK 的集成方式

### SDK 侧：本地 HTTP Server

SDK 在设备上启动一个本地 HTTP server，通过 ADB forward 暴露给 PC（默认端口 8080）：

```bash
adb forward tcp:8080 tcp:8080
```

提供 REST 接口：

- `POST /webview/push-html` — 推送 HTML 内容，参数：`tag`、`timestamp`（`MMdd-HHmm`）、`html`
- `POST /webview/show` — 显示 WebView，参数：`tag`、`timestamp`（指定加载哪个已保存文件）
- `POST /webview/hide` — 隐藏 WebView
- `POST /webview/adjust` — 调整 WebView 偏移和透明度
- `GET /dom/all` — 获取全量 DOM 树数据
- `GET /dom/:id` — 按 id 获取 DOM 节点数据
- `GET /view/all` — 获取全量 View 树数据
- `GET /view/:id` — 按 id 获取 View 节点数据
- `POST /view/modify` — 修改 View 属性
- `GET /page/current` — 获取当前页面信息（Activity 类名）

**页面切换通知：** SDK 监听 Activity 生命周期，页面切换时主动推送事件（SSE 或 WebSocket），AI 订阅后判断当前页面是否为目标页面，确认后再触发 WebView 叠加和校对流程。

### AI 侧：MCP + Skill 结合

**MCP Server（负责接口调用）**
- 封装上述 HTTP 接口，暴露为 MCP 工具函数
- 每个工具有明确的入参/出参定义，AI 直接调用工具名，无需手动拼 HTTP 请求
- 返回结构化数据（基于 KMP 定义的 data class），AI 直接消费

**Skill（负责工作流策略）**
- 描述校正循环的执行策略：按什么顺序校对、如何决定调整量、何时触发全屏验收、收敛失败如何处理
- AI 读取 skill 理解整体流程，通过 MCP 工具完成每一步具体操作

**Skill 清单（共 3 个，含项目前缀避免全局冲突）：**

| skill 名 | 职责 |
|---------|------|
| `client-tools:preprocess` | 预处理流程：询问锚点、调用脚本、验证输出 |
| `client-tools:implement` | 编码实现：读取 design.json、生成 Android 代码、id 约束提醒 |
| `client-tools:inspect` | 校正循环：局部校对顺序、调整策略、全屏验收、收敛判断 |

**安装方式（软链到全局 skill 目录）：**

```bash
# 在项目根目录执行
ln -sf $(pwd)/skill/client-tools-preprocess ~/.claude/skills/client-tools-preprocess
ln -sf $(pwd)/skill/client-tools-implement ~/.claude/skills/client-tools-implement
ln -sf $(pwd)/skill/client-tools-inspect ~/.claude/skills/client-tools-inspect
```

开发时修改 `skill/` 下的文件即时生效，无需重复安装。后续提供 `install.sh` 脚本封装上述命令。

---

## 工具交互流程（端到端）

```
1. [PC] 运行预处理脚本：python preprocess.py design.html
   → 输出 design.json

2. [AI] 读取 design.json，生成 Android 页面代码
   → 所有 View 带 id

3. [设备] 运行 App，导航到目标页面

4. [SDK→AI] SDK 推送页面切换事件，AI 确认当前页面为目标页面后触发后续流程

5. [AI] 调用 MCP 接口加载 WebView 叠加

6. [用户] 手动调整 WebView 偏移和透明度，对齐锚点

7. [AI] 通过 MCP 获取 DOM 树 + View 树数据，执行差异计算，启动校正循环

8. 循环结束后输出报告：通过 or 未收敛（附差异详情）
```

---

## 技术栈

| 层 | 技术 |
|----|------|
| 设计稿预处理 | Python 3.10+、Playwright、BeautifulSoup（辅助解析）|
| 结构化数据格式 | JSON |
| 数据结构唯一源 | KMP（Kotlin Multiplatform）共享模块，定义 data class + 序列化逻辑 |
| KMP 共享模块 | `packages/shared/`，Kotlin Multiplatform，commonMain，定义 data class + 序列化 |
| Android SDK | `packages/android/sdk/`，Kotlin，依赖 shared，打包为 `.aar` |
| iOS SDK | `packages/ios/sdk/`，Swift/OC 实现视图层遍历，依赖 shared KMP framework，打包为 `.xcframework`，兼容 UIKit/SwiftUI/OC |
| 运行时修改（Android）| ViewGroup LayoutParams、View.setPadding()、findViewById |
| 运行时修改（iOS）| UIView frame/bounds/layoutMargins 等公开 API |

---

## 约束与边界

- Android 布局统一使用 XML，不使用 Jetpack Compose
- 当前支持 Android 原生 View 体系和 iOS（UIKit/SwiftUI/OC）
- 预处理阶段需本地安装 Playwright + Chromium（约 100MB，一次性）
- 运行时修改为内存状态，不持久化到源码；持久化由 AI 另行修改源文件完成
- SDK 仅用于开发调试阶段，不应打包进生产包
