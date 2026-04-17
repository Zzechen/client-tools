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
