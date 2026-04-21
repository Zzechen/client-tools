# Spec: Orchestrator + SVG 自动转换

## 背景

现有工作流由三个独立 skill 组成（preprocess → implement → inspect），用户需要手动触发每个阶段。此外，HTML 设计稿中的 SVG 图标目前未被提取，implement 阶段无法自动生成对应的 Vector Drawable。

本 spec 描述两项改动：
1. 新增 `client-tools-orchestrate` skill，管理设计稿生命周期，支持跨会话恢复
2. 在 preprocess 阶段自动提取 SVG 并转换为 Android Vector Drawable

## 工作目录结构

每个设计稿对应一个独立工作目录，位于用户当前项目根目录下：

```
<project-root>/
└── design/
    └── <timestamp>-<bizname>/       # 如 20260421-login-phone
        ├── state.json               # 状态机文件
        ├── design.html              # 原始设计稿（复制进来）
        ├── design.json              # preprocess 产出
        └── drawables/               # SVG → Vector Drawable
            ├── ic_wechat.xml
            ├── ic_close.xml
            └── ...
```

### state.json 结构

```json
{
  "bizname": "login-phone",
  "phase": "implement",
  "viewport": 375,
  "anchor": { "id": "login_text_title", "edge": "top" },
  "target": {
    "project": "../my-android-app",
    "module": "app",
    "layout": "activity_login"
  },
  "history": [
    { "phase": "preprocess", "completedAt": "2026-04-21T10:00:00Z" }
  ]
}
```

`phase` 取值：`preprocess` | `implement` | `inspect` | `done`

`target` 字段在 implement 阶段开始时由用户提供后写入，preprocess 完成后该字段为空。

## Orchestrator Skill（client-tools-orchestrate）

### 触发条件
- 用户说"开始"、"继续"、"恢复"
- 用户调用 `/client-tools:orchestrate`

### 工作流

```
启动
  ↓
扫描 ./design/ 下所有 state.json
  ↓
有未完成项（phase != done）
  → 列出未完成项，询问用户恢复哪个，或新建
无未完成项
  → 直接进入新建流程
  ↓
根据 state.json.phase 分发：
  preprocess → 调用 client-tools:preprocess（含 SVG 提取）
  implement  → 调用 client-tools:implement
  inspect    → 调用 client-tools:inspect
  done       → 提示已完成，询问是否新建
  ↓
每个子阶段完成后：
  更新 state.json.phase 到下一阶段
  自动推进，无需用户手动触发
```

### 新建流程
1. 询问 bizname（业务名称，用于目录命名）
2. 询问 HTML 设计稿路径
3. 创建工作目录 `design/<yyyymmddHHMM>-<bizname>/`
4. 复制 HTML 到工作目录，命名为 `design.html`
5. 写入初始 `state.json`（phase = preprocess）
6. 自动进入 preprocess 阶段

## preprocess 阶段变更

在现有 preprocess 脚本基础上，新增 SVG 提取与转换步骤。

### SVG 识别规则

Playwright 渲染页面后，对每个元素额外检测：

**规则 1：图标替换**
- 条件：元素只有一个 SVG 子节点，且无文本内容、无其他可见子元素
- 处理：该元素节点 type 强制设为 `IMAGE`，提取 SVG 转换为 Vector Drawable

**规则 2：装饰背景**
- 条件：SVG 元素面积超过视口面积的 50%
- 处理：提取转换，但节点 type 标记为 `DRAWABLE`（不生成 ImageView，供 AI 按需引用）

### SVG → Vector Drawable 转换

使用纯 Python 库（无需 Android SDK），在 preprocess 虚拟环境中安装，不引入外部依赖。转换产出存入工作目录 `drawables/` 文件夹。

### 命名规则

按以下优先级推断语义名称：
1. 父元素的 `aria-label` 属性（如 `aria-label="微信登录"` → `ic_wechat`）
2. 父元素 id 的语义部分（如 `login_btn_wechat` → `ic_wechat`）
3. SVG path 内容特征（AI 推断）
4. Fallback：`ic_<node_id>`

### design.json 变更

IMAGE 节点的 `attrs` 新增 `drawable` 字段（无扩展名）：

```json
{
  "id": "container_5",
  "type": "IMAGE",
  "attrs": {
    "scaleType": "fitCenter",
    "drawable": "ic_wechat"
  }
}
```

DRAWABLE 类型节点同理，但 type 为 `DRAWABLE`。

## implement 阶段变更

### 新增：目标路径收集

implement 开始时，若 `state.json.target` 为空，询问用户：
- 目标 Android module 根路径（如 `../my-android-app/app`）
- 布局文件名（如 `activity_login`）

收集后写入 `state.json.target`。

### 新增：drawables 复制

生成布局 XML 之前，将 `design/<timestamp>-<bizname>/drawables/*.xml` 复制到 `<module>/src/main/res/drawable/`。

### 代码生成变化

IMAGE 节点有 `drawable` 字段时，生成：

```xml
<ImageView
    android:id="@+id/login_btn_wechat"
    android:layout_width="..."
    android:layout_height="..."
    android:src="@drawable/ic_wechat"
    android:scaleType="fitCenter" />
```

DRAWABLE 类型节点不自动生成 View，AI 在生成背景容器时按需引用。

## 不在范围内

- iOS 平台的 SVG 转换
- SVG 动画（Lottie 等）
- 装饰类 SVG 的自动布局集成
- design.html 的版本管理
