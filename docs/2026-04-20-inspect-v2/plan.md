# Inspect V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 解除 HTML id 与 Android View id 强绑定，新增 `get_all_nodes()` MCP 工具，支持 `wrap_content`，重写 inspect skill 校对循环。

**Architecture:** SDK 新增 `/api/nodes/all` endpoint 返回全量 View 快照；`ViewProps` 的 `widthDp`/`heightDp` 扩展为支持 `"wrap_content"` 字符串；inspect skill 改为全量双侧数据 + AI 坐标匹配，每轮批量调整后调一次 `get_all_nodes()` 刷新全局状态。

**Tech Stack:** Kotlin（Android SDK）、TypeScript（MCP）、Python（extractor）、kotlinx.serialization

---

## 文件变更清单

| 文件 | 类型 | 变更说明 |
|------|------|----------|
| `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/ModifyViewRequest.kt` | 修改 | `ViewProps.widthDp`/`heightDp` 从 `Float?` 改为 `String?`（兼容数字字符串和 `"wrap_content"`） |
| `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt` | 修改 | 解析 `widthDp`/`heightDp` 的 `"wrap_content"` 特殊值 |
| `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewQueryService.kt` | 修改 | 新增 `getAllViewInfos()` 方法 |
| `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt` | 修改 | 新增 `handleGetAllNodes()` 方法 |
| `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt` | 修改 | 新增 `/api/nodes/all` 路由 |
| `mcp/src/tools/view.ts` | 修改 | 新增 `get_all_nodes` 工具，`widthDp`/`heightDp` 类型改为 `number \| "wrap_content"` |
| `skill/preprocess/extractor.py` | 修改 | 移除 HTML id 读取逻辑，恢复自动 id 生成 |
| `/Users/zzc/.claude/skills/client-tools-inspect/skill.md` | 修改 | 重写匹配逻辑、校对循环、阈值（< 1dp） |

---

## Task 1：ViewProps 支持 wrap_content

**Files:**
- Modify: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/ModifyViewRequest.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt`
- Test: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/SerializationTest.kt`

- [ ] **Step 1：在 SerializationTest.kt 末尾添加失败测试**

```kotlin
@Test
fun testViewPropsWrapContent() {
    val json = """{"widthDp":"wrap_content","heightDp":"42.0"}"""
    val props = Json.decodeFromString<ViewProps>(json)
    assertEquals("wrap_content", props.widthDp)
    assertEquals("42.0", props.heightDp)
}
```

- [ ] **Step 2：运行测试确认失败**

```bash
cd packages && ./gradlew :shared:jvmTest --tests "*.SerializationTest.testViewPropsWrapContent" 2>&1 | tail -20
```
期望：FAIL，`widthDp` 类型不匹配

- [ ] **Step 3：修改 ViewProps，将 widthDp/heightDp 类型改为 String?**

将 `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/ModifyViewRequest.kt` 改为：

```kotlin
package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ViewProps(
    val marginTopDiffDp: Float? = null,
    val marginBottomDiffDp: Float? = null,
    val marginLeftDiffDp: Float? = null,
    val marginRightDiffDp: Float? = null,
    val paddingTopDiffDp: Float? = null,
    val paddingBottomDiffDp: Float? = null,
    val paddingLeftDiffDp: Float? = null,
    val paddingRightDiffDp: Float? = null,
    val widthDp: String? = null,
    val heightDp: String? = null
)

@Serializable
data class ModifyViewRequest(
    val id: String,
    val props: ViewProps
)
```

- [ ] **Step 4：修改 ViewModifier.kt，解析 wrap_content**

```kotlin
package com.clienttools.sdk.runtime

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.shared.models.ViewProps

object ViewModifier {
    fun apply(viewId: String, props: ViewProps): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        return if (views.isEmpty()) false else {
            views.forEach { view ->
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    modify(view, props)
                } else {
                    val activity = ClientToolsSDK.getCurrentActivity()
                    activity?.runOnUiThread { modify(view, props) }
                }
            }
            true
        }
    }

    private fun resolveDimension(value: String?, currentPx: Int, density: Float): Int? {
        if (value == null) return null
        if (value == "wrap_content") return ViewGroup.LayoutParams.WRAP_CONTENT
        val dp = value.toFloatOrNull() ?: return null
        return (dp * density).toInt()
    }

    private fun modify(view: View, props: ViewProps) {
        val displayMetrics = view.resources.displayMetrics
        val density = displayMetrics.density
        val dpToPx = { dp: Float -> (dp * density).toInt() }

        val layoutParams = view.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (layoutParams is ViewGroup.MarginLayoutParams) {
            val top = props.marginTopDiffDp?.let { layoutParams.topMargin + dpToPx(it) } ?: layoutParams.topMargin
            val bottom = props.marginBottomDiffDp?.let { layoutParams.bottomMargin + dpToPx(it) } ?: layoutParams.bottomMargin
            val left = props.marginLeftDiffDp?.let { layoutParams.leftMargin + dpToPx(it) } ?: layoutParams.leftMargin
            val right = props.marginRightDiffDp?.let { layoutParams.rightMargin + dpToPx(it) } ?: layoutParams.rightMargin
            layoutParams.setMargins(left, top, right, bottom)
        }

        resolveDimension(props.widthDp, layoutParams.width, density)?.let { layoutParams.width = it }
        resolveDimension(props.heightDp, layoutParams.height, density)?.let { layoutParams.height = it }

        view.layoutParams = layoutParams

        props.paddingTopDiffDp?.let {
            view.setPadding(view.paddingLeft, view.paddingTop + dpToPx(it), view.paddingRight, view.paddingBottom)
        }
        props.paddingBottomDiffDp?.let {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom + dpToPx(it))
        }
        props.paddingLeftDiffDp?.let {
            view.setPadding(view.paddingLeft + dpToPx(it), view.paddingTop, view.paddingRight, view.paddingBottom)
        }
        props.paddingRightDiffDp?.let {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight + dpToPx(it), view.paddingBottom)
        }
    }
}
```

- [ ] **Step 5：运行测试确认通过**

```bash
cd packages && ./gradlew :shared:jvmTest --tests "*.SerializationTest.testViewPropsWrapContent" 2>&1 | tail -10
```
期望：PASS

- [ ] **Step 6：编译 Android demo 确认无编译错误**

```bash
cd packages && ./gradlew :android:demo:assembleDebug 2>&1 | tail -10
```
期望：BUILD SUCCESSFUL

- [ ] **Step 7：Commit**

```bash
git add packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/ModifyViewRequest.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt \
        packages/shared/src/commonTest/kotlin/com/clienttools/shared/SerializationTest.kt
git commit -m "feat(sdk): support wrap_content in ViewProps widthDp/heightDp"
```

---

## Task 2：新增 get_all_nodes SDK endpoint

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewQueryService.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt`

- [ ] **Step 1：在 ViewQueryService.kt 新增 getAllViewInfos()**

在文件末尾 `}` 之前添加：

```kotlin
fun getAllViewInfos(): List<ViewInfo> {
    val results = mutableListOf<ViewInfo>()
    ViewTreeTraversal.traverseAll { view ->
        if (view.id == View.NO_ID) return@traverseAll
        val id = try {
            view.resources.getResourceName(view.id).substringAfterLast("/")
        } catch (e: Exception) {
            return@traverseAll
        }
        results.add(buildViewInfo(view, id))
    }
    return results
}
```

- [ ] **Step 2：在 ApiHandler.kt 新增 handleGetAllNodes()**

在 `handleGetNode` 方法之后添加：

```kotlin
fun handleGetAllNodes(): NanoHTTPD.Response {
    return try {
        val viewInfos = ViewQueryService.getAllViewInfos()
        val json = Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ViewInfo.serializer()),
            viewInfos
        )
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json)
    } catch (e: Exception) {
        Log.e("ApiHandler", "Error getting all nodes", e)
        NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json",
            """{"error":"${e.message}"}"""
        )
    }
}
```

- [ ] **Step 3：在 HttpServer.kt 注册路由**

在 `method == Method.GET && uri.startsWith("/api/nodes/")` 的 when 分支之前添加：

```kotlin
method == Method.GET && uri == "/api/nodes/all" -> {
    ApiHandler.handleGetAllNodes()
}
```

> 注意：必须在 `/api/nodes/` 前缀匹配之前，避免被吞掉。

- [ ] **Step 4：编译并安装到设备**

```bash
cd packages && ./gradlew :android:demo:assembleDebug 2>&1 | tail -5
adb install -r android/demo/build/outputs/apk/debug/demo-debug.apk
adb shell am start -n com.clienttools.demo/.MainActivity
adb forward tcp:8080 tcp:8080
```

- [ ] **Step 5：手动验证 endpoint**

```bash
curl -s http://localhost:8080/api/nodes/all | python3 -m json.tool | head -40
```
期望：返回 JSON 数组，每项包含 id、type、screenX、screenY、widthDp、heightDp

- [ ] **Step 6：Commit**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewQueryService.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/ApiHandler.kt \
        packages/android/sdk/src/main/kotlin/com/clienttools/sdk/http/HttpServer.kt
git commit -m "feat(sdk): add /api/nodes/all endpoint for bulk View snapshot"
```

---

## Task 3：MCP 新增 get_all_nodes 工具 + 修改 modify_view 参数类型

**Files:**
- Modify: `mcp/src/tools/view.ts`

- [ ] **Step 1：修改 view.ts**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGet, sdkPost } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

const DpValue = z.union([z.number(), z.literal("wrap_content")]);

const ViewPropsSchema = z.object({
  marginTopDiffDp: z.number().optional(),
  marginBottomDiffDp: z.number().optional(),
  marginLeftDiffDp: z.number().optional(),
  marginRightDiffDp: z.number().optional(),
  paddingTopDiffDp: z.number().optional(),
  paddingBottomDiffDp: z.number().optional(),
  paddingLeftDiffDp: z.number().optional(),
  paddingRightDiffDp: z.number().optional(),
  widthDp: DpValue.optional(),
  heightDp: DpValue.optional(),
}).describe("View 布局属性，margin/padding 为差值（dp），width/height 为绝对值（dp）或 \"wrap_content\"");

export function registerViewTools(server: McpServer): void {
  server.tool(
    "get_node",
    "查询 Android 原生 View 节点的屏幕位置和尺寸",
    {
      id: z.string().describe("Android View 的 resource id（不含包名前缀，如 btn_login）"),
    },
    async ({ id }) => {
      try {
        const result = await sdkGet(`/api/nodes/${encodeURIComponent(id)}`);
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_all_nodes",
    "获取当前页面所有 Android View 节点的屏幕坐标和尺寸快照",
    {},
    async () => {
      try {
        const result = await sdkGet("/api/nodes/all");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "modify_view",
    "修改 Android View 的布局属性（margin/padding/size），单位 dp",
    {
      id: z.string().describe("Android View 的 resource id"),
      props: ViewPropsSchema,
    },
    async ({ id, props }) => {
      try {
        const propsToSend = {
          ...props,
          widthDp: props.widthDp !== undefined ? String(props.widthDp) : undefined,
          heightDp: props.heightDp !== undefined ? String(props.heightDp) : undefined,
        };
        const result = await sdkPost("/api/modify", { id, props: propsToSend });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
```

- [ ] **Step 2：编译 MCP**

```bash
cd mcp && npm run build 2>&1 | tail -10
```
期望：无错误

- [ ] **Step 3：重启 MCP server 并验证工具注册**

在 Claude Code 中断开并重新连接 MCP，确认 `get_all_nodes` 出现在工具列表中。

- [ ] **Step 4：Commit**

```bash
git add mcp/src/tools/view.ts mcp/dist/
git commit -m "feat(mcp): add get_all_nodes tool, support wrap_content in modify_view"
```

---

## Task 4：extractor.py 移除 HTML id 读取逻辑

**Files:**
- Modify: `skill/preprocess/extractor.py`

- [ ] **Step 1：修改 extractor.py，移除 html_id 读取，恢复自动 id**

将 `extract_nodes` 函数中以下两行：

```python
html_id = await el.evaluate("el => el.id || ''")
node_id = html_id if html_id else _next_id(counters, node_type)
```

改为：

```python
node_id = _next_id(counters, node_type)
```

同时删除 `await el.evaluate(f"el => el.setAttribute('data-ct-id', '{node_id}')")` 之前不再需要的 html_id 变量。

- [ ] **Step 2：运行 preprocess 测试**

```bash
cd /Users/zzc/Desktop/works/client-tools
skill/preprocess/.venv/bin/pytest tests/preprocess/ -q 2>&1 | tail -10
```
期望：全部通过

- [ ] **Step 3：Commit**

```bash
git add skill/preprocess/extractor.py
git commit -m "refactor(preprocess): remove HTML id dependency, use auto-generated ids"
```

---

## Task 5：重写 client-tools-inspect skill

**Files:**
- Modify: `/Users/zzc/.claude/skills/client-tools-inspect/skill.md`

- [ ] **Step 1：重写 skill.md**

```markdown
---
name: client-tools-inspect
description: Use when user wants to visually inspect or correct an Android screen against its design, or says "开始校正"/"视觉核对"/"inspect"
---

# client-tools:inspect

运行时视觉校正工作流。将设计稿叠加到 Android App 上，通过坐标自动匹配 DOM 节点与 Android View，批量调整直到通过验收。

## 触发条件

- 用户说"开始校正"、"视觉核对"、"inspect"
- 用户调用 `/client-tools:inspect`

## 前置条件

- 设计稿 HTML 文件已准备好（无需添加 id）
- App 已运行到目标页面
- MCP client-tools 工具已连接

## 工作流程

### 阶段一：叠加对齐

1. 调用 `push_html(tag, html)` 推送设计稿叠加层
2. 提示用户手动调整偏移/透明度，使叠加层与 App 视觉对齐
3. 用户确认对齐后继续

### 阶段二：全量数据采集 + 自动匹配

1. 调用 `dom_all()` 获取全量 DOM 节点（含 x、y、w、h、tagName、text）
2. 调用 `get_all_nodes()` 获取全量 Android View 节点
3. 根据锚点节点（用户对齐时参照的元素）计算坐标系偏移量：
   - 选取一个在两侧都能识别的元素（如顶部关闭按钮）
   - offset = View.screenY - DOM.y（x 方向同理）
4. 对所有 DOM 坐标做偏移修正：dom_corrected_x = dom.x + offsetX，dom_corrected_y = dom.y + offsetY
5. 对每个 Android View，按以下优先级在 DOM 中寻找最佳匹配：
   - **文字内容匹配**（View 文字 == DOM textContent，强信号，优先）
   - **坐标距离**（|dx| + |dy| 最小）
   - **尺寸接近度**（|dw| + |dh| 最小）
   - **类型兼容性**（TextView ↔ p/span/h1-h6/button，ImageView ↔ img，ViewGroup ↔ div）
6. 双向验证：View A 匹配 DOM B，且 DOM B 反向也最近邻 View A，否则标记「可疑」
7. 输出匹配表，可疑匹配请用户确认

匹配表格式：
```
View id                   DOM 元素        文字          dx    dy    dw    dh
login_text_title          h1             欢迎回来       0     0     0     0
login_btn_submit          button         获取验证码      0     1    -1     0
⚠️ login_logo_name        div(可疑)       PULSE          3     2     5     4
```

### 阶段三：批量校对循环

使用匹配表，按从上到下（screenY 升序）顺序校对每个节点。

**跳过规则：** type 为 CONTAINER 且无文字内容的节点，仅校对位置和尺寸，跳过样式属性。

**验收阈值：** 所有维度差异 < 1dp 即通过。

**每轮校对流程（最多 5 轮）：**

```
① 找出本轮所有差异 ≥ 1dp 的节点
② 优先处理父容器（位置靠上的节点），每轮最多 5 个节点
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
- login_logo_section: 最终差异 dx=0, dy=3dp（超出阈值）建议：检查 marginTop 约束链
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
| `push_html` | tag, html | 推送并显示设计稿叠加层 |
| `adjust_overlay` | dx?, dy?, alpha? | 调整偏移/透明度 |
| `hide_overlay` | - | 校正完成后隐藏 |
| `dom_all` | - | 全量 DOM 数据 |
| `get_all_nodes` | - | 全量 Android View 快照 |
| `get_node` | id | 单个 View 坐标（辅助验证用） |
| `modify_view` | id, props | 修改 View 布局属性（支持 wrap_content） |
```

- [ ] **Step 2：确认 skill 文件写入成功**

```bash
head -5 /Users/zzc/.claude/skills/client-tools-inspect/skill.md
```
期望：输出 `---` frontmatter 开头

- [ ] **Step 3：Commit**

```bash
git add /Users/zzc/.claude/skills/client-tools-inspect/skill.md
git commit -m "feat(skill): rewrite inspect skill with coordinate-based matching and batch snapshot"
```

---

## 自检

**Spec 覆盖检查：**
- ✅ 解除 id 强绑定 → Task 4（extractor）+ Task 5（skill 匹配逻辑）
- ✅ get_all_nodes() → Task 2（SDK）+ Task 3（MCP）
- ✅ wrap_content → Task 1（ViewProps + ViewModifier）+ Task 3（MCP 类型）
- ✅ 联动问题 → Task 5（批量调整 + 全量 snapshot 循环）
- ✅ 阈值 < 1dp → Task 5（skill 阈值）
- ✅ 核对阶段不改代码 → Task 5（checklist 输出，等用户确认再写 XML）

**类型一致性：**
- `ViewProps.widthDp: String?` 贯穿 Task 1 → Task 3（MCP 转 String 后发送）✅
- `getAllViewInfos()` 在 Task 2 定义，Task 2 ApiHandler 调用 ✅
- `get_all_nodes` MCP 工具在 Task 3 注册，Task 5 skill 使用 ✅
