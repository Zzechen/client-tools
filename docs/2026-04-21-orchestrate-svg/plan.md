# Orchestrator + SVG 自动转换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `client-tools-orchestrate` skill 管理设计稿生命周期（含跨会话恢复），并在 preprocess 阶段自动提取 HTML 中的 SVG 图标转换为 Android Vector Drawable。

**Architecture:** preprocess 脚本新增 `svg_extractor.py` 负责 SVG 识别、命名和转换，产物写入工作目录 `drawables/`；`models.py` 新增 `DRAWABLE` 类型和 `drawable` attrs 字段；orchestrate skill 是纯 Markdown 工作流文档，读写 `state.json` 驱动三个子 skill 自动推进。

**Tech Stack:** Python 3.13, `svg-to-android-vector` (PyPI), Playwright, pytest-asyncio；Markdown skill 文件

---

## 文件结构

### 新建
- `skill/preprocess/svg_extractor.py` — SVG 识别、命名推断、Vector Drawable 转换
- `skill/client-tools-orchestrate/SKILL.md` — Orchestrator skill 工作流文档
- `tests/preprocess/fixtures/icon_svg.html` — 含 SVG 图标的测试 fixture
- `tests/preprocess/test_svg_extractor.py` — svg_extractor 单元测试

### 修改
- `skill/preprocess/models.py` — 新增 `DRAWABLE` NodeType，`ImageAttrs` 新增 `drawable` 可选字段
- `skill/preprocess/extractor.py` — 集成 svg_extractor，节点提取时识别 SVG 节点
- `skill/preprocess/requirements.txt` — 新增 `svg-to-android-vector`
- `skill/client-tools-preprocess/SKILL.md` — 说明工作目录和 drawables 产出
- `skill/client-tools-implement/SKILL.md` — 新增目标路径收集、drawables 复制、DRAWABLE 节点处理说明

---

## Task 1: 新增 DRAWABLE 类型和 drawable attrs 字段

**Files:**
- Modify: `skill/preprocess/models.py`

- [ ] **Step 1: 修改 models.py**

将文件改为：

```python
from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import Optional, Union
import json


class NodeType(str, Enum):
    TEXT = "TEXT"
    IMAGE = "IMAGE"
    LIST = "LIST"
    CONTAINER = "CONTAINER"
    DRAWABLE = "DRAWABLE"


@dataclass
class TextAttrs:
    fontSize: float
    color: str
    fontWeight: str


@dataclass
class ImageAttrs:
    scaleType: str = "fitCenter"
    drawable: Optional[str] = None


@dataclass
class ListAttrs:
    itemSpacing: float
    orientation: str  # "VERTICAL" | "HORIZONTAL"


@dataclass
class ContainerAttrs:
    paddingTop: float
    paddingBottom: float
    paddingLeft: float
    paddingRight: float


NodeAttrs = Union[TextAttrs, ImageAttrs, ListAttrs, ContainerAttrs]


@dataclass
class RelPos:
    dx: float
    dy: float


@dataclass
class Node:
    id: str
    type: NodeType
    screenX: float
    screenY: float
    widthDp: float
    heightDp: float
    rel: Optional[RelPos] = None
    attrs: Optional[NodeAttrs] = None


@dataclass
class AnchorRef:
    id: str
    edge: str  # "top" | "bottom"


@dataclass
class DesignDoc:
    viewport: int
    anchor: AnchorRef
    nodes: list[Node]


def _node_to_dict(node: Node) -> dict:
    d = {
        "id": node.id,
        "type": node.type.value,
        "screenX": node.screenX,
        "screenY": node.screenY,
        "widthDp": node.widthDp,
        "heightDp": node.heightDp,
    }
    if node.rel:
        d["rel"] = {"dx": node.rel.dx, "dy": node.rel.dy}
    if node.attrs:
        attrs_dict = asdict(node.attrs)
        # 过滤掉值为 None 的字段
        attrs_dict = {k: v for k, v in attrs_dict.items() if v is not None}
        d["attrs"] = attrs_dict
    return d


def design_doc_to_json(doc: DesignDoc) -> str:
    data = {
        "viewport": doc.viewport,
        "anchor": {"id": doc.anchor.id, "edge": doc.anchor.edge},
        "nodes": [_node_to_dict(n) for n in doc.nodes]
    }
    return json.dumps(data, ensure_ascii=False, indent=2)
```

- [ ] **Step 2: 运行现有测试确认不回归**

```bash
cd /path/to/project && skill/preprocess/.venv/bin/pytest tests/preprocess/ -q
```

Expected: 所有测试通过（PASSED）

- [ ] **Step 3: Commit**

```bash
git add skill/preprocess/models.py
git commit -m "feat(preprocess): add DRAWABLE NodeType and drawable field to ImageAttrs"
```

---

## Task 2: 创建测试 fixture（含 SVG 图标）

**Files:**
- Create: `tests/preprocess/fixtures/icon_svg.html`

- [ ] **Step 1: 创建 fixture**

```html
<!DOCTYPE html>
<html>
<head>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { width: 375px; }
.btn { width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; }
.label { font-size: 14px; color: #333; }
.bg-svg { width: 375px; height: 200px; display: block; }
</style>
</head>
<body>
  <button id="btn_close" class="btn" aria-label="close">
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
      <path d="M4 4l10 10M14 4L4 14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    </svg>
  </button>
  <button id="btn_wechat" class="btn" aria-label="wechat login">
    <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
      <circle cx="8" cy="8" r="5" stroke="currentColor"/>
      <circle cx="15" cy="14" r="4" stroke="currentColor"/>
    </svg>
  </button>
  <p id="text_label" class="label">登录</p>
  <svg class="bg-svg" viewBox="0 0 375 200">
    <rect width="375" height="200" fill="rgba(0,0,0,0.1)"/>
  </svg>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add tests/preprocess/fixtures/icon_svg.html
git commit -m "test(preprocess): add icon_svg fixture for SVG extraction tests"
```

---

## Task 3: 实现 svg_extractor.py

**Files:**
- Create: `skill/preprocess/svg_extractor.py`
- Modify: `skill/preprocess/requirements.txt`

- [ ] **Step 1: 安装依赖，确认可用**

```bash
skill/preprocess/.venv/bin/pip install svg-to-android-vector
```

Expected: Successfully installed svg-to-android-vector-x.x.x

- [ ] **Step 2: 更新 requirements.txt**

在 `skill/preprocess/requirements.txt` 末尾加一行：

```
svg-to-android-vector>=0.1.0
```

- [ ] **Step 3: 创建 svg_extractor.py**

```python
import re
from pathlib import Path
from svg_to_android_vector import convert


def infer_drawable_name(aria_label: str, parent_id: str, node_id: str) -> str:
    """按优先级推断 drawable 文件名（不含扩展名）。"""
    if aria_label:
        name = re.sub(r"[^a-z0-9]+", "_", aria_label.lower().strip()).strip("_")
        if name:
            return f"ic_{name}"

    if parent_id:
        # 去掉末尾数字段，取最后一个语义段
        # login_btn_wechat → wechat；btn_close → close
        parts = [p for p in parent_id.split("_") if not p.isdigit()]
        if parts:
            return f"ic_{parts[-1]}"

    return f"ic_{node_id}"


def svg_content_to_vector(svg_content: str, output_path: Path) -> bool:
    """将 SVG 字符串转换为 Android Vector Drawable XML，写入 output_path。
    返回 True 表示成功，False 表示转换失败（跳过）。
    """
    try:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        vector_xml = convert(svg_content)
        output_path.write_text(vector_xml, encoding="utf-8")
        return True
    except Exception:
        return False
```

- [ ] **Step 4: Commit**

```bash
git add skill/preprocess/svg_extractor.py skill/preprocess/requirements.txt
git commit -m "feat(preprocess): add svg_extractor module for SVG to Vector Drawable conversion"
```

---

## Task 4: 编写 svg_extractor 单元测试

**Files:**
- Create: `tests/preprocess/test_svg_extractor.py`

- [ ] **Step 1: 写失败测试**

```python
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "skill" / "preprocess"))

from svg_extractor import infer_drawable_name, svg_content_to_vector


def test_infer_name_from_aria_label():
    assert infer_drawable_name("wechat login", "btn_wechat", "container_1") == "ic_wechat_login"


def test_infer_name_from_parent_id():
    assert infer_drawable_name("", "login_btn_wechat", "container_1") == "ic_wechat"


def test_infer_name_fallback_node_id():
    assert infer_drawable_name("", "", "container_5") == "ic_container_5"


def test_infer_name_strips_digits():
    assert infer_drawable_name("", "btn_close_1", "container_2") == "ic_close"


def test_svg_to_vector_writes_file(tmp_path):
    svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path d="M4 4l16 16"/></svg>'
    out = tmp_path / "ic_test.xml"
    result = svg_content_to_vector(svg, out)
    assert result is True
    assert out.exists()
    content = out.read_text()
    assert "<vector" in content


def test_svg_to_vector_invalid_svg_returns_false(tmp_path):
    out = tmp_path / "ic_bad.xml"
    result = svg_content_to_vector("not svg content", out)
    assert result is False
```

- [ ] **Step 2: 运行确认失败**

```bash
skill/preprocess/.venv/bin/pytest tests/preprocess/test_svg_extractor.py -v
```

Expected: FAILED（ImportError 或 AssertionError）

- [ ] **Step 3: 运行确认通过（Task 3 已实现）**

```bash
skill/preprocess/.venv/bin/pytest tests/preprocess/test_svg_extractor.py -v
```

Expected: 5 passed

- [ ] **Step 4: Commit**

```bash
git add tests/preprocess/test_svg_extractor.py
git commit -m "test(preprocess): add unit tests for svg_extractor"
```

---

## Task 5: 集成 SVG 识别到 extractor.py

**Files:**
- Modify: `skill/preprocess/extractor.py`

- [ ] **Step 1: 写失败测试（在 test_extractor.py 末尾追加）**

在 `tests/preprocess/test_extractor.py` 末尾追加：

```python
ICON_FIXTURE = str(Path(__file__).parent / "fixtures/icon_svg.html")


@pytest.mark.asyncio
async def test_svg_icon_button_becomes_image_node():
    """只含 SVG 的按钮应被识别为 IMAGE 类型节点。"""
    from models import NodeType, ImageAttrs
    nodes = await extract_nodes(ICON_FIXTURE, viewport=375)
    image_nodes = [n for n in nodes if n.type == NodeType.IMAGE]
    assert len(image_nodes) >= 2  # btn_close, btn_wechat


@pytest.mark.asyncio
async def test_svg_icon_node_has_drawable_attr():
    """IMAGE 节点应有非空 drawable 字段。"""
    from models import NodeType, ImageAttrs
    nodes = await extract_nodes(ICON_FIXTURE, viewport=375)
    image_nodes = [n for n in nodes if n.type == NodeType.IMAGE]
    for n in image_nodes:
        assert isinstance(n.attrs, ImageAttrs)
        assert n.attrs.drawable is not None
        assert n.attrs.drawable.startswith("ic_")


@pytest.mark.asyncio
async def test_large_svg_becomes_drawable_node():
    """面积超过视口 50% 的 SVG 应为 DRAWABLE 类型。"""
    from models import NodeType
    nodes = await extract_nodes(ICON_FIXTURE, viewport=375)
    drawable_nodes = [n for n in nodes if n.type == NodeType.DRAWABLE]
    assert len(drawable_nodes) >= 1
```

- [ ] **Step 2: 运行确认失败**

```bash
skill/preprocess/.venv/bin/pytest tests/preprocess/test_extractor.py::test_svg_icon_button_becomes_image_node -v
```

Expected: FAILED

- [ ] **Step 3: 修改 extractor.py 集成 SVG 识别**

将 `skill/preprocess/extractor.py` 完整替换为：

```python
import asyncio
from pathlib import Path
from playwright.async_api import async_playwright
from models import (
    Node, NodeType, TextAttrs, ImageAttrs, ListAttrs, ContainerAttrs
)
from svg_extractor import infer_drawable_name

_TAG_TYPE_MAP = {
    "p": NodeType.TEXT, "span": NodeType.TEXT, "label": NodeType.TEXT,
    "h1": NodeType.TEXT, "h2": NodeType.TEXT, "h3": NodeType.TEXT,
    "h4": NodeType.TEXT, "h5": NodeType.TEXT, "h6": NodeType.TEXT,
    "img": NodeType.IMAGE,
    "ul": NodeType.LIST, "ol": NodeType.LIST,
}


def _get_node_type(tag: str) -> NodeType:
    return _TAG_TYPE_MAP.get(tag.lower(), NodeType.CONTAINER)


def _next_id(counters: dict, node_type: NodeType) -> str:
    key = node_type.value.lower()
    counters[key] = counters.get(key, 0) + 1
    return f"{key}_{counters[key]}"


async def _is_svg_only_element(page, selector: str) -> tuple[bool, str]:
    """检测元素是否只含单个 SVG 子节点（无文本、无其他可见子元素）。
    返回 (is_svg_only, svg_content)。
    """
    result = await page.evaluate(f"""() => {{
        const el = document.querySelector("{selector}");
        if (!el) return {{ isSvgOnly: false, svgContent: "" }};
        const children = Array.from(el.children);
        const visibleChildren = children.filter(c => {{
            const s = window.getComputedStyle(c);
            return s.display !== 'none' && s.visibility !== 'hidden';
        }});
        const textContent = (el.childNodes);
        let hasText = false;
        for (const node of el.childNodes) {{
            if (node.nodeType === 3 && node.textContent.trim()) {{
                hasText = true;
                break;
            }}
        }}
        if (hasText) return {{ isSvgOnly: false, svgContent: "" }};
        if (visibleChildren.length === 1 && visibleChildren[0].tagName.toLowerCase() === 'svg') {{
            return {{ isSvgOnly: true, svgContent: visibleChildren[0].outerHTML }};
        }}
        return {{ isSvgOnly: false, svgContent: "" }};
    }}""")
    return result["isSvgOnly"], result["svgContent"]


async def _get_element_info(page, selector: str) -> dict:
    """获取元素的 aria-label 和 id。"""
    return await page.evaluate(f"""() => {{
        const el = document.querySelector("{selector}");
        return {{
            ariaLabel: el ? (el.getAttribute('aria-label') || '') : '',
            elementId: el ? (el.id || '') : ''
        }};
    }}""")


async def _extract_attrs(page, selector: str, node_type: NodeType):
    if node_type == NodeType.TEXT:
        style = await page.evaluate(f"""() => {{
            const el = document.querySelector("{selector}");
            const s = window.getComputedStyle(el);
            return {{
                fontSize: parseFloat(s.fontSize),
                color: s.color,
                fontWeight: s.fontWeight
            }};
        }}""")
        return TextAttrs(
            fontSize=round(style["fontSize"], 1),
            color=style["color"],
            fontWeight=style["fontWeight"]
        )
    elif node_type in (NodeType.IMAGE, NodeType.DRAWABLE):
        return ImageAttrs(scaleType="fitCenter")
    elif node_type == NodeType.LIST:
        spacing = await page.evaluate(f"""() => {{
            const el = document.querySelector("{selector}");
            const items = el.querySelectorAll("li");
            if (items.length < 2) return 0;
            const r1 = items[0].getBoundingClientRect();
            const r2 = items[1].getBoundingClientRect();
            return r2.top - r1.bottom;
        }}""")
        return ListAttrs(itemSpacing=round(max(spacing, 0), 1), orientation="VERTICAL")
    else:
        style = await page.evaluate(f"""() => {{
            const el = document.querySelector("{selector}");
            const s = window.getComputedStyle(el);
            return {{
                paddingTop: parseFloat(s.paddingTop),
                paddingBottom: parseFloat(s.paddingBottom),
                paddingLeft: parseFloat(s.paddingLeft),
                paddingRight: parseFloat(s.paddingRight)
            }};
        }}""")
        return ContainerAttrs(
            paddingTop=round(style["paddingTop"], 1),
            paddingBottom=round(style["paddingBottom"], 1),
            paddingLeft=round(style["paddingLeft"], 1),
            paddingRight=round(style["paddingRight"], 1)
        )


async def extract_nodes(html_path: str, viewport: int,
                        drawables_dir: Path | None = None) -> list[Node]:
    """提取页面节点。若提供 drawables_dir，将 SVG 节点转换并写入该目录。"""
    counters: dict = {}
    nodes: list[Node] = []

    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page(viewport={"width": viewport, "height": 812})
        await page.goto(f"file://{Path(html_path).resolve()}")
        await page.wait_for_load_state("networkidle")
        await asyncio.sleep(5)

        viewport_area = viewport * 812
        elements = await page.query_selector_all("*")

        for el in elements:
            tag = await el.evaluate("el => el.tagName.toLowerCase()")
            if tag in ("html", "head", "body", "style", "script", "meta", "link", "svg", "defs"):
                continue

            rect = await el.bounding_box()
            if not rect or rect["width"] == 0 or rect["height"] == 0:
                continue

            visible = await el.evaluate("""el => {
                const s = window.getComputedStyle(el);
                return s.display !== 'none' && s.visibility !== 'hidden';
            }""")
            if not visible:
                continue

            node_type = _get_node_type(tag)
            node_id = _next_id(counters, node_type)

            await el.evaluate(f"el => el.setAttribute('data-ct-id', '{node_id}')")
            selector = f"[data-ct-id='{node_id}']"

            # SVG 识别：检测是否只含单个 SVG 子节点
            svg_drawable: str | None = None
            is_svg_only, svg_content = await _is_svg_only_element(page, selector)
            if is_svg_only and svg_content:
                elem_info = await _get_element_info(page, selector)
                drawable_name = infer_drawable_name(
                    elem_info["ariaLabel"], elem_info["elementId"], node_id
                )
                node_type = NodeType.IMAGE
                svg_drawable = drawable_name

                if drawables_dir:
                    from svg_extractor import svg_content_to_vector
                    svg_content_to_vector(svg_content, drawables_dir / f"{drawable_name}.xml")
            elif tag == "svg":
                # 直接 SVG 元素：检测是否为装饰背景（面积超过视口 50%）
                area = rect["width"] * rect["height"]
                if area > viewport_area * 0.5:
                    elem_info = await _get_element_info(page, selector)
                    drawable_name = infer_drawable_name(
                        elem_info["ariaLabel"], elem_info["elementId"], node_id
                    )
                    node_type = NodeType.DRAWABLE
                    svg_drawable = drawable_name

                    if drawables_dir:
                        svg_raw = await el.evaluate("el => el.outerHTML")
                        from svg_extractor import svg_content_to_vector
                        svg_content_to_vector(svg_raw, drawables_dir / f"{drawable_name}.xml")

            attrs = await _extract_attrs(page, selector, node_type)
            if svg_drawable and isinstance(attrs, ImageAttrs):
                attrs.drawable = svg_drawable

            nodes.append(Node(
                id=node_id,
                type=node_type,
                screenX=round(rect["x"], 1),
                screenY=round(rect["y"], 1),
                widthDp=round(rect["width"], 1),
                heightDp=round(rect["height"], 1),
                attrs=attrs
            ))

        await browser.close()

    return nodes
```

- [ ] **Step 4: 运行测试确认通过**

```bash
skill/preprocess/.venv/bin/pytest tests/preprocess/test_extractor.py -v
```

Expected: 所有测试 PASSED（包括 3 个新增测试）

- [ ] **Step 5: Commit**

```bash
git add skill/preprocess/extractor.py
git commit -m "feat(preprocess): integrate SVG detection and extraction into node extractor"
```

---

## Task 6: 修改 preprocess.py 支持工作目录

**Files:**
- Modify: `skill/preprocess/preprocess.py`

preprocess.py 需要接受 `--drawables-dir` 参数，将其传入 `extract_nodes`。

- [ ] **Step 1: 修改 preprocess.py**

```python
#!/usr/bin/env python3
import argparse
import asyncio
import json
import sys
from pathlib import Path

from extractor import extract_nodes
from anchor import apply_anchor
from models import AnchorRef, DesignDoc, design_doc_to_json


def parse_args():
    parser = argparse.ArgumentParser(description="设计稿预处理工具")
    parser.add_argument("--input", required=True, help="HTML 设计稿文件路径")
    parser.add_argument("--viewport", type=int, required=True, help="设计稿宽度（px）")
    parser.add_argument("--anchor-id", help="锚点节点 id")
    parser.add_argument("--anchor-edge", default="top", choices=["top", "bottom"], help="锚点基准边缘")
    parser.add_argument("--output", help="输出 JSON 文件路径，默认与 input 同目录同名 .json")
    parser.add_argument("--drawables-dir", help="Vector Drawable 输出目录，不指定则不提取 SVG")
    parser.add_argument("--list-only", action="store_true", help="仅输出节点列表，不生成 JSON 文件")
    return parser.parse_args()


async def main():
    args = parse_args()

    drawables_dir = Path(args.drawables_dir) if args.drawables_dir else None
    nodes = await extract_nodes(args.input, args.viewport, drawables_dir=drawables_dir)

    node_list = [
        {"id": n.id, "type": n.type.value,
         "screenX": n.screenX, "screenY": n.screenY,
         "widthDp": n.widthDp, "heightDp": n.heightDp}
        for n in nodes
    ]

    if args.list_only:
        print(json.dumps(node_list, ensure_ascii=False, indent=2))
        return

    if not args.anchor_id:
        print(json.dumps(node_list, ensure_ascii=False, indent=2))
        print("\n[ERROR] 请通过 --anchor-id 指定锚点节点 id", file=sys.stderr)
        sys.exit(1)

    nodes = apply_anchor(nodes, args.anchor_id, args.anchor_edge)

    doc = DesignDoc(
        viewport=args.viewport,
        anchor=AnchorRef(id=args.anchor_id, edge=args.anchor_edge),
        nodes=nodes
    )

    output_path = args.output or str(Path(args.input).with_suffix(".json"))
    Path(output_path).write_text(design_doc_to_json(doc), encoding="utf-8")
    print(f"[OK] 输出至 {output_path}，共 {len(nodes)} 个节点")
    if drawables_dir:
        xml_files = list(drawables_dir.glob("*.xml"))
        print(f"[OK] drawables 输出至 {drawables_dir}，共 {len(xml_files)} 个文件")


if __name__ == "__main__":
    asyncio.run(main())
```

- [ ] **Step 2: 运行全量测试确认不回归**

```bash
skill/preprocess/.venv/bin/pytest tests/preprocess/ -q
```

Expected: 全部通过

- [ ] **Step 3: Commit**

```bash
git add skill/preprocess/preprocess.py
git commit -m "feat(preprocess): add --drawables-dir argument for SVG extraction output"
```

---

## Task 7: 创建 client-tools-orchestrate skill

**Files:**
- Create: `skill/client-tools-orchestrate/SKILL.md`

- [ ] **Step 1: 创建目录和 SKILL.md**

```markdown
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

## 启动流程

### Step 1: 扫描未完成工作目录

扫描 `./design/*/state.json`，收集所有 `phase != "done"` 的条目。

**有未完成项时**，以表格列出：

```
编号  目录名                    当前阶段      最后更新
1     20260421-login-phone      implement     2026-04-21 10:00
```

询问用户：选择恢复哪个（输入编号），或输入 `n` 新建。

**无未完成项时**，直接进入新建流程。

### Step 2: 新建流程

依次询问（每次只问一个问题）：
1. bizname（英文，用于目录名，如 `login-phone`）
2. HTML 设计稿的完整路径

然后：
1. 创建目录 `design/<yyyymmddHHMM>-<bizname>/`
2. 将 HTML 文件复制到目录内，命名为 `design.html`
3. 写入初始 `state.json`（phase = "preprocess"，其余字段待填）
4. 自动进入 preprocess 阶段（见下方）

### Step 3: 根据 phase 分发

读取工作目录下的 `state.json`，按 phase 执行：

#### phase = "preprocess"

调用 `client-tools:preprocess` skill，但使用工作目录模式：

- HTML 路径：`<workdir>/design.html`
- 输出 JSON：`<workdir>/design.json`
- drawables 目录：`<workdir>/drawables/`
- 脚本命令示例：
  ```bash
  skill/preprocess/.venv/bin/python skill/preprocess/preprocess.py \
    --input <workdir>/design.html \
    --viewport <viewport> \
    --anchor-id <anchor_id> \
    --anchor-edge <anchor_edge> \
    --output <workdir>/design.json \
    --drawables-dir <workdir>/drawables/
  ```

preprocess 完成后：
- 更新 `state.json`：填入 `viewport`、`anchor`，phase 改为 `"implement"`，history 追加记录
- 自动推进到 implement 阶段

#### phase = "implement"

若 `state.json.target` 为空，先收集：
1. 目标 Android module 根路径（如 `../my-app/app`，需包含 `src/main/res/` 目录）
2. 布局文件名（如 `activity_login`，不含 `.xml`）

将收集到的信息写入 `state.json.target`。

然后：
1. 将 `<workdir>/drawables/*.xml` 复制到 `<target.module>/src/main/res/drawable/`
2. 调用 `client-tools:implement` skill，design.json 路径为 `<workdir>/design.json`

implement 完成后：
- 更新 `state.json`：phase 改为 `"inspect"`，history 追加记录
- 自动推进到 inspect 阶段

#### phase = "inspect"

调用 `client-tools:inspect` skill。

inspect 完成后：
- 更新 `state.json`：phase 改为 `"done"`，history 追加记录
- 提示用户全流程完成

#### phase = "done"

告知用户该设计稿已完成全流程，询问是否开始新的设计稿。

## state.json 更新规范

每次更新 state.json 必须：
1. 读取现有内容（避免覆盖其他字段）
2. 仅修改目标字段
3. history 数组追加，不覆盖
4. completedAt 使用 ISO 8601 格式（UTC）

## 注意事项

- preprocess skill 的锚点询问步骤由 orchestrator 接管，不再重复询问
- 工作目录路径在整个会话中保持不变，始终从 state.json 读取
- 若 drawables/ 目录为空（无 SVG），implement 阶段跳过复制步骤
```

- [ ] **Step 2: Commit**

```bash
git add skill/client-tools-orchestrate/SKILL.md
git commit -m "feat(skill): add client-tools-orchestrate skill for lifecycle management"
```

---

## Task 8: 更新子 skill 文档

**Files:**
- Modify: `skill/client-tools-preprocess/SKILL.md`
- Modify: `skill/client-tools-implement/SKILL.md`

- [ ] **Step 1: 更新 preprocess SKILL.md**

在 `skill/client-tools-preprocess/SKILL.md` 的"工作流程"末尾（Step 5 确认完成之前）新增说明段：

```markdown
### 工作目录模式（由 orchestrator 调用时）

当由 `client-tools:orchestrate` 调用时，preprocess 使用工作目录模式：
- HTML 已复制到工作目录，路径由 orchestrator 提供
- `--output` 指向 `<workdir>/design.json`
- `--drawables-dir` 指向 `<workdir>/drawables/`，自动提取 SVG 并转换为 Vector Drawable
- 完成后 orchestrator 负责更新 `state.json` 并推进到 implement 阶段
```

- [ ] **Step 2: 更新 implement SKILL.md**

在 `skill/client-tools-implement/SKILL.md` 的 Step 2（生成代码）之前新增 Step 1.5：

```markdown
### Step 1.5：处理 drawables（仅 orchestrator 模式）

若工作目录中 `drawables/` 目录存在且非空：
1. 确认目标 module 路径已在 `state.json.target.module` 中
2. 将 `drawables/*.xml` 复制到 `<target.module>/src/main/res/drawable/`

design.json 中 type 为 `IMAGE` 且 `attrs.drawable` 非空的节点，生成 ImageView 时使用：
```xml
android:src="@drawable/<attrs.drawable>"
```

type 为 `DRAWABLE` 的节点不生成 View，在生成背景容器时按需引用 `@drawable/<attrs.drawable>`。
```

- [ ] **Step 3: Commit**

```bash
git add skill/client-tools-preprocess/SKILL.md skill/client-tools-implement/SKILL.md
git commit -m "docs(skill): update preprocess and implement skills for orchestrator mode"
```

---

## Task 9: 全量回归测试

- [ ] **Step 1: 运行全部测试**

```bash
skill/preprocess/.venv/bin/pytest tests/preprocess/ -v
```

Expected: 全部 PASSED，无跳过

- [ ] **Step 2: 手动冒烟测试（可选）**

```bash
skill/preprocess/.venv/bin/python skill/preprocess/preprocess.py \
  --input docs/examples/login-phone.html \
  --viewport 375 \
  --list-only
```

观察输出中是否有 `type: IMAGE` 的节点（对应 SVG 图标按钮）。

- [ ] **Step 3: Commit（若有修复）**

```bash
git add -p
git commit -m "fix(preprocess): regression fixes from full test run"
```
