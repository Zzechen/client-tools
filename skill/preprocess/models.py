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
