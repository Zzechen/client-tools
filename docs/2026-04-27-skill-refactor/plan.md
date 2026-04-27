# Skill 瘦身 & 接入文档 — 实现计划

> 基于 spec.md，2026-04-27

---

## Step 1：迁移 preprocess 脚本

**目标：** 将脚本从 `skill/client-tools-preprocess/scripts/` 迁移到 `mcp/scripts/preprocess/`

- [ ] 在 `mcp/` 下创建 `scripts/preprocess/` 目录
- [ ] 将以下文件移入（保持原文件名）：
  - `preprocess.py`
  - `extractor.py`
  - `anchor.py`
  - `models.py`
  - `svg_extractor.py`
  - `requirements.txt`
  - `schema/`（整个目录）
- [ ] 在 `mcp/scripts/preprocess/` 重建 `.venv`：
  ```bash
  cd mcp/scripts/preprocess && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
  ```
- [ ] 在 `mcp/.gitignore`（或根 `.gitignore`）中加入 `mcp/scripts/preprocess/.venv/`
- [ ] 修改 `preprocess.py`：移除 `--anchor-id` 和 `--anchor-edge` 参数，脚本直接输出所有节点绝对坐标，不做锚点相对偏移计算；输出路径默认与 HTML 同目录同名 `.json`

---

## Step 2：新增 MCP 工具 `extract_view_layout`

**目标：** 在现有 MCP 工程中注册工具，调用迁移后的脚本

- [ ] 新建 `mcp/src/tools/design.ts`，实现 `extract_view_layout` 工具：
  - 参数：`file`（string，必填）、`viewport`（number，必填）
  - 用 `child_process.execFile` 调用 `.venv/bin/python preprocess.py --input <file> --viewport <viewport>`
  - 捕获 stdout JSON，解析后返回 `{ outputPath, nodeCount, nodes }`
  - 错误处理：脚本不存在、`.venv` 未初始化、HTML 文件不存在均返回可读错误信息
- [ ] 在 `mcp/src/index.ts` 中 import 并注册 `registerDesignTools`
- [ ] 构建验证：`npm run build`，确认无 TypeScript 错误

---

## Step 3：删除三个 skill

**目标：** 移除 preprocess、implement、orchestrate 三个 skill 目录

- [ ] 删除 `skill/client-tools-preprocess/`（脚本已迁移）
- [ ] 删除 `skill/client-tools-implement/`
- [ ] 删除 `skill/client-tools-orchestrate/`
- [ ] 保留 `skill/client-tools-inspect/`（不动）

---

## Step 4：更新 CLAUDE.md

**目标：** 补充 View 标识约束，更新目录结构描述

- [ ] 在「技术约定」章节新增 View 标识约束：
  ```
  - **View 标识**：
    - Android：每个 View（含中间容器层）必须设置 `android:id`，
      这是 `get_node`/`get_all_nodes` 工具的硬性前提
    - iOS：每个 View 必须设置 `accessibilityIdentifier`，
      命名规则：页面前缀 + 语义名，如 `login_text_title`
  ```
- [ ] 更新目录结构中 `skill/` 条目描述，改为「仅含 `client-tools-inspect`」
- [ ] 更新「运行测试」章节，移除 preprocess 脚本的旧路径引用，改为新路径
  ```bash
  # Python（preprocess）
  mcp/scripts/preprocess/.venv/bin/pytest tests/preprocess/ -q
  ```

---

## Step 5：新增接入文档 `docs/integration.md`

**目标：** 写一份机器友好、供 Claude Code 读取后指导工程师配置的接入文档

- [ ] 新建 `docs/integration.md`，包含以下章节：

### 章节结构

**一、工程依赖**

Android（`clients/android/` 工程参考）：
- `build.gradle.kts` 添加 SDK `.aar` 依赖（Maven：`TODO: 待发布后填入`）
- protobuf-kotlin 依赖及 Gradle plugin
- `minSdk = 26`

iOS（`clients/ios/` 工程参考）：
- `Podfile` 添加 SDK pod（`TODO: 待发布后填入`）
- SwiftProtobuf 依赖
- `platform :ios, '14.0'`

**二、MCP 安装**

前置：Node.js 18+、adb、Python 3.11+

```bash
# 安装依赖并构建
cd <repo>/mcp && npm install && npm run build

# 初始化 preprocess 脚本环境（extract_view_layout 工具依赖）
cd <repo>/mcp/scripts/preprocess
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt

# adb forward（每次连接设备后执行）
adb forward tcp:8080 tcp:8080
```

Claude Code MCP 配置（`.claude/settings.json` 或 Claude Desktop config）：
```json
{
  "mcpServers": {
    "client-tools": {
      "command": "node",
      "args": ["<repo>/mcp/dist/index.js"]
    }
  }
}
```

**三、Skill 安装**

当前只需安装 `client-tools-inspect`：

```bash
cp -r <repo>/skill/client-tools-inspect ~/.claude/skills/
```

验证：在 Claude Code 中输入 `/client-tools-inspect`，确认 skill 加载成功。

**四、View 标识约束**

- Android：每个 View 必须设置 `android:id`（包括容器层），缺失则 `get_node` 无法定位
- iOS：每个 View 必须设置 `accessibilityIdentifier`
- 命名规则：`<页面前缀>_<语义名>`，如 `login_text_title`、`login_btn_submit`

---

## Step 6：提交

- [ ] `git add` 所有变更
- [ ] Commit：`refactor: slim down skills, add extract_view_layout MCP tool and integration docs`
- [ ] Push

---

## 验收标准

- [ ] `extract_view_layout` 工具可在 Claude Code 中正常调用，传入 HTML 路径和 viewport 返回节点列表
- [ ] MCP 构建无报错
- [ ] `client-tools-inspect` skill 可正常触发
- [ ] `docs/integration.md` 覆盖工程依赖、MCP 安装、skill 安装、View 标识约束四个章节
- [ ] CLAUDE.md 中包含 View 标识约束说明
