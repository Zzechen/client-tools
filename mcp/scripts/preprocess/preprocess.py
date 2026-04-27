#!/usr/bin/env python3
import argparse
import asyncio
import json
import sys
from pathlib import Path

from extractor import extract_nodes
from models import Node


def parse_args():
    parser = argparse.ArgumentParser(description="设计稿预处理工具")
    parser.add_argument("--input", required=True, help="HTML 设计稿文件路径")
    parser.add_argument("--viewport", type=int, required=True, help="设计稿宽度（px）")
    parser.add_argument("--output", help="输出 JSON 文件路径，默认与 input 同目录同名 .json")
    parser.add_argument("--drawables-dir", help="Vector Drawable 输出目录，不指定则不提取 SVG")
    return parser.parse_args()


def node_to_dict(node: Node) -> dict:
    d = {
        "id": node.id,
        "type": node.type.value,
        "screenX": node.screenX,
        "screenY": node.screenY,
        "widthDp": node.widthDp,
        "heightDp": node.heightDp,
    }
    if node.attrs:
        from dataclasses import asdict
        attrs_dict = asdict(node.attrs)
        attrs_dict = {k: v for k, v in attrs_dict.items() if v is not None}
        d["attrs"] = attrs_dict
    return d


async def main():
    args = parse_args()

    drawables_dir = Path(args.drawables_dir) if args.drawables_dir else None
    nodes = await extract_nodes(args.input, args.viewport, drawables_dir=drawables_dir)

    node_list = [node_to_dict(n) for n in nodes]

    output_path = args.output or str(Path(args.input).with_suffix(".json"))
    doc = {
        "viewport": args.viewport,
        "nodes": node_list
    }
    Path(output_path).write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")

    # stdout 输出供 MCP 工具捕获
    print(json.dumps({
        "outputPath": output_path,
        "nodeCount": len(nodes),
        "nodes": node_list
    }, ensure_ascii=False))

    if drawables_dir:
        xml_files = list(drawables_dir.glob("*.xml"))
        print(f"[drawables] {drawables_dir}，共 {len(xml_files)} 个文件", file=sys.stderr)


if __name__ == "__main__":
    asyncio.run(main())
