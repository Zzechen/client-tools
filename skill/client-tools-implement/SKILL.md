---
name: client-tools-implement
description: Use when user wants to generate Android layout code from design.json, or says "生成 Android 代码"/"实现布局"/"开始编码"
---

# client-tools:implement

Android 布局编码工作流。以 design.json 为基准，生成 Android XML 布局代码，并确认 App 已运行到目标页面。

## 触发条件

- 用户说"生成 Android 代码"、"实现布局"、"开始编码"
- 用户调用 `/client-tools:implement`

## 前置条件

- design.json 已生成（由 `client-tools:preprocess` 产出）
- 用户提供 design.json 路径（若未提供，询问）

## 工作流程

### Step 1：读取 design.json

读取 design.json，理解以下内容：
- `viewport`：设计稿宽度
- `anchor`：锚点节点 id 和边缘
- `nodes`：所有节点列表，每个节点包含 id、type、rel（相对锚点坐标）、attrs

### Step 2：生成 Android 布局代码

根据节点结构生成 XML 布局文件。**关键约束（不可违反）：**

1. **所有 View 必须设置 `android:id`**，包括中间容器层
2. **id 命名规则**：`<页面前缀>_<节点id>`，例如节点 id 为 `text_title`、页面为 `login`，则 id 为 `@+id/login_text_title`
3. **布局方式**：统一使用 XML，不使用 Jetpack Compose
4. **最低 API**：Android 26（Android 8.0）

节点 type 与 Android View 对应关系：

| type | Android View |
|------|-------------|
| text | TextView |
| image | ImageView（见下方 drawable 说明） |
| list | RecyclerView |
| container | ViewGroup（FrameLayout / LinearLayout / ConstraintLayout） |
| drawable | 不生成 View，在背景容器中按需引用 `@drawable/<attrs.drawable>` |

**IMAGE 节点 drawable 处理：** 若节点 `attrs.drawable` 非空，生成 ImageView 时使用：
```xml
android:src="@drawable/<attrs.drawable>"
```

**DRAWABLE 节点：** 不自动生成 View，代表装饰背景 SVG（如网格、波浪线），在生成父容器背景时按需引用 `@drawable/<attrs.drawable>`。

### Step 3：提示用户运行 App

告知用户：
1. 将生成的布局代码集成到项目
2. 运行 App 并导航到对应页面
3. 完成后告知 AI

### Step 4：确认页面就绪

调用 MCP 工具确认当前页面：

```
get_last_event()
```

检查返回的 `activityName` 是否为目标页面。若页面不匹配，提示用户重新导航。

### Step 5：完成

确认页面就绪后，提示下一步：调用 `client-tools:inspect` 开始视觉校正。

## 注意事项

- 若 design.json 中 `container` 类型节点仅用于布局分组，可根据实际情况选择合适的 ViewGroup
- RecyclerView item 布局中的 View id 会在校正阶段被批量修改，命名需一致
