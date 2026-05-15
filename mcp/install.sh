#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MCP_JSON="$PROJECT_ROOT/.mcp.json"

echo "==> Building MCP Server..."
cd "$SCRIPT_DIR"
npm install --silent
npm run build

# 更新 .mcp.json 中的绝对路径（适配不同成员的本地路径）
DIST_PATH="$SCRIPT_DIR/dist/index.js"

python3 - <<EOF
import json, sys

path = "$MCP_JSON"
dist = "$DIST_PATH"

with open(path, "r") as f:
    config = json.load(f)

config["mcpServers"]["client-tools"]["args"] = [dist]

with open(path, "w") as f:
    json.dump(config, f, indent=2, ensure_ascii=False)
    f.write("\n")

print(f"==> Updated .mcp.json: {dist}")
EOF

echo ""
echo "✓ Done. Restart Claude Code / Cursor to apply."
echo "  Config: $MCP_JSON"

# client-tools skills 软链
echo ""
echo "==> Linking client-tools skills..."
ln -sf "$PROJECT_ROOT/skill/client-tools-inspect" "$HOME/.claude/skills/client-tools-inspect"
echo "[OK] client-tools skills linked"
