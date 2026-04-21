---
name: client-tools-inspect
description: Use when user wants to visually inspect or correct an Android screen against its design, or says "开始校正"/"视觉核对"/"inspect"
---

# client-tools:inspect

运行时视觉校正工作流。将设计稿叠加到 Android App 上，自动计算锚点偏移对齐，通过坐标自动匹配 DOM 节点与 Android View，批量调整直到通过验收。

## 触发条件

- 用户说"开始校正"、"视觉核对"、"inspect"
- 用户调用 `/client-tools:inspect`

## 前置条件

- 设计稿 HTML 文件已准备好（无需添加 id）
- App 已运行到目标页面（用户确认后开始）
- MCP client-tools 工具已连接

## 工作流程

### 阶段一：推送并自动对齐

1. 询问用户 HTML 文件路径（若未提供）
2. 调用 `push_html(tag, file=<绝对路径>)` 推送设计稿叠加层（opacity 默认 0.5）
3. 调用 `dom_all()` 获取全量 DOM 节点
4. 调用 `get_all_nodes()` 获取全量 Android View 节点
5. **自动计算锚点偏移**（无需人工对齐）：
   - 策略一（文字匹配）：在 DOM 中找有文字内容的节点，在 View 中找文字相同的节点，取最强信号的一对作为锚点
   - 策略二（位置特征）：若无文字匹配，选 DOM 和 View 中 y 坐标最小的非零尺寸节点各一个
   - 计算 offsetX = view.screenX - dom.x / density，offsetY = view.screenY - dom.y / density
   - 调用 `adjust_overlay(offsetX=<值>, offsetY=<值>)` 设置偏移（支持小数 dp）
6. 再次调用 `dom_all()` 获取应用偏移后的 DOM 坐标（DOM 坐标会随 offset 更新）
7. 告知用户自动对齐完成，使用的锚点元素，进入匹配阶段

### 阶段二：全量匹配

1. 使用阶段一最终的 dom_all() 和 get_all_nodes() 数据
2. DOM 坐标已含 WebView offset，直接与 View 坐标比对（单位：px vs dp，需 /density 换算）
   - density 从任意 View 的 screenY(dp) 与实际像素推算，或从 dom.y(px) 与 view.screenY(dp) 差值估算
   - 实际上 dom_all() 返回的坐标已经是 dp（SDK 内部按 WebView 实际位置换算），可直接与 View 坐标比对
3. 对每个 Android View，按以下优先级在 DOM 中寻找最佳匹配：
   - **文字内容**（View text == DOM textContent，强信号，优先）
   - **坐标距离**（|dx| + |dy| 最小）
   - **尺寸接近度**（|dw| + |dh| 最小）
   - **类型兼容性**（TextView ↔ p/span/h1-h6/button，ImageView ↔ img，ViewGroup ↔ div）
4. 双向验证：View A 匹配 DOM B，且 DOM B 反向也最近邻 View A，否则标记「可疑」
5. 输出匹配表，可疑匹配请用户确认后再进入校对

匹配表格式：
```
View id                DOM 元素      文字        dx    dy    dw    dh
login_text_title       h1           欢迎回来      0     0     0     0
login_btn_submit       button       获取验证码     0     1    -1     0
⚠️ login_logo_name     div(可疑)     PULSE        3     2     5     4
```

### 阶段三：批量校对循环

使用匹配表，按从上到下（screenY 升序）顺序校对每个节点。

**跳过规则：** type 为 CONTAINER 且无文字内容的节点，仅校对位置和尺寸，跳过样式属性。

**验收阈值：** 所有维度差异 < 1dp 即通过。

**每轮校对流程（最多 5 轮）：**

```
① 找出本轮所有差异 ≥ 1dp 的节点
② 优先处理父容器（位置靠上），每轮最多调整 5 个节点
③ 对每个节点推断调整策略（margin/padding/width/height）
④ 批量调用 modify_view 应用所有调整
⑤ 调用 get_all_nodes() 获取全量快照
⑥ 全局比对更新所有节点差异状态
⑦ 差异全部 < 1dp → 进入验收；否则进入下一轮
```

**调整策略原则：**
- 每次调整后必须用最新 get_all_nodes() 数据，不得基于旧数据叠加计算
- 若连续 2 轮某节点差异无改善（变化 < 0.5dp），标记为「未收敛」，跳过该节点

**注意：校对阶段不修改 XML 代码，所有调整仅通过 modify_view 运行时生效。差异记录到 checklist，用户确认后再集中写回 XML。**

### 阶段四：全屏验收

1. 调用 `get_all_nodes()` 获取最终全量快照
2. 对照匹配表，逐一检查每个节点差异是否 < 1dp
3. 全部通过 → 验收成功，进入阶段五
4. 有未通过节点 → 重新进入阶段三，最多重复 3 次

### 阶段五：输出 checklist + 隐藏叠加层

**验收成功时，输出 checklist：**

```
✅ 校正完成，共 N 个节点通过验收

| View id | 匹配 DOM | dx | dy | dw | dh | 建议 XML 改动 |
|---------|---------|-----|-----|-----|-----|--------------|
| login_text_title | h1 | 0 | 0 | 0 | 0 | 无需改动 |
| login_btn_submit | button | 0 | 0.5 | 0 | 0 | 无需改动 |
| login_tabs | div | 0 | 0 | 0 | 0 | marginTop: 12dp→20dp |
```

**存在未收敛节点时：**

```
⚠️ 校正完成，N 个节点通过，M 个节点未收敛，需人工介入：
- login_logo_section: 最终差异 dy=3dp（超出阈值），建议检查 marginTop 约束链
```

调用 `hide_overlay()` 隐藏叠加层。

等用户确认 checklist 后，再集中写回 XML。

## 验收阈值

| 维度 | 阈值 |
|------|------|
| 位置（x/y） | < 1dp |
| 尺寸（w/h） | < 1dp |

## MCP 工具速查

| 工具 | 参数 | 用途 |
|------|------|------|
| `push_html` | tag, file | 推送并显示设计稿叠加层（file 为本地绝对路径） |
| `adjust_overlay` | offsetX?, offsetY?, opacity? | 自动对齐偏移（支持小数 dp） |
| `hide_overlay` | - | 校正完成后隐藏 |
| `dom_all` | - | 全量 DOM 数据（坐标已含 offset） |
| `get_all_nodes` | - | 全量 Android View 快照 |
| `get_node` | id | 单个 View 坐标（辅助验证用） |
| `modify_view` | id, props | 修改 View 布局属性（支持 wrap_content） |
