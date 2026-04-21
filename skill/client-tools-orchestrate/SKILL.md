# client-tools:orchestrate

设计稿实现全流程编排器。管理工作目录、驱动 preprocess → implement → inspect 自动推进，支持跨会话恢复。

## 触发条件

- 用户说"开始"、"继续"、"恢复"、"开始设计稿实现"
- 用户调用 `/client-tools:orchestrate`

## 工作目录结构

```
<project-root>/
└── design/
    └── <yyyymmddHHMM>-<bizname>/
        ├── state.json      # 状态机
        ├── design.html     # 原始设计稿
        ├── design.json     # preprocess 产出
        └── drawables/      # Vector Drawable XML
```

## state.json 结构

```json
{
  "bizname": "login-phone",
  "phase": "implement",
  "viewport": 375,
  "anchor": { "id": "login_text_title", "edge": "top" },
  "target": {
    "module": "/path/to/android/module",
    "layout": "activity_login"
  },
  "history": [
    { "phase": "preprocess", "completedAt": "2026-04-21T10:00:00Z" }
  ]
}
```

`phase` 取值：`preprocess` | `implement` | `inspect` | `done`

## 工作流程

### Step 1：扫描未完成工作目录

扫描 `./design/*/state.json`，收集所有 `phase != "done"` 的条目。

**有未完成项时**，以表格列出：

```
编号  目录名                    当前阶段      最后更新
1     20260421-login-phone      implement     2026-04-21 10:00
```

询问用户：选择恢复哪个（输入编号），或输入 `n` 新建。

**无未完成项时**，直接进入新建流程。

### Step 2：新建流程

依次询问（每次只问一个问题）：
1. bizname（英文，用于目录名，如 `login-phone`）
2. HTML 设计稿的完整路径

然后：
1. 创建目录 `design/<yyyymmddHHMM>-<bizname>/`（时间戳取当前时间）
2. 将 HTML 文件复制到目录内，命名为 `design.html`
3. 写入初始 `state.json`（phase = "preprocess"，viewport/anchor 待填）
4. 自动进入 preprocess 阶段

### Step 3：根据 phase 分发

读取工作目录下的 `state.json`，按 phase 执行：

#### phase = "preprocess"

按照 `client-tools:preprocess` skill 的 Step 2-3 流程，向用户询问 viewport 和锚点，然后运行：

```bash
.claude/skills/client-tools-preprocess/scripts/.venv/bin/python .claude/skills/client-tools-preprocess/scripts/preprocess.py \
  --input <workdir>/design.html \
  --viewport <viewport> \
  --anchor-id <anchor_id> \
  --anchor-edge <anchor_edge> \
  --output <workdir>/design.json \
  --drawables-dir <workdir>/drawables/
```

完成后：
- 更新 `state.json`：填入 `viewport`、`anchor`，`phase` 改为 `"implement"`，`history` 追加记录
- 自动推进到 implement 阶段

#### phase = "implement"

若 `state.json.target` 为空，先收集（每次一个问题）：
1. 目标 Android module 根路径（如 `../my-app/app`，需包含 `src/main/res/` 目录）
2. 布局文件名（如 `activity_login`，不含 `.xml`）

将收集到的信息写入 `state.json.target`。

然后：
1. 若 `<workdir>/drawables/` 目录非空，将其中所有 `*.xml` 复制到 `<target.module>/src/main/res/drawable/`
2. 按照 `client-tools:implement` skill 流程继续执行，design.json 路径为 `<workdir>/design.json`

完成后：
- 更新 `state.json`：`phase` 改为 `"inspect"`，`history` 追加记录
- 自动推进到 inspect 阶段

#### phase = "inspect"

按照 `client-tools:inspect` skill 流程执行。

完成后：
- 更新 `state.json`：`phase` 改为 `"done"`，`history` 追加记录
- 提示用户全流程完成

#### phase = "done"

告知用户该设计稿已完成全流程，询问是否开始新的设计稿。

## state.json 更新规范

每次更新 state.json 必须：
1. 读取现有内容（避免覆盖其他字段）
2. 仅修改目标字段
3. `history` 数组追加，不覆盖
4. `completedAt` 使用 ISO 8601 格式（UTC），如 `"2026-04-21T10:00:00Z"`

## 注意事项

- `svg2vectordrawable` 需通过 `npx` 调用，本机需已安装 Node.js
- 若 drawables/ 目录为空（无 SVG），implement 阶段跳过复制步骤
- 工作目录路径在整个会话中保持不变，始终从 `state.json` 所在目录读取
