import json
import subprocess
import sys
from pathlib import Path

FIXTURE = str(Path(__file__).parent / "fixtures/simple.html")
SCRIPT = str(Path(__file__).parent.parent.parent / "skill" / "client-tools-preprocess" / "scripts" / "preprocess.py")
PYTHON = str(Path(__file__).parent.parent.parent / "skill" / "client-tools-preprocess" / "scripts" / ".venv" / "bin" / "python")


def test_list_only_outputs_json_array():
    result = subprocess.run(
        [PYTHON, SCRIPT, "--input", FIXTURE, "--viewport", "375", "--list-only"],
        capture_output=True, text=True
    )
    assert result.returncode == 0
    nodes = json.loads(result.stdout)
    assert isinstance(nodes, list)
    assert len(nodes) > 0
    assert "id" in nodes[0]
    assert "type" in nodes[0]
    assert "screenX" in nodes[0]


def test_full_run_outputs_json_file(tmp_path):
    output = str(tmp_path / "design.json")
    result = subprocess.run(
        [PYTHON, SCRIPT,
         "--input", FIXTURE,
         "--viewport", "375",
         "--anchor-id", "text_1",
         "--anchor-edge", "top",
         "--output", output],
        capture_output=True, text=True
    )
    assert result.returncode == 0
    data = json.loads(Path(output).read_text())
    assert data["viewport"] == 375
    assert data["anchor"]["id"] == "text_1"
    assert len(data["nodes"]) > 0
    assert "rel" in data["nodes"][0]


def test_missing_anchor_id_exits_with_message(tmp_path):
    output = str(tmp_path / "design.json")
    result = subprocess.run(
        [PYTHON, SCRIPT,
         "--input", FIXTURE,
         "--viewport", "375",
         "--output", output],
        capture_output=True, text=True
    )
    assert result.returncode == 1
    nodes = json.loads(result.stdout)
    assert isinstance(nodes, list)
    assert len(nodes) > 0
