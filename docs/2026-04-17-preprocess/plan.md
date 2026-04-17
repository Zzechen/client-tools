# 设计稿预处理工具 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 `skill/preprocess/preprocess.py`，将 HTML/CSS 设计稿渲染后提取所有节点结构化信息，以锚点为基准输出相对坐标 JSON。

**Architecture:** 脚本分为四层：CLI 参数解析 → Playwright 渲染与节点提取 → 锚点计算与相对坐标生成 → JSON 序列化输出。`--list-only` 模式在节点提取后直接输出列表并退出，不进入后续阶段。

**Tech Stack:** Python 3.10+、Playwright（无头 Chromium）、argparse、json

---

## 文件结构

```
skill/preprocess/
  preprocess.py        # 主脚本（CLI 入口 + 流程编排）
  extractor.py         # Playwright 渲染 + 节点提取
  anchor.py            # 锚点计算 + 相对坐标生成
  models.py            # 数据结构定义（Node, NodeType, Attrs）
  tests/
    test_extractor.py  # 节点提取单元测试
    test_anchor.py     # 锚点计算单元测试
    test_cli.py        # CLI 集成测试
    fixtures/
      simple.html      # 测试用设计稿
```

---

### Task 1: 项目初始化与依赖配置

**Files:**
- Create: `skill/preprocess/requirements.txt`
- Create: `skill/preprocess/tests/fixtures/simple.html`

- [ ] **Step 1: 创建目录结构**

```bash
mkdir -p skill/preprocess/tests/fixtures
```

- [ ] **Step 2: 创建 requirements.txt**

```
playwright>=1.40.0
pytest>=7.0.0
pytest-asyncio>=0.21.0
```

- [ ] **Step 3: 创建测试用 HTML fixture**

写入 `skill/preprocess/tests/fixtures/simple.html`：

```html
<!DOCTYPE html>
<html>
<head>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { width: 375px; }
.header { width: 375px; height: 48px; padding: 12px 16px; background: #fff; }
.title { font-size: 16px; color: #333333; font-weight: bold; }
.avatar { width: 40px; height: 40px; margin: 16px; }
.list { margin: 0 16px; }
.list li { height: 48px; border-bottom: 1px solid #eee; }
</style>
</head>
<body>
  <div class="header">
    <p class="title">页面标题</p>
  </div>
  <img class="avatar" src="" alt="avatar" />
  <ul class="list">
    <li>Item 1</li>
    <li>Item 2</li>
  </ul>
</body>
</html>
```

- [ ] **Step 4: 安装依赖**

```bash
cd skill/preprocess
pip install -r requirements.txt
playwright install chromium
```

Expected: 安装成功，无报错

- [ ] **Step 5: Commit**

```bash
git add skill/preprocess/requirements.txt skill/preprocess/tests/fixtures/simple.html
git commit -m "feat: init preprocess tool structure"
```

---

### Task 2: 数据模型定义

**Files:**
- Create: `skill/preprocess/models.py`

- [ ] **Step 1: 创建 models.py**

```python
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional, Union


class NodeType(str, Enum):
    TEXT = "TEXT"
    IMAGE = "IMAGE"
    LIST = "LIST"
    CONTAINER = "CONTAINER"


@dataclass
class TextAttrs:
    fontSize: float
    color: str
    fontWeight: str


@dataclass
class ImageAttrs:
    scaleType: str = "fitCenter"


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
```

- [ ] **Step 2: Commit**

```bash
git add skill/preprocess/models.py
git commit -m "feat: add preprocess data models"
```

---

### Task 3: Playwright 节点提取

**Files:**
- Create: `skill/preprocess/extractor.py`
- Create: `skill/preprocess/tests/test_extractor.py`

- [ ] **Step 1: 写失败测试**

写入 `skill/preprocess/tests/test_extractor.py`：

```python
import pytest
from pathlib import Path
from extractor import extract_nodes

FIXTURE = str(Path(__file__).parent / "fixtures/simple.html")


@pytest.mark.asyncio
async def test_extract_returns_nodes():
    nodes = await extract_nodes(FIXTURE, viewport=375)
    assert len(nodes) > 0


@pytest.mark.asyncio
async def test_extract_node_has_required_fields():
    nodes = await extract_nodes(FIXTURE, viewport=375)
    node = nodes[0]
    assert node.id
    assert node.type
    assert node.screenX >= 0
    assert node.screenY >= 0
    assert node.widthDp > 0
    assert node.heightDp > 0


@pytest.mark.asyncio
async def test_extract_skips_invisible_nodes():
    nodes = await extract_nodes(FIXTURE, viewport=375)
    ids = [n.id for n in nodes]
    # img src 为空，宽高为 0 时应被过滤；此 fixture 中 avatar 有尺寸所以应存在
    assert all(n.widthDp > 0 and n.heightDp > 0 for n in nodes)


@pytest.mark.asyncio
async def test_extract_text_node_has_attrs():
    nodes = await extract_nodes(FIXTURE, viewport=375)
    text_nodes = [n for n in nodes if n.type.value == "TEXT"]
    assert len(text_nodes) > 0
    from models import TextAttrs
    assert isinstance(text_nodes[0].attrs, TextAttrs)
    assert text_nodes[0].attrs.fontSize > 0


@pytest.mark.asyncio
async def test_extract_ids_are_unique():
    nodes = await extract_nodes(FIXTURE, viewport=375)
    ids = [n.id for n in nodes]
    assert len(ids) == len(set(ids))


@pytest.mark.asyncio
async def test_extract_id_naming_convention():
    nodes = await extract_nodes(FIXTURE, viewport=375)
    for node in nodes:
        # id 格式应为 type_序号，如 text_1, img_1
        parts = node.id.rsplit("_", 1)
        assert len(parts) == 2
        assert parts[1].isdigit()
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd skill/preprocess
pytest tests/test_extractor.py -v
```

Expected: FAIL with `ModuleNotFoundError: No module named 'extractor'`

- [ ] **Step 3: 实现 extractor.py**

```python
import asyncio
from dataclasses import dataclass
from pathlib import Path
from playwright.async_api import async_playwright
from models import (
    Node, NodeType, TextAttrs, ImageAttrs, ListAttrs, ContainerAttrs
)

# DOM 标签 → NodeType 映射
_TAG_TYPE_MAP = {
    "p": NodeType.TEXT, "span": NodeType.TEXT, "label": NodeType.TEXT,
    "h1": NodeType.TEXT, "h2": NodeType.TEXT, "h3": NodeType.TEXT,
    "h4": NodeType.TEXT, "h5": NodeType.TEXT, "h6": NodeType.TEXT,
    "img": NodeType.IMAGE,
    "ul": NodeType.LIST, "ol": NodeType.LIST,
}

_TYPE_COUNTERS: dict[str, int] = {}


def _next_id(node_type: NodeType) -> str:
    key = node_type.value.lower()
    _TYPE_COUNTERS[key] = _TYPE_COUNTERS.get(key, 0) + 1
    return f"{key}_{_TYPE_COUNTERS[key]}"


def _get_node_type(tag: str) -> NodeType:
    return _TAG_TYPE_MAP.get(tag.lower(), NodeType.CONTAINER)


async def _extract_attrs(page, selector: str, node_type: NodeType):
    if node_type == NodeType.TEXT:
        style = await page.evaluate(f"""
            () => {{
                const el = document.querySelector('{selector}');
                const s = window.getComputedStyle(el);
                return {{
                    fontSize: parseFloat(s.fontSize),
                    color: s.color,
                    fontWeight: s.fontWeight
                }};
            }}
        """)
        return TextAttrs(
            fontSize=round(style["fontSize"], 1),
            color=style["color"],
            fontWeight=style["fontWeight"]
        )
    elif node_type == NodeType.IMAGE:
        return ImageAttrs(scaleType="fitCenter")
    elif node_type == NodeType.LIST:
        spacing = await page.evaluate(f"""
            () => {{
                const el = document.querySelector('{selector}');
                const items = el.querySelectorAll('li');
                if (items.length < 2) return 0;
                const r1 = items[0].getBoundingClientRect();
                const r2 = items[1].getBoundingClientRect();
                return r2.top - r1.bottom;
            }}
        """)
        return ListAttrs(itemSpacing=round(max(spacing, 0), 1), orientation="VERTICAL")
    else:
        style = await page.evaluate(f"""
            () => {{
                const el = document.querySelector('{selector}');
                const s = window.getComputedStyle(el);
                return {{
                    paddingTop: parseFloat(s.paddingTop),
                    paddingBottom: parseFloat(s.paddingBottom),
                    paddingLeft: parseFloat(s.paddingLeft),
                    paddingRight: parseFloat(s.paddingRight)
                }};
            }}
        """)
        return ContainerAttrs(
            paddingTop=round(style["paddingTop"], 1),
            paddingBottom=round(style["paddingBottom"], 1),
            paddingLeft=round(style["paddingLeft"], 1),
            paddingRight=round(style["paddingRight"], 1)
        )


async def extract_nodes(html_path: str, viewport: int) -> list[Node]:
    _TYPE_COUNTERS.clear()
    nodes: list[Node] = []

    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page(viewport={"width": viewport, "height": 10000})
        await page.goto(f"file://{Path(html_path).resolve()}")
        await page.wait_for_load_state("networkidle")

        elements = await page.query_selector_all("*")
        seen_selectors = set()

        for el in elements:
            tag = await el.evaluate("el => el.tagName.toLowerCase()")
            if tag in ("html", "head", "body", "style", "script", "meta", "link"):
                continue

            rect = await el.bounding_box()
            if not rect or rect["width"] == 0 or rect["height"] == 0:
                continue

            # 检查可见性
            visible = await el.evaluate("""el => {
                const s = window.getComputedStyle(el);
                return s.display !== 'none' && s.visibility !== 'hidden';
            }""")
            if not visible:
                continue

            node_type = _get_node_type(tag)
            node_id = _next_id(node_type)

            # 注入 id 到 DOM 用于后续 attrs 查询
            await el.evaluate(f"el => el.setAttribute('data-ct-id', '{node_id}')")
            selector = f"[data-ct-id='{node_id}']"

            attrs = await _extract_attrs(page, selector, node_type)

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
cd skill/preprocess
pytest tests/test_extractor.py -v
```

Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add skill/preprocess/extractor.py skill/preprocess/tests/test_extractor.py
git commit -m "feat: implement node extractor with playwright"
```

---

### Task 4: 锚点计算与相对坐标

**Files:**
- Create: `skill/preprocess/anchor.py`
- Create: `skill/preprocess/tests/test_anchor.py`

- [ ] **Step 1: 写失败测试**

写入 `skill/preprocess/tests/test_anchor.py`：

```python
import pytest
from models import Node, NodeType, RelPos, ContainerAttrs
from anchor import apply_anchor


def make_node(id, x, y, w, h, node_type=NodeType.CONTAINER):
    return Node(
        id=id, type=node_type,
        screenX=x, screenY=y, widthDp=w, heightDp=h,
        attrs=ContainerAttrs(0, 0, 0, 0)
    )


def test_anchor_top_edge():
    anchor = make_node("text_1", 16.0, 48.0, 200.0, 24.0)
    other = make_node("img_1", 16.0, 80.0, 40.0, 40.0)
    nodes = [anchor, other]
    result = apply_anchor(nodes, anchor_id="text_1", anchor_edge="top")
    anchor_result = next(n for n in result if n.id == "text_1")
    other_result = next(n for n in result if n.id == "img_1")
    assert anchor_result.rel.dx == 0.0
    assert anchor_result.rel.dy == 0.0
    assert other_result.rel.dx == 0.0
    assert other_result.rel.dy == 32.0


def test_anchor_bottom_edge():
    anchor = make_node("text_1", 16.0, 48.0, 200.0, 24.0)
    other = make_node("img_1", 16.0, 80.0, 40.0, 40.0)
    nodes = [anchor, other]
    result = apply_anchor(nodes, anchor_id="text_1", anchor_edge="bottom")
    # anchor bottom y = 48 + 24 = 72
    other_result = next(n for n in result if n.id == "img_1")
    assert other_result.rel.dy == round(80.0 - 72.0, 1)  # 8.0


def test_anchor_not_found_raises():
    nodes = [make_node("text_1", 0, 0, 100, 20)]
    with pytest.raises(ValueError, match="anchor_id 'missing' not found"):
        apply_anchor(nodes, anchor_id="missing", anchor_edge="top")


def test_all_nodes_have_rel():
    nodes = [
        make_node("text_1", 0, 0, 100, 20),
        make_node("text_2", 0, 30, 100, 20),
    ]
    result = apply_anchor(nodes, anchor_id="text_1", anchor_edge="top")
    assert all(n.rel is not None for n in result)
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd skill/preprocess
pytest tests/test_anchor.py -v
```

Expected: FAIL with `ModuleNotFoundError: No module named 'anchor'`

- [ ] **Step 3: 实现 anchor.py**

```python
from models import Node, RelPos


def apply_anchor(nodes: list[Node], anchor_id: str, anchor_edge: str) -> list[Node]:
    anchor = next((n for n in nodes if n.id == anchor_id), None)
    if anchor is None:
        raise ValueError(f"anchor_id '{anchor_id}' not found in nodes")

    anchor_x = anchor.screenX
    anchor_y = anchor.screenY if anchor_edge == "top" else round(anchor.screenY + anchor.heightDp, 1)

    for node in nodes:
        node.rel = RelPos(
            dx=round(node.screenX - anchor_x, 1),
            dy=round(node.screenY - anchor_y, 1)
        )

    return nodes
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd skill/preprocess
pytest tests/test_anchor.py -v
```

Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add skill/preprocess/anchor.py skill/preprocess/tests/test_anchor.py
git commit -m "feat: implement anchor relative coordinate calculation"
```

---

### Task 5: JSON 序列化输出

**Files:**
- Modify: `skill/preprocess/models.py`

- [ ] **Step 1: 为 DesignDoc 添加序列化方法**

在 `skill/preprocess/models.py` 末尾追加：

```python
import json
from dataclasses import asdict


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
        d["attrs"] = asdict(node.attrs)
    return d


def design_doc_to_json(doc: DesignDoc) -> str:
    data = {
        "viewport": doc.viewport,
        "anchor": {"id": doc.anchor.id, "edge": doc.anchor.edge},
        "nodes": [_node_to_dict(n) for n in doc.nodes]
    }
    return json.dumps(data, ensure_ascii=False, indent=2)
```

- [ ] **Step 2: 写测试**

在 `skill/preprocess/tests/test_anchor.py` 末尾追加：

```python
from models import AnchorRef, DesignDoc, design_doc_to_json
import json


def test_design_doc_serialization():
    nodes = [make_node("text_1", 16.0, 48.0, 200.0, 24.0)]
    nodes = apply_anchor(nodes, "text_1", "top")
    doc = DesignDoc(viewport=375, anchor=AnchorRef(id="text_1", edge="top"), nodes=nodes)
    output = json.loads(design_doc_to_json(doc))
    assert output["viewport"] == 375
    assert output["anchor"]["id"] == "text_1"
    assert len(output["nodes"]) == 1
    assert output["nodes"][0]["rel"]["dx"] == 0.0
```

- [ ] **Step 3: 运行测试**

```bash
cd skill/preprocess
pytest tests/test_anchor.py::test_design_doc_serialization -v
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add skill/preprocess/models.py skill/preprocess/tests/test_anchor.py
git commit -m "feat: add design doc JSON serialization"
```

---

### Task 6: CLI 主脚本

**Files:**
- Create: `skill/preprocess/preprocess.py`
- Create: `skill/preprocess/tests/test_cli.py`

- [ ] **Step 1: 写 CLI 集成测试**

写入 `skill/preprocess/tests/test_cli.py`：

```python
import json
import subprocess
import sys
from pathlib import Path

FIXTURE = str(Path(__file__).parent / "fixtures/simple.html")
SCRIPT = str(Path(__file__).parent.parent / "preprocess.py")


def test_list_only_outputs_json_array():
    result = subprocess.run(
        [sys.executable, SCRIPT, "--input", FIXTURE, "--viewport", "375", "--list-only"],
        capture_output=True, text=True
    )
    assert result.returncode == 0
    nodes = json.loads(result.stdout)
    assert isinstance(nodes, list)
    assert len(nodes) > 0
    assert "id" in nodes[0]
    assert "type" in nodes[0]
    assert "screenX" in nodes[0]


def test_full_run_outputs_json_file(tmp_path):
    output = str(tmp_path / "design.json")
    result = subprocess.run(
        [sys.executable, SCRIPT,
         "--input", FIXTURE,
         "--viewport", "375",
         "--anchor-id", "text_1",
         "--anchor-edge", "top",
         "--output", output],
        capture_output=True, text=True
    )
    assert result.returncode == 0
    data = json.loads(Path(output).read_text())
    assert data["viewport"] == 375
    assert data["anchor"]["id"] == "text_1"
    assert len(data["nodes"]) > 0
    assert "rel" in data["nodes"][0]


def test_missing_anchor_id_exits_with_message(tmp_path):
    output = str(tmp_path / "design.json")
    result = subprocess.run(
        [sys.executable, SCRIPT,
         "--input", FIXTURE,
         "--viewport", "375",
         "--output", output],
        capture_output=True, text=True
    )
    # 没有 --anchor-id 且非 --list-only，应输出节点列表到 stdout 并以非零退出
    assert result.returncode == 1
    assert "anchor" in result.stdout.lower() or len(json.loads(result.stdout)) > 0
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd skill/preprocess
pytest tests/test_cli.py -v
```

Expected: FAIL with `No such file or directory: 'preprocess.py'`

- [ ] **Step 3: 实现 preprocess.py**

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
    parser.add_argument("--list-only", action="store_true", help="仅输出节点列表，不生成 JSON 文件")
    return parser.parse_args()


async def main():
    args = parse_args()

    nodes = await extract_nodes(args.input, args.viewport)

    if args.list_only:
        output = [
            {"id": n.id, "type": n.type.value,
             "screenX": n.screenX, "screenY": n.screenY,
             "widthDp": n.widthDp, "heightDp": n.heightDp}
            for n in nodes
        ]
        print(json.dumps(output, ensure_ascii=False, indent=2))
        return

    if not args.anchor_id:
        # 输出节点列表供 AI 选择锚点，以非零退出提示需要指定 --anchor-id
        output = [
            {"id": n.id, "type": n.type.value,
             "screenX": n.screenX, "screenY": n.screenY,
             "widthDp": n.widthDp, "heightDp": n.heightDp}
            for n in nodes
        ]
        print(json.dumps(output, ensure_ascii=False, indent=2))
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


if __name__ == "__main__":
    asyncio.run(main())
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd skill/preprocess
pytest tests/test_cli.py -v
```

Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add skill/preprocess/preprocess.py skill/preprocess/tests/test_cli.py
git commit -m "feat: implement preprocess CLI entrypoint"
```

---

### Task 7: 端到端验证

**Files:**
- 无新增文件

- [ ] **Step 1: 运行全部测试**

```bash
cd skill/preprocess
pytest tests/ -v
```

Expected: 全部 PASS

- [ ] **Step 2: 手动端到端验证 list-only 模式**

```bash
cd skill/preprocess
python preprocess.py \
  --input tests/fixtures/simple.html \
  --viewport 375 \
  --list-only
```

Expected: 输出 JSON 数组，包含 text_1、img_1、list_1、container_* 等节点

- [ ] **Step 3: 手动端到端验证完整输出**

```bash
cd skill/preprocess
python preprocess.py \
  --input tests/fixtures/simple.html \
  --viewport 375 \
  --anchor-id text_1 \
  --anchor-edge top \
  --output /tmp/design.json

cat /tmp/design.json
```

Expected: 输出包含 `viewport`、`anchor`、`nodes` 的完整 JSON，所有节点含 `rel` 字段，`text_1` 的 `rel.dx` 和 `rel.dy` 均为 0.0

- [ ] **Step 4: Commit**

```bash
git add .
git commit -m "feat: preprocess tool complete"
```
