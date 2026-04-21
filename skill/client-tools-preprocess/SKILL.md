---
name: client-tools-preprocess
description: Use when user wants to preprocess a design HTML file, generate design.json, or says "预处理设计稿"/"处理 HTML"/"生成 design.json"
---

# client-tools:preprocess

设计稿预处理工作流。将 HTML/CSS 设计稿转换为结构化 design.json，作为 Android 编码的基准。

## 触发条件

- 用户说"预处理设计稿"、"处理 HTML"、"生成 design.json"
- 用户调用 `/client-tools:preprocess`

## 工作流程

### Step 1：收集参数

询问用户以下信息（逐一询问，不要一次列出全部）：

1. HTML 设计稿文件路径（绝对路径或相对项目根目录的路径）
2. 设计稿视口宽度（px），例如 375（iPhone）、390（iPhone Pro）、360（常见 Android）

### Step 2：列出节点

在项目根目录执行：

```bash
skill/preprocess/.venv/bin/python skill/preprocess/preprocess.py \
  --input <HTML路径> \
  --viewport <viewport宽度> \
  --list-only
```

将节点列表展示给用户，格式示例：

```
id: text_title    type: text    screenX: 100  screenY: 200  w: 240  h: 36
id: img_avatar    type: image   screenX: 80   screenY: 160  w: 40   h: 40
...
```

### Step 3：用户选锚点

引导用户从节点列表中选择锚点：

- **锚点 id**：选择视觉位置稳定的元素，如页面顶部标题、固定 header
- **锚点边缘**（anchor-edge）：`top`（默认）或 `bottom`

### Step 4：生成 design.json

执行完整预处理：

```bash
skill/preprocess/.venv/bin/python skill/preprocess/preprocess.py \
  --input <HTML路径> \
  --viewport <viewport宽度> \
  --anchor-id <锚点id> \
  --anchor-edge <top|bottom>
```

输出路径默认与 HTML 同目录同名 `.json`（可通过 `--output` 指定）。

### Step 5：确认完成

告知用户：
- design.json 输出路径
- 节点总数
- 锚点信息

提示下一步：调用 `client-tools:implement` 开始生成 Android 代码。

## 工作目录模式（由 orchestrator 调用时）

当由 `client-tools:orchestrate` 调用时，preprocess 使用工作目录模式：
- HTML 已复制到工作目录，路径由 orchestrator 提供
- `--output` 指向 `<workdir>/design.json`
- `--drawables-dir` 指向 `<workdir>/drawables/`，自动提取 SVG 并转换为 Vector Drawable
- 完成后 orchestrator 负责更新 `state.json` 并推进到 implement 阶段

## 常见问题

- **脚本找不到**：确认已在项目根目录执行，且 `skill/preprocess/.venv/` 已初始化（运行 `cd skill/preprocess && python -m venv .venv && .venv/bin/pip install -r requirements.txt`）
- **节点列表为空**：检查 HTML 文件路径是否正确，viewport 值是否合理
