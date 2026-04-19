# client-tools Skills 设计文档

> 创建时间：2026-04-19

---

## 概述

为 client-tools 项目创建 3 个 AI 工作流 Skill，驱动「设计稿 → Android 实现 → 运行时校正」全流程。

- **触发方式：** 用户手动调用（`/client-tools:preprocess`）或 AI 识别触发词自动加载
- **安装方式：** 软链到 `~/.claude/skills/`，修改即时生效

---

## Skill 1：`client-tools:preprocess`

### 职责

引导用户完成设计稿预处理，输出 design.json 作为编码基准。

### 触发词

"预处理设计稿"、"处理 HTML"、"生成 design.json"、`/client-tools:preprocess`

### 流程

1. **收集参数** — 询问用户：
   - HTML 文件路径（`--input`）
   - 设计稿视口宽度 px（`--viewport`），例：375、390
2. **列出节点** — 以 `--list-only` 运行脚本，展示所有节点 id 列表
3. **用户选锚点** — 用户从列表中指定锚点 id（`--anchor-id`）和边缘（`--anchor-edge`，top/bottom，默认 top）
4. **生成文档** — 完整运行脚本，输出 design.json，告知用户输出路径

### 工具调用

使用 Bash 调用 `skill/preprocess/.venv/bin/python skill/preprocess/preprocess.py`，参数按上述步骤拼接。

### 成功标准

design.json 文件生成，包含 viewport、anchor、nodes 字段。

---

## Skill 2：`client-tools:implement`

### 职责

流程编排：读取 design.json → AI 生成 Android 代码 → 确认页面已运行。

### 触发词

"生成 Android 代码"、"实现布局"、"开始编码"、`/client-tools:implement`

### 流程

1. **读取 design.json** — 理解节点结构（id、type、rel 坐标、attrs）
2. **生成代码** — AI 自行决定 XML 布局实现细节，关键约束：
   - 所有 View（包含中间容器）必须设置 `android:id`
   - id 命名加业务/页面前缀，与 design.json 中节点 id 对应
3. **提示用户** — 让用户运行 App 并导航到目标页面
4. **确认页面** — 调用 `get_last_event` 检查页面切换事件，确认当前页面为目标页面后结束

### MCP 工具

- `get_last_event` — 获取最新页面切换事件

### 成功标准

Android 布局代码已生成，`get_last_event` 返回目标页面的 `page_changed` 事件。

---

## Skill 3：`client-tools:inspect`

### 职责

运行时视觉校正循环：WebView 叠加 → 逐节点比对 → AI 推理调整 → 验收。

### 触发词

"开始校正"、"视觉核对"、"inspect"、`/client-tools:inspect`

### 流程

#### 阶段一：WebView 叠加

1. 调用 `push_html` 推送设计稿 HTML（自动显示）
2. 提示用户手动调整偏移/透明度，对齐锚点节点

#### 阶段二：逐节点局部校对

按从左到右、从上到下顺序处理每个节点：

```
取下一个节点 id
  ↓
dom_by_id(id) + get_node(id) → 获取双侧数据
  ↓
AI 推理：计算差异，决定调整属性和量
  ↓
差异在阈值内？→ 是 → 该节点通过，继续下一个
  ↓ 否
modify_view(id, props) → 调整
  ↓
重新获取，重新对比（最多 10 轮）
连续 2 轮无改善 → 提前终止，标记该节点未收敛
```

**调整策略（AI 自行推理）：**
- 根据差异方向和大小，结合节点 type，决定调整 margin/padding/width/height
- 每次调整后必须重新获取数据，不得基于旧数据叠加计算

**收敛阈值：**
- 位置误差 ≤ 2dp
- 尺寸误差 ≤ 2dp

#### 阶段三：全屏验收

1. 调用 `dom_all` 获取全量 DOM 数据
2. 对所有节点批量调用 `get_node` 获取 View 数据
3. 整体差异检查，全部通过则验收成功
4. 未通过节点重新进入局部校对循环

#### 阶段四：输出报告

- 通过：列出所有节点最终误差
- 未收敛：列出未通过节点 id 和最后一次差异数据，建议人工介入

### MCP 工具

| 工具 | 用途 |
|------|------|
| `push_html` | 推送并显示设计稿 |
| `adjust_overlay` | 调整 WebView 偏移/透明度（用户操作，AI 可辅助） |
| `hide_overlay` | 校正完成后隐藏 |
| `dom_by_id` | 按 id 获取 DOM 节点坐标 |
| `dom_all` | 全量 DOM 数据（验收阶段） |
| `get_node` | 获取 Android View 节点坐标 |
| `modify_view` | 修改 View 布局属性 |

### 成功标准

全屏验收阶段所有节点位置/尺寸误差 ≤ 2dp。

---

## 目录结构

```
skill/
  client-tools-preprocess/
    SKILL.md
  client-tools-implement/
    SKILL.md
  client-tools-inspect/
    SKILL.md
```

安装（软链）：

```bash
ln -sf $(pwd)/skill/client-tools-preprocess ~/.claude/skills/client-tools-preprocess
ln -sf $(pwd)/skill/client-tools-implement ~/.claude/skills/client-tools-implement
ln -sf $(pwd)/skill/client-tools-inspect ~/.claude/skills/client-tools-inspect
```
