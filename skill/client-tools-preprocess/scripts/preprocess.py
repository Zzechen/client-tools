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
