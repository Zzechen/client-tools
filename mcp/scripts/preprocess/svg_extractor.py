import re
import subprocess
import tempfile
from pathlib import Path


def infer_drawable_name(aria_label: str, parent_id: str, node_id: str) -> str:
    """按优先级推断 drawable 文件名（不含扩展名）。"""
    if aria_label:
        name = re.sub(r"[^a-z0-9]+", "_", aria_label.lower().strip()).strip("_")
        if name:
            return f"ic_{name}"

    if parent_id:
        parts = [p for p in parent_id.split("_") if not p.isdigit()]
        if parts:
            return f"ic_{parts[-1]}"

    return f"ic_{node_id}"


def svg_content_to_vector(svg_content: str, output_path: Path) -> bool:
    """将 SVG 字符串转换为 Android Vector Drawable XML，写入 output_path。
    依赖 svg2vectordrawable（npx svg2vectordrawable）。
    返回 True 表示成功，False 表示转换失败（跳过）。
    """
    try:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(suffix=".svg", mode="w",
                                         encoding="utf-8", delete=False) as f:
            f.write(svg_content)
            tmp_svg = Path(f.name)

        result = subprocess.run(
            ["npx", "svg2vectordrawable", "-i", str(tmp_svg), "-o", str(output_path)],
            capture_output=True, text=True, timeout=15
        )
        tmp_svg.unlink(missing_ok=True)
        return result.returncode == 0 and output_path.exists()
    except Exception:
        return False
