import asyncio
from pathlib import Path
from playwright.async_api import async_playwright
from models import (
    Node, NodeType, TextAttrs, ImageAttrs, ListAttrs, ContainerAttrs
)

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
    elif node_type == NodeType.IMAGE:
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


async def extract_nodes(html_path: str, viewport: int) -> list[Node]:
    counters: dict = {}
    nodes: list[Node] = []

    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page(viewport={"width": viewport, "height": 812})
        await page.goto(f"file://{Path(html_path).resolve()}")
        await page.wait_for_load_state("networkidle")
        # bundler 型设计稿：JS 解包后用 replaceWith 替换整个 document，
        # 再动态执行 React/Babel 脚本，需等待框架完成渲染
        await asyncio.sleep(5)

        elements = await page.query_selector_all("*")

        for el in elements:
            tag = await el.evaluate("el => el.tagName.toLowerCase()")
            if tag in ("html", "head", "body", "style", "script", "meta", "link"):
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
