# 模块 1：设计稿预处理工具 — Spec

> 创建时间：2026-04-17

---

## 目标

将 HTML/CSS 设计稿渲染后提取所有节点的结构化信息，以锚点为基准输出相对坐标 JSON，供 AI 编码实现时使用。

---

## 使用方式

脚本位于 `skill/preprocess/` 目录，由 AI 在需要预处理时调用。

```bash
python preprocess.py \
  --input design.html \
  --viewport 375 \
  --anchor-id text_title \
  --anchor-edge top \
  --output design.json
```

**参数说明：**

| 参数 | 必填 | 说明 |
|------|------|------|
| `--input` | ✅ | HTML/CSS 设计稿文件路径 |
| `--viewport` | ✅ | 设计稿宽度（px），如 `375`、`390`；设备屏幕 dp 宽度通过 adb 强制设置为相同值 |
| `--anchor-id` | ❌ | 锚点节点 id，未指定时脚本输出节点列表后暂停等待 AI 指定 |
| `--anchor-edge` | ❌ | 锚点基准边缘，`top`（默认）或 `bottom` |
| `--output` | ❌ | 输出文件路径，默认与 `--input` 同目录同名 `.json`；建议指定到对应页面的 spec 目录下 |

---

## 锚点选择流程

```
AI 调用脚本前检查是否已知锚点
        ↓
  已知？ → 直接带 --anchor-id 参数运行
        ↓ 否
  询问用户：「请描述你想作为锚点的元素，如『页面顶部的标题文字』」
        ↓
  脚本先跑 --list-only 模式，输出节点列表
        ↓
  AI 根据用户描述从列表中匹配最合适的节点 id
        ↓
  确认后带 --anchor-id 参数重新运行
```

`--list-only` 模式只输出节点列表（stdout），不计算相对坐标，不生成 JSON 文件：

```json
[
  { "id": "text_1", "type": "TEXT", "screenX": 16.0, "screenY": 48.0, "widthDp": 200.0, "heightDp": 24.0 },
  { "id": "img_1",  "type": "IMAGE", "screenX": 16.0, "screenY": 80.0, "widthDp": 40.0, "heightDp": 40.0 }
]
```

---

## 处理流程

1. 用 Playwright（无头 Chromium）按 `--viewport` 宽度加载并渲染 HTML，高度自适应内容
2. 遍历所有 DOM 节点，按节点标签自动生成唯一语义化 id：
   - 规则：`<type>_<序号>`，如 `text_1`、`img_2`、`list_1`
   - 同类型节点按从上到下、从左到右顺序编号
   - 跳过不可见节点（`display:none`、`visibility:hidden`、尺寸为 0）
3. 调用 `getBoundingClientRect()` 获取每个节点渲染后的位置和尺寸，单位即为 dp（viewport px = 设备 dp）
4. 确定锚点基准 y 值：
   - `edge=top`：锚点节点的 `screenY`
   - `edge=bottom`：锚点节点的 `screenY + heightDp`
6. 计算所有节点相对锚点的偏移：
   - `dx = node.screenX - anchor.screenX`
   - `dy = node.screenY - anchorEdgeY`
7. 提取节点样式属性（按节点类型）
8. 输出结构化 JSON

---

## 节点类型映射

| DOM 标签 | type | attrs |
|---------|------|-------|
| `<p>`, `<span>`, `<h1>`~`<h6>`, `<label>` | `TEXT` | `fontSize`, `color`, `fontWeight` |
| `<img>` | `IMAGE` | `scaleType`（默认 `fitCenter`）|
| `<ul>`, `<ol>` | `LIST` | `itemSpacing`, `orientation` |
| 其他（`<div>`, `<section>` 等）| `CONTAINER` | `paddingTop`, `paddingBottom`, `paddingLeft`, `paddingRight` |

---

## 输出 JSON 格式

```json
{
  "viewport": 375,
  "anchor": {
    "id": "text_1",
    "edge": "top"
  },
  "nodes": [
    {
      "id": "text_1",
      "type": "TEXT",
      "screenX": 16.0,
      "screenY": 48.0,
      "widthDp": 200.0,
      "heightDp": 24.0,
      "rel": {
        "dx": 0.0,
        "dy": 0.0
      },
      "attrs": {
        "fontSize": 16.0,
        "color": "#333333",
        "fontWeight": "bold"
      }
    },
    {
      "id": "img_1",
      "type": "IMAGE",
      "screenX": 16.0,
      "screenY": 80.0,
      "widthDp": 40.0,
      "heightDp": 40.0,
      "rel": {
        "dx": 0.0,
        "dy": 32.0
      },
      "attrs": {
        "scaleType": "fitCenter"
      }
    },
    {
      "id": "container_1",
      "type": "CONTAINER",
      "screenX": 0.0,
      "screenY": 0.0,
      "widthDp": 360.0,
      "heightDp": 48.0,
      "rel": {
        "dx": -16.0,
        "dy": -48.0
      },
      "attrs": {
        "paddingTop": 8.0,
        "paddingBottom": 8.0,
        "paddingLeft": 16.0,
        "paddingRight": 16.0
      }
    }
  ]
}
```

---

## 输出文件约定

输出 JSON 属于特定页面/需求的产物，应存放在对应的 spec 目录下：

```
docs/
  2026-04-17-login-page/
    spec.md        ← 该页面实现 spec
    design.html    ← 设计稿原文件
    design.json    ← 预处理输出（--output 指向此处）
```

AI 调用脚本时示例：

```bash
python preprocess.py \
  --input docs/2026-04-17-login-page/design.html \
  --viewport 375 \
  --anchor-id text_1 \
  --output docs/2026-04-17-login-page/design.json
```

---

## 依赖

```
playwright>=1.40.0
beautifulsoup4>=4.12.0
```

首次使用需安装 Chromium（约 100MB，一次性）：

```bash
pip install playwright
playwright install chromium
```

---

## 约束与边界

- 仅支持静态 HTML/CSS，不执行 JavaScript 动态渲染
- 不可见节点（`display:none`、尺寸为 0）不输出
- 所有坐标和尺寸保留一位小数（dp）
