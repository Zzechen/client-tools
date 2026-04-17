import pytest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).parent.parent))

from models import Node, NodeType, RelPos, ContainerAttrs, AnchorRef, DesignDoc, design_doc_to_json
from anchor import apply_anchor
import json


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


def test_design_doc_serialization():
    nodes = [make_node("text_1", 16.0, 48.0, 200.0, 24.0)]
    nodes = apply_anchor(nodes, "text_1", "top")
    doc = DesignDoc(viewport=375, anchor=AnchorRef(id="text_1", edge="top"), nodes=nodes)
    output = json.loads(design_doc_to_json(doc))
    assert output["viewport"] == 375
    assert output["anchor"]["id"] == "text_1"
    assert len(output["nodes"]) == 1
    assert output["nodes"][0]["rel"]["dx"] == 0.0
