#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEMO_DIR="$REPO_ROOT/clients/ios/demo"
WORKSPACE="$DEMO_DIR/ClientToolsDemo.xcworkspace"
SCHEME="ClientToolsDemo"
BUNDLE_ID="com.clienttools.demo"
BUILD_DIR="$DEMO_DIR/.build"
SIMULATOR_NAME="${1:-iPhone 17}"

# ── 1. 找到目标模拟器 ─────────────────────────────────────────────
echo ">>> 查找模拟器: $SIMULATOR_NAME"
SIM_UDID=$(xcrun simctl list devices available -j \
  | python3 -c "
import sys, json
data = json.load(sys.stdin)
name = '$SIMULATOR_NAME'
for runtime, devices in data['devices'].items():
    for d in devices:
        if d['name'] == name and d['isAvailable']:
            print(d['udid'])
            exit()
" 2>/dev/null || true)

if [[ -z "$SIM_UDID" ]]; then
  echo "错误: 找不到模拟器 '$SIMULATOR_NAME'"
  echo ""
  echo "可用 iPhone 模拟器:"
  xcrun simctl list devices available | grep "iPhone"
  exit 1
fi
echo "    UDID: $SIM_UDID"

# ── 2. 启动模拟器 ─────────────────────────────────────────────────
SIM_STATE=$(xcrun simctl list devices -j \
  | python3 -c "
import sys, json
data = json.load(sys.stdin)
for runtime, devices in data['devices'].items():
    for d in devices:
        if d['udid'] == '$SIM_UDID':
            print(d['state'])
            exit()
")

if [[ "$SIM_STATE" != "Booted" ]]; then
  echo ">>> 启动模拟器..."
  xcrun simctl boot "$SIM_UDID"
  # 等待模拟器就绪
  until xcrun simctl list devices | grep "$SIM_UDID" | grep -q "Booted"; do
    sleep 1
  done
fi

# 确保模拟器 UI 可见
open -a Simulator --args -CurrentDeviceUDID "$SIM_UDID" 2>/dev/null || true

# ── 3. 编译 ───────────────────────────────────────────────────────
echo ">>> 编译 $SCHEME ..."
xcodebuild \
  -workspace "$WORKSPACE" \
  -scheme "$SCHEME" \
  -destination "id=$SIM_UDID" \
  -derivedDataPath "$BUILD_DIR" \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  build \
  | xcpretty 2>/dev/null || cat  # 有 xcpretty 就美化输出，没有就原始输出

# ── 4. 安装并启动 ─────────────────────────────────────────────────
APP_PATH=$(find "$BUILD_DIR/Build/Products/Debug-iphonesimulator" -name "*.app" -maxdepth 1 | head -1)
if [[ -z "$APP_PATH" ]]; then
  echo "错误: 找不到编译产物，请检查编译日志"
  exit 1
fi

echo ">>> 安装 App: $(basename "$APP_PATH")"
xcrun simctl install "$SIM_UDID" "$APP_PATH"

echo ">>> 启动 App: $BUNDLE_ID"
xcrun simctl launch --console-pty "$SIM_UDID" "$BUNDLE_ID" || \
  xcrun simctl launch "$SIM_UDID" "$BUNDLE_ID"

echo ""
echo "✓ 完成！App 已在模拟器 '$SIMULATOR_NAME' 上运行"
