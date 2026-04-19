# client-tools Skills 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 3 个 Claude Code Skill（preprocess / implement / inspect），驱动「设计稿预处理 → Android 编码 → 运行时视觉校正」全流程。

**Architecture:** 每个 skill 为独立目录，内含一个 SKILL.md。3 个目录放在 `skill/` 下，通过 `install.sh` 软链到 `~/.claude/skills/`。Skill 内容为纯 Markdown 工作流指南，不含可执行代码。

**Tech Stack:** Claude Code Skill（Markdown + YAML frontmatter）、MCP client-tools 工具集、Python preprocess 脚本

---

## 文件结构

| 路径 | 操作 | 职责 |
|------|------|------|
| `skill/client-tools-preprocess/SKILL.md` | 新建 | preprocess skill 主文件 |
| `skill/client-tools-implement/SKILL.md` | 新建 | implement skill 主文件 |
| `skill/client-tools-inspect/SKILL.md` | 新建 | inspect skill 主文件 |
| `install.sh` | 修改 | 追加 3 个 skill 的软链命令 |

---

## Task 1：创建 `client-tools-preprocess` Skill

**Files:**
- Create: `skill/client-tools-preprocess/SKILL.md`
- Modify: `install.sh`

- [ ] **Step 1: 创建目录和 SKILL.md**

```bash
mkdir -p skill/client-tools-preprocess
```

内容写入 `skill/client-tools-preprocess/SKILL.md`：

```markdown
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

## 常见问题

- **脚本找不到**：确认已在项目根目录执行，且 `skill/preprocess/.venv/` 已初始化（运行 `cd skill/preprocess && python -m venv .venv && .venv/bin/pip install -r requirements.txt`）
- **节点列表为空**：检查 HTML 文件路径是否正确，viewport 值是否合理
```

- [ ] **Step 2: 验证文件内容正确**

```bash
head -5 skill/client-tools-preprocess/SKILL.md
```

期望输出：frontmatter 的 `---` 起始行和 `name: client-tools-preprocess`

- [ ] **Step 3: 更新 install.sh，追加 preprocess skill 软链**

在 `install.sh` 中追加（在文件末尾）：

```bash
# client-tools skills
ln -sf "$(pwd)/skill/client-tools-preprocess" "$HOME/.claude/skills/client-tools-preprocess"
ln -sf "$(pwd)/skill/client-tools-implement" "$HOME/.claude/skills/client-tools-implement"
ln -sf "$(pwd)/skill/client-tools-inspect" "$HOME/.claude/skills/client-tools-inspect"
echo "[OK] client-tools skills linked"
```

- [ ] **Step 4: 提交**

```bash
git add skill/client-tools-preprocess/SKILL.md install.sh
git commit -m "feat(skill): add client-tools-preprocess skill"
```

---

## Task 2：创建 `client-tools-implement` Skill

**Files:**
- Create: `skill/client-tools-implement/SKILL.md`

- [ ] **Step 1: 创建目录和 SKILL.md**

```bash
mkdir -p skill/client-tools-implement
```

内容写入 `skill/client-tools-implement/SKILL.md`：

```markdown
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
| image | ImageView |
| list | RecyclerView |
| container | ViewGroup（FrameLayout / LinearLayout / ConstraintLayout） |

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
```

- [ ] **Step 2: 验证文件存在**

```bash
head -5 skill/client-tools-implement/SKILL.md
```

期望输出：frontmatter 的 `---` 起始行和 `name: client-tools-implement`

- [ ] **Step 3: 提交**

```bash
git add skill/client-tools-implement/SKILL.md
git commit -m "feat(skill): add client-tools-implement skill"
```

---

## Task 3：创建 `client-tools-inspect` Skill

**Files:**
- Create: `skill/client-tools-inspect/SKILL.md`

- [ ] **Step 1: 创建目录和 SKILL.md**

```bash
mkdir -p skill/client-tools-inspect
```

内容写入 `skill/client-tools-inspect/SKILL.md`：

```markdown
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
```

- [ ] **Step 2: 验证文件存在**

```bash
head -5 skill/client-tools-inspect/SKILL.md
```

期望输出：frontmatter 的 `---` 起始行和 `name: client-tools-inspect`

- [ ] **Step 3: 提交**

```bash
git add skill/client-tools-inspect/SKILL.md
git commit -m "feat(skill): add client-tools-inspect skill"
```

---

## Task 4：执行 install.sh 安装软链

**Files:**
- 无文件变更，仅执行脚本

- [ ] **Step 1: 查看当前 install.sh**

```bash
cat install.sh
```

确认 Task 1 中追加的 3 条软链命令已存在。

- [ ] **Step 2: 执行安装**

在项目根目录执行：

```bash
bash install.sh
```

期望输出包含：`[OK] client-tools skills linked`

- [ ] **Step 3: 验证软链**

```bash
ls -la ~/.claude/skills/ | grep client-tools
```

期望输出：3 条软链指向项目目录下的 skill 子目录：

```
client-tools-preprocess -> /path/to/client-tools/skill/client-tools-preprocess
client-tools-implement  -> /path/to/client-tools/skill/client-tools-implement
client-tools-inspect    -> /path/to/client-tools/skill/client-tools-inspect
```

- [ ] **Step 4: 验证 skill 可被 Claude Code 发现**

启动一个新的 Claude Code 会话，在 system-reminder 的 available skills 列表中确认出现以下 3 个 skill：

```
- client-tools:preprocess
- client-tools:implement
- client-tools:inspect
```

---

## 自检：Spec 覆盖

| Spec 要求 | 对应 Task |
|-----------|-----------|
| preprocess skill：引导式收集参数 | Task 1 |
| preprocess skill：--list-only 展示节点 | Task 1 |
| preprocess skill：用户选锚点后生成 JSON | Task 1 |
| implement skill：流程编排，读 design.json | Task 2 |
| implement skill：id 约束（所有 View 必须有 id）| Task 2 |
| implement skill：get_last_event 确认页面 | Task 2 |
| inspect skill：push_html 叠加，用户对齐锚点 | Task 3 |
| inspect skill：逐节点 dom_by_id + get_node 比对 | Task 3 |
| inspect skill：AI 推理调整策略 | Task 3 |
| inspect skill：最多 10 轮，连续 2 轮无改善终止 | Task 3 |
| inspect skill：全屏验收 dom_all | Task 3 |
| inspect skill：输出报告 | Task 3 |
| 软链安装到 ~/.claude/skills/ | Task 1（install.sh）+ Task 4 |
