import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "skill" / "client-tools-preprocess" / "scripts"))

from svg_extractor import infer_drawable_name, svg_content_to_vector


def test_infer_name_from_aria_label():
    assert infer_drawable_name("wechat login", "btn_wechat", "container_1") == "ic_wechat_login"


def test_infer_name_from_parent_id():
    assert infer_drawable_name("", "login_btn_wechat", "container_1") == "ic_wechat"


def test_infer_name_fallback_node_id():
    assert infer_drawable_name("", "", "container_5") == "ic_container_5"


def test_infer_name_strips_digits():
    assert infer_drawable_name("", "btn_close_1", "container_2") == "ic_close"


def test_svg_to_vector_writes_file(tmp_path):
    svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path d="M4 4l16 16"/></svg>'
    out = tmp_path / "ic_test.xml"
    result = svg_content_to_vector(svg, out)
    assert result is True
    assert out.exists()
    content = out.read_text()
    assert "<vector" in content


def test_svg_to_vector_invalid_svg_returns_false(tmp_path):
    out = tmp_path / "ic_bad.xml"
    result = svg_content_to_vector("not svg content", out)
    assert result is False
