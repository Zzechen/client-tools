# Skill 瘦身 & 接入文档 — Spec

> 创建时间：2026-04-27

## 背景与目标

当前工具套件包含四个 skill，承担了大量流程编排职责。调整方向：**只提供能力，不限制流程**，把工作流决策权还给 AI，同时尽量减少对接入工程的约束。

具体目标：
1. 将 preprocess 脚本封装为 MCP 工具，AI 自主决定何时调用
2. 删除三个流程型 skill（preprocess、implement、orchestrate）
3. 保留 inspect skill（运行时校正协议不可替代）
4. 在 CLAUDE.md 补充 View id 硬性约束
5. 新增机器友好的接入文档，由 AI 读取后指导工程师配置

---

## 1. 新增 MCP 工具：`extract_view_layout`

### 定位

将设计稿 HTML 文件渲染并提取与运行时 View 对齐的布局结构数据。原 preprocess skill 的核心能力，以原子工具形式提供，不捆绑任何流程。

### 脚本迁移

现有脚本从 `skill/client-tools-preprocess/scripts/` 迁移到 `mcp/scripts/preprocess/`，包含：

```
mcp/scripts/preprocess/
├── preprocess.py
├── extractor.py
├── anchor.py
├── models.py
├── svg_extractor.py
├── requirements.txt
└── schema/
```

`.venv` 随脚本迁移，不提交到仓库（加入 `.gitignore`）。

### MCP 工具定义

**名称：** `extract_view_layout`

**描述：** 渲染设计稿 HTML，提取与 Android/iOS 运行时 View 对齐的布局节点数据，输出 design.json

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | string | ✅ | 设计稿 HTML 文件的绝对路径 |
| `viewport` | number | ✅ | 设计稿渲染宽度（px），如 375、390、360 |

**返回：**

```json
{
  "outputPath": "/path/to/design.json",
  "nodeCount": 23,
  "nodes": [ ... ]
}
```

- `outputPath`：design.json 写入路径（与 HTML 同目录同名 `.json`），临时文件，方便查阅和删除
- `nodes`：节点绝对坐标数组，AI 可直接使用，无需读文件
- 不含锚点概念，inspect 阶段的坐标对齐由 `adjust_overlay` 自动处理

**实现方式：**

在 `mcp/src/tools/` 新增 `design.ts`，调用方式：

```typescript
// 用 child_process.execFile 调用脚本，传入参数，捕获 stdout JSON
const scriptPath = path.join(__dirname, '../../scripts/preprocess/preprocess.py');
const venvPython = path.join(__dirname, '../../scripts/preprocess/.venv/bin/python');
```

工具注册到现有 MCP server（`mcp/src/index.ts`）。

---

## 2. Skill 处置

| Skill | 处置 | 原因 |
|-------|------|------|
| `client-tools-preprocess` | **删除** | 能力迁移到 `extract_view_layout` MCP 工具 |
| `client-tools-implement` | **删除** | 纯约束手册，改放 CLAUDE.md；流程编排交给 AI |
| `client-tools-orchestrate` | **删除** | 纯流程胶水，AI 自身可胜任 |
| `client-tools-inspect` | **保留** | 运行时校正协议（坐标公式、匹配算法）不可替代 |

删除操作：移除 `skill/` 下对应目录，同步更新 CLAUDE.md 中对 skill 目录的描述。

---

## 3. CLAUDE.md 补充

在「技术约定」章节新增：

```
- **View 标识**：
  - Android 布局：每个 View（包括中间容器层）必须设置 `android:id`，
    这是 `get_node`/`get_all_nodes` 工具的硬性前提，缺失则运行时无法定位
  - iOS 布局：每个 View 必须设置 `accessibilityIdentifier`，
    命名规则与 Android id 一致（页面前缀 + 语义名，如 `login_text_title`）
```

同时更新目录结构描述，移除已删除的 skill 目录说明，更新 `skill/` 条目为「仅含 `client-tools-inspect`」。

---

## 4. 接入文档：`docs/integration.md`

面向 Claude Code 读取，由 AI 指导工程师完成接入配置。风格：简洁、结构化、约束明确。

### 文档结构

**章节一：工程依赖**

Android：
- Gradle 添加 SDK `.aar` 依赖（Maven 坐标：`TODO: 待发布后填入`）
- proto 依赖（`protobuf-kotlin`）及 Gradle plugin 配置
- 最低 API 26

iOS：
- Podfile 添加 SDK（CocoaPod）
- SwiftProtobuf 依赖
- 最低 iOS 14

**章节二：MCP 安装**

- 前置：Node.js 18+、adb
- 安装步骤：`npm install`，构建 `npm run build`
- adb forward 配置：`adb forward tcp:8080 tcp:8080`
- Claude Desktop / Claude Code MCP 配置示例（JSON）
- `extract_view_layout` 工具额外依赖：Python 3.11+，初始化 `.venv`

**章节三：Skill 安装**

- 当前只有 `client-tools-inspect` 需要安装
- 安装路径：`.claude/skills/client-tools-inspect/`（复制 `skill/client-tools-inspect/` 目录）
- 验证：在 Claude Code 中运行 `/client-tools-inspect` 确认加载

**章节四：View 标识约束**

- 重申 Android `android:id` 和 iOS `accessibilityIdentifier` 的必要性
- 提供命名示例

---

## 约束与边界

- `extract_view_layout` 不负责生成 Android/iOS 代码，只输出结构数据
- MCP 工具不捆绑流程，AI 自行决定何时调用、如何使用返回数据
- inspect skill 内容不在本次调整范围内
- preprocess 脚本逻辑不变，只做目录迁移
