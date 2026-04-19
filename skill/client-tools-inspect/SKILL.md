---
name: client-tools-inspect
description: Use when user wants to visually inspect or correct an Android screen against its design, or says "开始校正"/"视觉核对"/"inspect"
---

# client-tools:inspect

运行时视觉校正工作流。将设计稿叠加到 Android App 上，逐节点比对差异，AI 自动调整 View 属性直到通过验收。

## 触发条件

- 用户说"开始校正"、"视觉核对"、"inspect"
- 用户调用 `/client-tools:inspect`

## 前置条件

- design.json 已生成，包含节点列表
- App 已运行到目标页面（由 `client-tools:implement` 确认）
- MCP client-tools 工具已连接（`get_last_event` 可用）

## 工作流程

### 阶段一：WebView 叠加

1. 读取设计稿 HTML 文件（路径从 design.json 同目录推断，或询问用户）
2. 调用 `push_html(tag, html)` 推送并显示设计稿叠加层
3. 提示用户手动调整偏移/透明度，对齐锚点节点后告知 AI 继续

### 阶段二：逐节点局部校对

从 design.json 的 `nodes` 列表中，**按从左到右、从上到下**（即 screenY 升序，同 Y 则 screenX 升序）依次处理每个节点。

**跳过规则：** type 为 `container` 的节点跳过样式属性校对，但仍校对位置和尺寸。

**每个节点的校对循环（最多 10 轮）：**

```
① 调用 dom_by_id(id) → 获取 DOM 节点屏幕坐标和尺寸
② 调用 get_node(id) → 获取 Android View 屏幕坐标和尺寸
③ 计算差异：
   - dx = dom.screenX - view.screenX
   - dy = dom.screenY - view.screenY
   - dw = dom.widthDp - view.widthDp
   - dh = dom.heightDp - view.heightDp
④ 差异在阈值内（|dx|≤2dp, |dy|≤2dp, |dw|≤2dp, |dh|≤2dp）？
   → 是：该节点通过，继续下一个
   → 否：AI 推理调整策略（见下）
⑤ 调用 modify_view(id, props) 应用调整
⑥ 重回 ① 重新获取数据
```

**AI 推理调整策略：**

根据差异方向、大小和节点 type，自行决定调整哪个属性（margin/padding/width/height）和调整量。原则：
- 每次调整后必须重新获取数据，不得基于旧数据叠加计算
- 若连续 2 轮差异无改善（变化 < 0.5dp），提前终止该节点并标记为「未收敛」

**modify_view props 字段说明：**
- `marginTopDiffDp` / `marginBottomDiffDp` / `marginLeftDiffDp` / `marginRightDiffDp`：margin 增量（dp，可负）
- `paddingTopDiffDp` / `paddingBottomDiffDp` / `paddingLeftDiffDp` / `paddingRightDiffDp`：padding 增量（dp，可负）
- `widthDp` / `heightDp`：尺寸绝对值（dp）

### 阶段三：全屏验收

所有节点局部校对完成后：

1. 调用 `dom_all()` 获取全量 DOM 数据
2. 对所有节点 id 批量调用 `get_node(id)` 获取 View 数据
3. 逐一检查每个节点的差异是否在阈值内
4. 全部通过 → 验收成功，进入阶段四
5. 有未通过节点 → 对这些节点重新进入阶段二局部校对循环

### 阶段四：输出报告

**验收成功时：**

```
✅ 校正完成，共 N 个节点通过验收
节点详情：
- text_title: dx=0.5dp, dy=1.0dp, dw=0dp, dh=0dp ✓
- img_avatar: dx=0dp, dy=0.5dp, dw=0dp, dh=0dp ✓
...
```

**存在未收敛节点时：**

```
⚠️ 校正完成，N 个节点通过，M 个节点未收敛，需人工介入：
- login_list_feed: 最终差异 dx=5dp, dy=0dp（超出阈值）
```

5. 调用 `hide_overlay()` 隐藏叠加层

## 验收阈值（默认）

| 维度 | 阈值 |
|------|------|
| 位置（x/y） | ≤ 2dp |
| 尺寸（w/h） | ≤ 2dp |

## MCP 工具速查

| 工具 | 参数 | 用途 |
|------|------|------|
| `push_html` | tag, html | 推送并显示设计稿叠加层 |
| `adjust_overlay` | dx?, dy?, alpha? | 调整偏移/透明度 |
| `hide_overlay` | - | 校正完成后隐藏 |
| `dom_by_id` | id | 获取单个 DOM 节点坐标 |
| `dom_all` | - | 全量 DOM 数据（验收阶段） |
| `get_node` | id | 获取 Android View 坐标 |
| `modify_view` | id, props | 修改 View 布局属性 |
