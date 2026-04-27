import asyncio
from pathlib import Path
from playwright.async_api import async_playwright
from models import (
    Node, NodeType, TextAttrs, ImageAttrs, ListAttrs, ContainerAttrs
)
from svg_extractor import infer_drawable_name, svg_content_to_vector

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


async def _check_svg_only(page, selector: str) -> tuple[bool, str]:
    """检测元素是否只含单个 SVG 子节点（无文本、无其他可见子元素）。
    返回 (is_svg_only, svg_outerHTML)。
    """
    result = await page.evaluate(f"""() => {{
        const el = document.querySelector("{selector}");
        if (!el) return {{ isSvgOnly: false, svgContent: "" }};
        const visibleChildren = Array.from(el.children).filter(c => {{
            const s = window.getComputedStyle(c);
            return s.display !== 'none' && s.visibility !== 'hidden';
        }});
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


async def _get_elem_info(page, selector: str) -> dict:
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
            if tag in ("html", "head", "body", "style", "script", "meta", "link", "defs"):
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

            svg_drawable: str | None = None

            if tag == "svg":
                # 直接 SVG 元素：面积超过视口 50% 则为装饰背景
                area = rect["width"] * rect["height"]
                if area > viewport_area * 0.5:
                    elem_info = await _get_elem_info(page, selector)
                    drawable_name = infer_drawable_name(
                        elem_info["ariaLabel"], elem_info["elementId"], node_id
                    )
                    node_type = NodeType.DRAWABLE
                    svg_drawable = drawable_name
                    if drawables_dir:
                        svg_raw = await el.evaluate("el => el.outerHTML")
                        svg_content_to_vector(svg_raw, drawables_dir / f"{drawable_name}.xml")
            else:
                # 非 SVG 元素：检测是否只含单个 SVG 子节点
                is_svg_only, svg_content = await _check_svg_only(page, selector)
                if is_svg_only and svg_content:
                    elem_info = await _get_elem_info(page, selector)
                    drawable_name = infer_drawable_name(
                        elem_info["ariaLabel"], elem_info["elementId"], node_id
                    )
                    node_type = NodeType.IMAGE
                    svg_drawable = drawable_name
                    if drawables_dir:
                        svg_content_to_vector(svg_content, drawables_dir / f"{drawable_name}.xml")

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
