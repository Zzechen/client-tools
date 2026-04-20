# Inspect V2 设计文档

日期：2026-04-20  
范围：TODO #2 / #5 / #7

---

## 背景

当前 inspect 工作流存在三个核心问题：

1. **HTML id 与 Android View id 强绑定**：设计稿必须手动添加与 Android 一一对应的 id，两侧任意重命名都会导致匹配失效，流程脆弱。
2. **关联视图联动问题**：ConstraintLayout 是约束系统，`modify_view` 调整一个节点会导致下游节点位置联动偏移，逐节点校对陷入反复震荡。
3. **`modify_view` 不支持 `wrap_content`**：无法将被硬编码宽高的节点还原为自适应尺寸。

---

## 设计

### 一、解除 id 强绑定：基于坐标的自动匹配

**核心思路：** inspect 开始时，一次性获取两侧全量数据，AI 在上下文内做全局匹配，不依赖 id 对应关系。

**新增 MCP 工具：`get_all_nodes()`**

调用后返回当前页面所有有 id 的 View 的坐标快照：

```json
[
  { "id": "login_text_title", "type": "TEXT", "screenX": 24, "screenY": 186, "widthDp": 327, "heightDp": 38 },
  { "id": "login_btn_submit", "type": "CONTAINER", "screenX": 24, "screenY": 400, "widthDp": 327, "heightDp": 52 },
  ...
]
```

SDK 侧实现：复用已有的 `ViewTreeTraversal.traverseAll()`，新增 `/view/all` HTTP endpoint，MCP 封装为 `get_all_nodes` 工具。

**匹配流程：**

1. 调用 `dom_all()` 获取全量 DOM 节点（含 x、y、w、h、tagName、text）
2. 调用 `get_all_nodes()` 获取全量 View 节点
3. 用锚点坐标计算 DOM ↔ View 坐标系偏移量（offsetX、offsetY）
4. AI 对每个 View，在偏移修正后的 DOM 中寻找最佳匹配节点，匹配依据优先级：
   - **文字内容**（TextView.text == DOM.textContent，强信号）
   - **坐标距离**（|dx| + |dy| 最小）
   - **尺寸接近度**（|dw| + |dh| 最小）
   - **类型兼容性**（TextView ↔ text 标签，ImageView ↔ img 标签）
5. 双向验证：View A 匹配 DOM B，同时 DOM B 也应最近邻 View A，否则标记为「可疑匹配」
6. 输出匹配表，可疑匹配请用户确认后再进入校对

**HTML 侧变化：** 设计稿无需添加任何 id，extractor.py 恢复为全自动 id 生成。

---

### 二、联动问题：adjust + 全量 snapshot

**核心思路：** 每批 `modify_view` 操作结束后，调用一次 `get_all_nodes()` 获取全量快照，AI 一次性看清所有联动变化，统一更新 checklist。

**校对循环调整：**

```
旧流程：
  modify_view(A) → get_node(A) → 验证 A → modify_view(A) → ...

新流程：
  [批量推断本轮需要调整的节点] →
  modify_view(A) → modify_view(B) → ... →
  get_all_nodes() →
  AI 全局比对，更新所有节点状态 →
  进入下一轮
```

**关键原则：**
- 单轮内优先调整父容器，再调子节点（减少联动次数）
- 单轮调整数量建议 ≤ 5 个节点，避免快照难以分析
- 验收阶段（全部节点收敛后）调用一次 `get_all_nodes()` 做最终核查

**token 消耗控制：**
- 中间校对循环用单个 `get_node` 验证，不重复调用 `get_all_nodes()`
- 只在每轮批量调整结束后 + 最终验收时调用 `get_all_nodes()`，预计每次校对全流程额外消耗 ≤ 10 次调用（约 6000 token）

---

### 三、`modify_view` 支持 `wrap_content`

**SDK 侧（ViewModifier.kt）：**

`ModifyRequest` 的 `widthDp` / `heightDp` 类型从 `Float?` 改为支持特殊字符串 `"wrap_content"`：

```kotlin
// 伪代码
val width = when (props.widthDp) {
    "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
    else -> dpToPx(props.widthDp.toFloat())
}
```

实现上可以用 `sealed class DpValue { data class Dp(val value: Float); object WrapContent }` 或直接用 `String?` 在 handler 层判断。

**MCP 侧（view.ts）：** `widthDp` / `heightDp` 参数类型改为 `number | "wrap_content"`。

**AI 使用示例：**
```
modify_view("login_text_title", { widthDp: "wrap_content" })
```

---

## 影响范围

| 模块 | 变更 |
|------|------|
| `sdk/runtime/ViewTreeTraversal.kt` | 无需改动（traverseAll 已有） |
| `sdk/http/ApiHandler.kt` | 新增 `/view/all` endpoint |
| `sdk/runtime/ViewModifier.kt` | widthDp/heightDp 支持 wrap_content |
| `sdk/shared/models/ViewProps` | widthDp/heightDp 类型扩展 |
| `mcp/src/tools/view.ts` | 新增 `get_all_nodes` 工具，修改参数类型 |
| `skill/client-tools-inspect` | 匹配逻辑、校对循环、阈值全部重写 |
| `skill/preprocess/extractor.py` | 移除 HTML id 读取逻辑，恢复自动 id |
| `docs/examples/login-phone.html` | 移除手动添加的 login_ id |

---

## 不在本次范围

- TODO #1：核对阶段不改代码（skill 行为约束，下次迭代）
- TODO #3：需求目录结构（目录规范，下次迭代）
- TODO #4：阈值收严为 < 1dp（本次 skill 重写时一并落地）
- TODO #6：protobuf 迁移（独立大改动，单独 spec）
