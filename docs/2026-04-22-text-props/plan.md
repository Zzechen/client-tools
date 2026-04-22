# TextView 文字属性扩展 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `modify_view` MCP 工具中扩展对 TextView 文字属性的支持，新增字间距（letterSpacing）、行间距（lineSpacing）和 includeFontPadding 三个属性，供 inspect 阶段人工调整文字对齐。

**Architecture:** 改动沿现有链路向上传递：`ViewProps`（KMP shared 数据模型）→ `ViewModifier`（Android SDK 运行时应用）→ `view.ts`（MCP 工具 schema）。新字段全部可选，不改变现有字段语义。

**Tech Stack:** Kotlin (KMP shared + Android SDK), TypeScript (MCP), kotlinx.serialization, NanoHTTPD

---

## 涉及文件

- Modify: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/ModifyViewRequest.kt`
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt`
- Modify: `mcp/src/tools/view.ts`
- Modify: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/SerializationTest.kt`
- Modify: `skill/client-tools-inspect/SKILL.md`

---

### Task 1: 扩展 ViewProps 数据模型

**Files:**
- Modify: `packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/ModifyViewRequest.kt`
- Modify: `packages/shared/src/commonTest/kotlin/com/clienttools/shared/SerializationTest.kt`

- [ ] **Step 1: 写失败测试**

在 `SerializationTest.kt` 末尾（`}` 前）添加：

```kotlin
@Test
fun testViewPropsTextAttrs() {
    val request = ModifyViewRequest(
        id = "text_1",
        props = ViewProps(
            letterSpacingEm = 0.05f,
            lineSpacingExtraDp = 4f,
            includeFontPadding = false
        )
    )
    val encoded = json.encodeToString(request)
    val decoded: ModifyViewRequest = json.decodeFromString(encoded)
    assertEquals(0.05f, decoded.props.letterSpacingEm)
    assertEquals(4f, decoded.props.lineSpacingExtraDp)
    assertEquals(false, decoded.props.includeFontPadding)
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd packages && ./gradlew :shared:jvmTest --tests "*.SerializationTest.testViewPropsTextAttrs" 2>&1 | tail -20
```

期望：编译失败，`Unresolved reference: letterSpacingEm`

- [ ] **Step 3: 在 ViewProps 中添加三个新字段**

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
    val heightDp: String? = null,
    val letterSpacingEm: Float? = null,
    val lineSpacingExtraDp: Float? = null,
    val includeFontPadding: Boolean? = null
)

@Serializable
data class ModifyViewRequest(
    val id: String,
    val props: ViewProps
)
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd packages && ./gradlew :shared:jvmTest --tests "*.SerializationTest" 2>&1 | tail -20
```

期望：所有 SerializationTest 通过，无 FAILED

- [ ] **Step 5: 提交**

```bash
git add packages/shared/src/commonMain/kotlin/com/clienttools/shared/models/ModifyViewRequest.kt \
        packages/shared/src/commonTest/kotlin/com/clienttools/shared/SerializationTest.kt
git commit -m "feat(shared): add text props to ViewProps (letterSpacing, lineSpacing, includeFontPadding)"
```

---

### Task 2: 在 ViewModifier 中应用文字属性

**Files:**
- Modify: `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt`

注意：`letterSpacing` 在 Android 上单位是 em（相对于字号的倍数），API 21+；`setLineSpacing(extra, mult)` API 1+；`includeFontPadding` API 1+。全部兼容 API 26 目标。

- [ ] **Step 1: 修改 ViewModifier.kt 的 modify 函数**

将 `packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt` 改为：

```kotlin
package com.clienttools.sdk.runtime

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

    private fun resolveDimension(value: String?, density: Float): Int? {
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

        resolveDimension(props.widthDp, density)?.let { layoutParams.width = it }
        resolveDimension(props.heightDp, density)?.let { layoutParams.height = it }

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

        if (view is TextView) {
            props.letterSpacingEm?.let { view.letterSpacing = it }
            props.lineSpacingExtraDp?.let { extra ->
                view.setLineSpacing(extra * density, view.lineSpacingMultiplier)
            }
            props.includeFontPadding?.let { view.includeFontPadding = it }
        }
    }
}
```

- [ ] **Step 2: 编译 SDK 确认无错误**

```bash
cd packages && ./gradlew :android:sdk:compileDebugKotlin 2>&1 | tail -30
```

期望：`BUILD SUCCESSFUL`，无编译错误

- [ ] **Step 3: 提交**

```bash
git add packages/android/sdk/src/main/kotlin/com/clienttools/sdk/runtime/ViewModifier.kt
git commit -m "feat(sdk): apply letterSpacing/lineSpacing/includeFontPadding to TextView in ViewModifier"
```

---

### Task 3: 扩展 MCP view.ts 工具 schema

**Files:**
- Modify: `mcp/src/tools/view.ts`

- [ ] **Step 1: 更新 ViewPropsSchema 和工具描述**

将 `mcp/src/tools/view.ts` 中的 `ViewPropsSchema` 和 `modify_view` 工具替换为：

```typescript
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
  letterSpacingEm: z.number().optional(),
  lineSpacingExtraDp: z.number().optional(),
  includeFontPadding: z.boolean().optional(),
}).describe("View 布局属性，margin/padding 为差值（dp），width/height 为绝对值（dp）或 \"wrap_content\"；letterSpacingEm 为字间距（em 单位），lineSpacingExtraDp 为额外行间距（dp），includeFontPadding 控制字体内置 padding");
```

`modify_view` 工具描述改为：

```typescript
  server.tool(
    "modify_view",
    "修改 Android View 的布局属性（margin/padding/size），单位 dp；TextView 额外支持 letterSpacingEm、lineSpacingExtraDp、includeFontPadding",
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
```

- [ ] **Step 2: 编译 MCP 确认无错误**

```bash
cd mcp && npm run build 2>&1 | tail -20
```

期望：`Found 0 errors`，无 TypeScript 编译错误

- [ ] **Step 3: 提交**

```bash
git add mcp/src/tools/view.ts
git commit -m "feat(mcp): extend modify_view schema with letterSpacingEm, lineSpacingExtraDp, includeFontPadding"
```

---

### Task 4: 更新 inspect skill 文档

**Files:**
- Modify: `skill/client-tools-inspect/SKILL.md`

- [ ] **Step 1: 在 MCP 工具速查表中更新 modify_view 行**

找到 `skill/client-tools-inspect/SKILL.md` 中的 MCP 工具速查表，将 `modify_view` 行替换为：

```markdown
| `modify_view` | id, props | 修改 View 布局属性（支持 wrap_content）；TextView 额外支持 `letterSpacingEm`（em）、`lineSpacingExtraDp`（dp 差值）、`includeFontPadding`（bool） |
```

- [ ] **Step 2: 在阶段三校对循环的调整策略原则末尾添加文字节点说明**

在「注意：校对阶段不修改 XML 代码」段落前插入：

```markdown
**文字节点行高/字间距调整：**
- 若文字节点 dy 超阈值但父容器 dy 在阈值内（位置对、文字在容器内偏移），优先考虑行高问题
- 调整顺序：先 `includeFontPadding=false` 去掉字体内置 padding，再用 `lineSpacingExtraDp` 微调行间距
- `letterSpacingEm` 对应 CSS `letter-spacing`，单位为 em（如 CSS `0.1px` at 14sp ≈ 0.007em）
- 文字属性调整后需同步回 XML：`android:includeFontPadding`、`android:lineSpacingExtra`、`android:letterSpacing`
```

- [ ] **Step 3: 提交**

```bash
git add skill/client-tools-inspect/SKILL.md
git commit -m "docs(inspect): document TextView text props in modify_view quick reference"
```

---

## 自查

- **Spec 覆盖：** 字间距 ✅ Task 1/2/3，行间距 ✅ Task 1/2/3，includeFontPadding ✅ Task 1/2/3，文档 ✅ Task 4
- **占位符：** 无 TBD/TODO
- **类型一致性：** `letterSpacingEm: Float?`（shared/SDK）↔ `z.number().optional()`（MCP）✅；`lineSpacingExtraDp: Float?` ↔ `z.number().optional()` ✅；`includeFontPadding: Boolean?` ↔ `z.boolean().optional()` ✅
