import pytest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "skill" / "preprocess"))

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
