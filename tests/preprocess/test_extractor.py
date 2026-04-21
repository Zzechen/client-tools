import pytest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "skill" / "client-tools-preprocess" / "scripts"))

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
        parts = node.id.rsplit("_", 1)
        assert len(parts) == 2
        assert parts[1].isdigit()


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
