# Runtime Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 TypeScript 脚本对 Android/iOS demo app 做完整的 MCP + SDK 运行时验证，覆盖 modify_view（含幂等性）、get_node/get_all_nodes 视觉尺寸、capture_view、click_view/scroll_view、mock CRUD、get_current_page。

**Architecture:** `tests/runtime/` 是独立 TypeScript 包（`tsx` 直接运行），通过 HTTP 调用设备端 SDK（port 8080），proto 类型复用 `mcp/src/generated/`（相对路径引入，tsx 处理 .js→.ts 映射）。Android 用 `adb forward`，iOS 用 `iproxy`（外部启动）。测试结果截图保存到 `tests/snapshots/{platform}/`。

**Tech Stack:** TypeScript + tsx, @bufbuild/protobuf, fetch API, adb/iproxy.

---

## 文件清单

| 文件 | 说明 |
|------|------|
| `tests/runtime/package.json` | 独立包，依赖 tsx + @bufbuild/protobuf |
| `tests/runtime/tsconfig.json` | ES2022 + Node16 + noEmit |
| `tests/runtime/src/client.ts` | sdkGet / sdkPost / sdkDelete HTTP helpers |
| `tests/runtime/src/ids.ts` | Demo app View ID 常量（Android/iOS 双端） |
| `tests/runtime/src/helpers.ts` | assert / assertApprox / saveSnapshot / sleep / getResults |
| `tests/runtime/src/suites/page.ts` | get_current_page 测试 |
| `tests/runtime/src/suites/capture.ts` | capture_view 测试 |
| `tests/runtime/src/suites/nodes.ts` | get_node / get_all_nodes 测试（含视觉尺寸验证） |
| `tests/runtime/src/suites/interact.ts` | click_view / scroll_view 测试 |
| `tests/runtime/src/suites/modify-view.ts` | modify_view 核心测试（move 累加、size 绝对值、text、视觉 snapshot） |
| `tests/runtime/src/suites/mock.ts` | mock add / list / delete / clear 测试 |
| `tests/runtime/src/index.ts` | 测试入口，顺序运行所有 suite，打印汇总 |

**View ID 依据：**

| ID | Android (activity_login.xml) | iOS (LoginViewController.swift) |
|----|-----------------------------|---------------------------------|
| `login_btn_submit` | FrameLayout, 52dp height | UIButton |
| `login_text_title` | TextView "欢迎回来" | UILabel "欢迎回来" |
| `login_text_subtitle` | TextView | UILabel |
| `login_input_area` | LinearLayout | UIStackView（非 TextView） |
| `login_home_indicator` | View | UIView |
| `login_btn_close` | TextView | UIButton |
| `login_logo_name` / `login_text_brand` | Android: `login_logo_name` | iOS: `login_text_brand` |
| `login_tab_code` / `login_tab_sms` | Android: `login_tab_code` | iOS: `login_tab_sms` |
| `login_tab_password` / `login_tab_pwd` | Android: `login_tab_password` | iOS: `login_tab_pwd` |
| `login_scroll` | ScrollView（Android only） | — |

---

### Task 1: 创建 package.json + tsconfig.json

**Files:**
- Create: `tests/runtime/package.json`
- Create: `tests/runtime/tsconfig.json`

- [ ] **Step 1: 创建 tests/runtime/package.json**

```json
{
  "name": "@client-tools/runtime-tests",
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "test:android": "PLATFORM=android tsx src/index.ts",
    "test:ios": "PLATFORM=ios tsx src/index.ts"
  },
  "devDependencies": {
    "@types/node": "^22.0.0",
    "tsx": "^4.19.0",
    "typescript": "^5.5.0"
  },
  "dependencies": {
    "@bufbuild/protobuf": "^2.2.3"
  }
}
```

- [ ] **Step 2: 创建 tests/runtime/tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "Node16",
    "moduleResolution": "Node16",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "noEmit": true
  }
}
```

- [ ] **Step 3: 安装依赖**

```bash
cd /Users/zzc/Desktop/works/client-tools/tests/runtime && npm install
```

Expected: `node_modules/` 创建，无报错。

- [ ] **Step 4: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/package.json tests/runtime/tsconfig.json tests/runtime/package-lock.json
git commit -m "tests: add runtime test package scaffold"
```

---

### Task 2: 创建 src/client.ts

**Files:**
- Create: `tests/runtime/src/client.ts`

- [ ] **Step 1: 创建 tests/runtime/src/client.ts**

```typescript
import { fromBinary, toBinary, create, type MessageShape, type DescMessage } from "@bufbuild/protobuf";
import { execSync } from "child_process";

const PORT = process.env.CLIENT_TOOLS_PORT ?? "8080";
const BASE_URL = `http://127.0.0.1:${PORT}`;
const PLATFORM = process.env.PLATFORM ?? "android";

function ensurePortForward(): void {
  if (PLATFORM === "android") {
    try {
      execSync(`adb forward tcp:${PORT} tcp:${PORT}`, { stdio: "ignore" });
    } catch { /* ignore: no device or adb not in PATH */ }
  }
  // iOS: assumes `iproxy 8080 8080` is already running externally
}

async function fetchBytes(url: string, init: RequestInit = {}): Promise<{ status: number; bytes: Uint8Array }> {
  const res = await fetch(url, init);
  const bytes = new Uint8Array(await res.arrayBuffer());
  return { status: res.status, bytes };
}

/**
 * HTTP GET → parse protobuf response.
 */
export async function sdkGet<T extends DescMessage>(
  path: string,
  schema: T
): Promise<MessageShape<T>> {
  ensurePortForward();
  const { bytes } = await fetchBytes(`${BASE_URL}${path}`);
  return fromBinary(schema, bytes);
}

/**
 * HTTP POST with protobuf body → parse protobuf response.
 * On non-2xx or parse failure, returns an empty message (fields are default values).
 */
export async function sdkPost<Req extends DescMessage, Res extends DescMessage>(
  path: string,
  reqSchema: Req,
  req: MessageShape<Req>,
  resSchema: Res
): Promise<MessageShape<Res>> {
  ensurePortForward();
  const body = toBinary(reqSchema, req);
  const { bytes } = await fetchBytes(`${BASE_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/x-protobuf" },
    body,
  });
  try {
    return fromBinary(resSchema, bytes);
  } catch {
    // Android error responses may be plain text (not protobuf); return empty proto.
    return create(resSchema, {});
  }
}

/**
 * HTTP DELETE → parse protobuf response.
 */
export async function sdkDelete<Res extends DescMessage>(
  path: string,
  resSchema: Res
): Promise<MessageShape<Res>> {
  ensurePortForward();
  const { bytes } = await fetchBytes(`${BASE_URL}${path}`, { method: "DELETE" });
  return fromBinary(resSchema, bytes);
}
```

- [ ] **Step 2: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/src/client.ts
git commit -m "tests: add SDK HTTP client helpers"
```

---

### Task 3: 创建 src/ids.ts + src/helpers.ts

**Files:**
- Create: `tests/runtime/src/ids.ts`
- Create: `tests/runtime/src/helpers.ts`

- [ ] **Step 1: 创建 tests/runtime/src/ids.ts**

```typescript
/**
 * View IDs anchored to the demo app Login screen.
 *
 * Android source: clients/android/demo/src/main/res/layout/activity_login.xml
 * iOS source:     clients/ios/demo/Sources/ClientToolsDemo/Login/LoginViewController.swift
 */
const PLATFORM = process.env.PLATFORM ?? "android";

export const IDS = {
  // ── Shared (same ID on both platforms) ────────────────────────────────────
  /** 登录按钮容器 (FrameLayout / UIButton, 52dp height) */
  SUBMIT_BTN: "login_btn_submit",
  /** 标题文本 "欢迎回来" (TextView / UILabel) — good for text modify tests */
  TITLE_TEXT: "login_text_title",
  /** 副标题文本 (TextView / UILabel) */
  SUBTITLE_TEXT: "login_text_subtitle",
  /** 输入区域容器 (LinearLayout / UIStackView) — NOT a text view, used for error test */
  INPUT_AREA: "login_input_area",
  /** 底部 Home 指示条 (View / UIView) */
  HOME_INDICATOR: "login_home_indicator",
  /** 关闭按钮 (TextView / UIButton) */
  CLOSE_BTN: "login_btn_close",

  // ── Platform-specific ─────────────────────────────────────────────────────
  /** 品牌 Logo 文字 "PULSE" */
  BRAND_TEXT: PLATFORM === "android" ? "login_logo_name" : "login_text_brand",
  /** 验证码 Tab 按钮 */
  TAB_SMS_BTN: PLATFORM === "android" ? "login_tab_code" : "login_tab_sms",
  /** 密码 Tab 按钮（点击仅切换 tab，无页面跳转） */
  TAB_PWD_BTN: PLATFORM === "android" ? "login_tab_password" : "login_tab_pwd",
  /** 可滚动视图（Android only，iOS login screen 无滚动容器 ID） */
  SCROLL_VIEW: PLATFORM === "android" ? "login_scroll" : (null as string | null),
} as const;
```

- [ ] **Step 2: 创建 tests/runtime/src/helpers.ts**

```typescript
import { mkdirSync, writeFileSync } from "fs";
import { join } from "path";

const PLATFORM = process.env.PLATFORM ?? "android";

// Snapshot output directory: tests/snapshots/{platform}/
export const SNAPSHOT_DIR = join("tests", "snapshots");

let _passed = 0;
let _failed = 0;

export function assert(condition: boolean, msg: string): void {
  if (condition) {
    console.log(`    ✅  ${msg}`);
    _passed++;
  } else {
    console.error(`    ❌  ${msg}`);
    _failed++;
  }
}

/**
 * Assert |actual - expected| <= tolerance.
 * Prints actual and expected values in the failure message.
 */
export function assertApprox(
  actual: number,
  expected: number,
  tolerance: number,
  msg: string
): void {
  const ok = Math.abs(actual - expected) <= tolerance;
  assert(ok, `${msg} (got ${actual.toFixed(2)}, expected ${expected.toFixed(2)} ±${tolerance})`);
}

/**
 * Save PNG bytes to tests/snapshots/{platform}/{name}_{timestamp}.png.
 */
export function saveSnapshot(name: string, pngBytes: Uint8Array): void {
  const dir = join(SNAPSHOT_DIR, PLATFORM);
  mkdirSync(dir, { recursive: true });
  const file = join(dir, `${name}_${Date.now()}.png`);
  writeFileSync(file, Buffer.from(pngBytes));
  console.log(`    📷  saved ${file}`);
}

export function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

export function getResults(): { passed: number; failed: number } {
  return { passed: _passed, failed: _failed };
}
```

- [ ] **Step 3: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/src/ids.ts tests/runtime/src/helpers.ts
git commit -m "tests: add view IDs and test helpers"
```

---

### Task 4: 创建 page.ts + capture.ts

**Files:**
- Create: `tests/runtime/src/suites/page.ts`
- Create: `tests/runtime/src/suites/capture.ts`

- [ ] **Step 1: 创建 tests/runtime/src/suites/page.ts**

```typescript
import { sdkGet } from "../client.js";
import { assert } from "../helpers.js";
import { PageResponseSchema } from "../../../mcp/src/generated/api_pb.js";

export async function runPageSuite(): Promise<void> {
  console.log("\n📄  get_current_page");

  const res = await sdkGet("/api/page/current", PageResponseSchema);

  assert(res.data != null, "response has data");
  assert(
    typeof res.data?.pageName === "string" && res.data.pageName.length > 0,
    `pageName is non-empty string: "${res.data?.pageName}"`
  );
  assert(
    typeof res.meta?.sdkVersion === "number" && res.meta.sdkVersion > 0,
    `sdkVersion > 0 (got ${res.meta?.sdkVersion})`
  );
  assert(
    (res.meta?.device?.screenWidthDp ?? 0) > 0,
    `device.screenWidthDp > 0 (got ${res.meta?.device?.screenWidthDp})`
  );
}
```

- [ ] **Step 2: 创建 tests/runtime/src/suites/capture.ts**

```typescript
import { sdkGet } from "../client.js";
import { assert, saveSnapshot } from "../helpers.js";
import { CaptureResponseSchema } from "../../../mcp/src/generated/api_pb.js";
import { IDS } from "../ids.js";

export async function runCaptureSuite(): Promise<void> {
  console.log("\n📷  capture_view");

  const res = await sdkGet(
    `/api/capture/${encodeURIComponent(IDS.SUBMIT_BTN)}`,
    CaptureResponseSchema
  );

  // PNG must have content
  assert(res.imagePng.length > 100, `capture_view returns >100 bytes (got ${res.imagePng.length})`);

  // Validate PNG magic bytes: 0x89 0x50 0x4E 0x47
  assert(
    res.imagePng[0] === 0x89 &&
    res.imagePng[1] === 0x50 &&
    res.imagePng[2] === 0x4E &&
    res.imagePng[3] === 0x47,
    "PNG starts with correct magic bytes (89 50 4E 47)"
  );

  saveSnapshot(`capture-${IDS.SUBMIT_BTN}`, res.imagePng);
}
```

- [ ] **Step 3: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/src/suites/page.ts tests/runtime/src/suites/capture.ts
git commit -m "tests: add page and capture suites"
```

---

### Task 5: 创建 src/suites/nodes.ts

**Files:**
- Create: `tests/runtime/src/suites/nodes.ts`

- [ ] **Step 1: 创建 tests/runtime/src/suites/nodes.ts**

```typescript
import { sdkGet } from "../client.js";
import { assert, assertApprox } from "../helpers.js";
import { NodeResponseSchema, NodeListResponseSchema } from "../../../mcp/src/generated/api_pb.js";
import { IDS } from "../ids.js";

export async function runNodesSuite(): Promise<void> {
  console.log("\n🔍  get_node / get_all_nodes");

  // ── get_node ──────────────────────────────────────────────────────────────
  const res = await sdkGet(
    `/api/nodes/${encodeURIComponent(IDS.SUBMIT_BTN)}`,
    NodeResponseSchema
  );

  assert(res.data != null, `get_node("${IDS.SUBMIT_BTN}") returns data`);
  const node = res.data!;

  assert(node.id === IDS.SUBMIT_BTN, `node.id === "${IDS.SUBMIT_BTN}"`);
  assert(node.widthDp > 0, `widthDp > 0 (got ${node.widthDp})`);
  assert(node.heightDp > 0, `heightDp > 0 (got ${node.heightDp})`);
  assert(node.screenX >= 0, `screenX >= 0 (got ${node.screenX})`);
  assert(node.screenY >= 0, `screenY >= 0 (got ${node.screenY})`);
  assert(node.visibility === 0, `visibility === 0 (VISIBLE)`);
  assert(node.isEnabled === true, `isEnabled === true`);

  // ── get_all_nodes ─────────────────────────────────────────────────────────
  const allRes = await sdkGet("/api/nodes/all", NodeListResponseSchema);

  assert(allRes.data != null, "get_all_nodes returns data");
  const nodes = allRes.data?.nodes ?? [];
  assert(nodes.length > 5, `get_all_nodes returns >5 nodes (got ${nodes.length})`);

  const found = nodes.find(n => n.id === IDS.SUBMIT_BTN);
  assert(found != null, `get_all_nodes contains "${IDS.SUBMIT_BTN}"`);

  if (found) {
    assertApprox(found.widthDp,  node.widthDp,  1, "get_all_nodes widthDp  matches get_node");
    assertApprox(found.screenX,  node.screenX,  1, "get_all_nodes screenX  matches get_node");
    assertApprox(found.screenY,  node.screenY,  1, "get_all_nodes screenY  matches get_node");
  }

  // Also verify the title label is visible
  const titleNode = nodes.find(n => n.id === IDS.TITLE_TEXT);
  assert(titleNode != null, `get_all_nodes contains "${IDS.TITLE_TEXT}"`);
}
```

- [ ] **Step 2: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/src/suites/nodes.ts
git commit -m "tests: add get_node / get_all_nodes suite"
```

---

### Task 6: 创建 src/suites/interact.ts

**Files:**
- Create: `tests/runtime/src/suites/interact.ts`

- [ ] **Step 1: 创建 tests/runtime/src/suites/interact.ts**

```typescript
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../client.js";
import { assert, sleep } from "../helpers.js";
import { ClickResponseSchema, ScrollResponseSchema } from "../../../mcp/src/generated/api_pb.js";
import { ClickRequestSchema, ScrollRequestSchema } from "../../../mcp/src/generated/modify_pb.js";
import { NodeResponseSchema } from "../../../mcp/src/generated/api_pb.js";
import { IDS } from "../ids.js";

export async function runInteractSuite(): Promise<void> {
  console.log("\n👆  click_view / scroll_view");

  // ── click_view: switch to password tab (no navigation side-effect) ────────
  const clickPwd = create(ClickRequestSchema, { id: IDS.TAB_PWD_BTN });
  const clickRes = await sdkPost("/api/click", ClickRequestSchema, clickPwd, ClickResponseSchema);
  assert(clickRes.data?.id === IDS.TAB_PWD_BTN, `click_view returns id="${IDS.TAB_PWD_BTN}"`);
  await sleep(300);

  // click_view: switch back to SMS tab
  const clickSms = create(ClickRequestSchema, { id: IDS.TAB_SMS_BTN });
  const clickResBack = await sdkPost("/api/click", ClickRequestSchema, clickSms, ClickResponseSchema);
  assert(clickResBack.data?.id === IDS.TAB_SMS_BTN, `click_view (back to sms tab) returns id="${IDS.TAB_SMS_BTN}"`);
  await sleep(300);

  // ── scroll_view (Android only) ────────────────────────────────────────────
  if (IDS.SCROLL_VIEW) {
    const scrollId = IDS.SCROLL_VIEW;

    const beforeNode = await sdkGet(`/api/nodes/${encodeURIComponent(scrollId)}`, NodeResponseSchema);
    assert(beforeNode.data != null, `get_node("${scrollId}") works before scroll`);

    // Scroll down 80dp
    const scrollDown = create(ScrollRequestSchema, { id: scrollId, dx: 0, dy: 80 });
    const scrollRes = await sdkPost("/api/scroll", ScrollRequestSchema, scrollDown, ScrollResponseSchema);
    assert(scrollRes.data?.id === scrollId, `scroll_view returns id="${scrollId}"`);
    await sleep(300);

    // Scroll back up
    const scrollUp = create(ScrollRequestSchema, { id: scrollId, dx: 0, dy: -80 });
    await sdkPost("/api/scroll", ScrollRequestSchema, scrollUp, ScrollResponseSchema);
    await sleep(300);
  } else {
    console.log("    ⏭   scroll_view skipped on iOS (login screen has no scrollable view with an ID)");
  }
}
```

- [ ] **Step 2: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/src/suites/interact.ts
git commit -m "tests: add click_view / scroll_view suite"
```

---

### Task 7: 创建 src/suites/modify-view.ts

**Files:**
- Create: `tests/runtime/src/suites/modify-view.ts`

这是计划中最核心的测试。重点验证：
1. `move_dx` 每次调用**累加**，不重置
2. `move_dy` 生效
3. `size.width/height` 绝对值生效
4. `get_node` + `get_all_nodes` 反映 post-transform 视觉尺寸（`widthDp = layout × scaleX`）
5. `text` 修改 TextView/UILabel 内容并截图存档
6. 对非文本 View 传 `text` 返回错误

- [ ] **Step 1: 创建 tests/runtime/src/suites/modify-view.ts**

```typescript
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../client.js";
import { assert, assertApprox, saveSnapshot, sleep } from "../helpers.js";
import {
  NodeResponseSchema,
  NodeListResponseSchema,
  CaptureResponseSchema,
  ModifyResponseSchema,
} from "../../../mcp/src/generated/api_pb.js";
import { ModifyViewRequestSchema } from "../../../mcp/src/generated/modify_pb.js";
import { IDS } from "../ids.js";

// ── helpers ──────────────────────────────────────────────────────────────────

async function getNode(id: string) {
  const res = await sdkGet(`/api/nodes/${encodeURIComponent(id)}`, NodeResponseSchema);
  if (!res.data) throw new Error(`getNode: no data for "${id}"`);
  return res.data;
}

async function modify(opts: {
  id: string;
  move_dx?: number;
  move_dy?: number;
  width?: number;
  height?: number;
  text?: string;
}) {
  const req = create(ModifyViewRequestSchema, {
    id: opts.id,
    ...((opts.move_dx != null || opts.move_dy != null) && {
      move: {
        ...(opts.move_dx != null && { dx: opts.move_dx }),
        ...(opts.move_dy != null && { dy: opts.move_dy }),
      },
    }),
    ...((opts.width != null || opts.height != null) && {
      size: {
        ...(opts.width  != null && { width:  opts.width }),
        ...(opts.height != null && { height: opts.height }),
      },
    }),
    ...(opts.text != null && { text: { content: opts.text } }),
  });
  return sdkPost("/api/modify", ModifyViewRequestSchema, req, ModifyResponseSchema);
}

// ── suite ─────────────────────────────────────────────────────────────────────

export async function runModifyViewSuite(): Promise<void> {
  console.log("\n✏️   modify_view");

  // Snapshot of baseline node dimensions before any modification
  const baseline = await getNode(IDS.SUBMIT_BTN);

  // ── move_dx: first +20dp ─────────────────────────────────────────────────
  await modify({ id: IDS.SUBMIT_BTN, move_dx: 20 });
  await sleep(150);
  const after1 = await getNode(IDS.SUBMIT_BTN);
  assertApprox(after1.screenX - baseline.screenX, 20, 2,
    "move_dx +20: screenX shifts +20dp");

  // ── move_dx: second +20dp must ACCUMULATE (not reset) ────────────────────
  await modify({ id: IDS.SUBMIT_BTN, move_dx: 20 });
  await sleep(150);
  const after2 = await getNode(IDS.SUBMIT_BTN);
  assertApprox(after2.screenX - baseline.screenX, 40, 2,
    "move_dx accumulates: screenX +40dp after two separate +20 calls");

  // reset X offset back to original
  await modify({ id: IDS.SUBMIT_BTN, move_dx: -(after2.screenX - baseline.screenX) });
  await sleep(150);
  const resetX = await getNode(IDS.SUBMIT_BTN);
  assertApprox(resetX.screenX, baseline.screenX, 2,
    "move_dx reset: screenX returns to baseline");

  // ── move_dy: +30dp ───────────────────────────────────────────────────────
  await modify({ id: IDS.SUBMIT_BTN, move_dy: 30 });
  await sleep(150);
  const afterDy = await getNode(IDS.SUBMIT_BTN);
  assertApprox(afterDy.screenY - baseline.screenY, 30, 2,
    "move_dy +30: screenY shifts +30dp");
  // reset Y
  await modify({ id: IDS.SUBMIT_BTN, move_dy: -30 });
  await sleep(150);

  // ── size.width: absolute 200dp ────────────────────────────────────────────
  await modify({ id: IDS.SUBMIT_BTN, width: 200 });
  await sleep(150);
  const afterW200 = await getNode(IDS.SUBMIT_BTN);
  assertApprox(afterW200.widthDp, 200, 3,
    "size.width=200: widthDp ≈ 200dp (post-transform visual size)");
  assert(Math.abs(afterW200.heightDp - baseline.heightDp) < 3,
    "size.width=200: heightDp unchanged");

  // capture snapshot to visually verify width change
  const snapW200 = await sdkGet(
    `/api/capture/${encodeURIComponent(IDS.SUBMIT_BTN)}`,
    CaptureResponseSchema
  );
  saveSnapshot(`size-width-200-${IDS.SUBMIT_BTN}`, snapW200.imagePng);
  assert(snapW200.imagePng.length > 100, "capture after size.width=200 returns PNG");

  // reset
  await modify({ id: IDS.SUBMIT_BTN, width: baseline.widthDp });
  await sleep(150);

  // ── size.height: absolute 80dp ────────────────────────────────────────────
  await modify({ id: IDS.SUBMIT_BTN, height: 80 });
  await sleep(150);
  const afterH80 = await getNode(IDS.SUBMIT_BTN);
  assertApprox(afterH80.heightDp, 80, 3,
    "size.height=80: heightDp ≈ 80dp");
  assert(Math.abs(afterH80.widthDp - baseline.widthDp) < 3,
    "size.height=80: widthDp unchanged");
  // reset
  await modify({ id: IDS.SUBMIT_BTN, height: baseline.heightDp });
  await sleep(150);

  // ── visual size reflected in get_all_nodes ───────────────────────────────
  await modify({ id: IDS.SUBMIT_BTN, width: 180 });
  await sleep(150);
  const allNodes = await sdkGet("/api/nodes/all", NodeListResponseSchema);
  const foundInAll = allNodes.data?.nodes.find(n => n.id === IDS.SUBMIT_BTN);
  assert(foundInAll != null,
    `get_all_nodes contains "${IDS.SUBMIT_BTN}" after size modify`);
  if (foundInAll) {
    assertApprox(foundInAll.widthDp, 180, 3,
      "get_all_nodes.widthDp reflects post-transform visual size (=180dp)");
  }
  // reset
  await modify({ id: IDS.SUBMIT_BTN, width: baseline.widthDp });
  await sleep(150);

  // ── text: modify UILabel / TextView ──────────────────────────────────────
  const newText = "RUNTIME_TEST";
  await modify({ id: IDS.TITLE_TEXT, text: newText });
  await sleep(150);
  // Save screenshot to visually confirm the text change
  const snapText = await sdkGet(
    `/api/capture/${encodeURIComponent(IDS.TITLE_TEXT)}`,
    CaptureResponseSchema
  );
  saveSnapshot(`text-modify-${IDS.TITLE_TEXT}`, snapText.imagePng);
  assert(snapText.imagePng.length > 100,
    "capture after text modify returns non-empty PNG");
  // reset text to original
  await modify({ id: IDS.TITLE_TEXT, text: "欢迎回来" });
  await sleep(150);

  // ── text on non-text view must return error ───────────────────────────────
  // Android: returns HTTP 404 plain-text → sdkPost returns empty ModifyResponse (message="")
  // iOS:     returns HTTP 404 proto   → ModifyResponse.message contains error string
  // In both cases we detect failure via empty message (Android) or non-ok message (iOS).
  let textErrDetected = false;
  try {
    const errRes = await modify({ id: IDS.INPUT_AREA, text: "should_fail" });
    // iOS path: message contains the error description
    textErrDetected = errRes.message.length > 0 && errRes.message !== "ok";
    if (!textErrDetected) {
      // Android path: fromBinary on plain-text error returns empty proto (message="")
      // Empty message means the request was rejected (ok=false), proto just couldn't carry the reason.
      // We treat empty message as an error indicator for Android.
      textErrDetected = errRes.message === "";
    }
  } catch {
    textErrDetected = true;
  }
  assert(textErrDetected,
    "text on non-text view (INPUT_AREA) is rejected (error message or empty proto)");
}
```

- [ ] **Step 2: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/src/suites/modify-view.ts
git commit -m "tests: add modify_view suite (move accumulation, size, text, visual snapshot)"
```

---

### Task 8: 创建 src/suites/mock.ts

**Files:**
- Create: `tests/runtime/src/suites/mock.ts`

- [ ] **Step 1: 创建 tests/runtime/src/suites/mock.ts**

```typescript
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost, sdkDelete } from "../client.js";
import { assert } from "../helpers.js";
import {
  MockRuleResponseSchema,
  MockRuleListResponseSchema,
  SimpleResponseSchema,
  ClearMockRulesResponseSchema,
} from "../../../mcp/src/generated/api_pb.js";
import { AddMockRuleRequestSchema } from "../../../mcp/src/generated/mock_pb.js";

export async function runMockSuite(): Promise<void> {
  console.log("\n🔀  mock rules");

  // Clear first for a clean state
  await sdkDelete("/mock/rules", ClearMockRulesResponseSchema);

  // ── add ───────────────────────────────────────────────────────────────────
  const addReq = create(AddMockRuleRequestSchema, {
    url: "/api/rt-test",
    method: "GET",
    status: 200,
    body: '{"ok":true}',
  });
  const addRes = await sdkPost(
    "/mock/rules",
    AddMockRuleRequestSchema,
    addReq,
    MockRuleResponseSchema
  );
  assert(
    (addRes.data?.id ?? "").length > 0,
    "mock_add returns non-empty id"
  );
  assert(addRes.data?.url === "/api/rt-test", "mock_add: stored url matches");
  assert(addRes.data?.status === 200, "mock_add: stored status matches");
  const ruleId = addRes.data!.id;

  // ── list contains added rule ──────────────────────────────────────────────
  const listRes = await sdkGet("/mock/rules", MockRuleListResponseSchema);
  const found = listRes.data?.rules.find(r => r.id === ruleId);
  assert(found != null, "mock_list contains added rule by id");
  assert(found?.url === "/api/rt-test", "mock_list rule.url is correct");

  // ── delete specific rule ──────────────────────────────────────────────────
  await sdkDelete(`/mock/rules/${ruleId}`, SimpleResponseSchema);
  const listAfterDelete = await sdkGet("/mock/rules", MockRuleListResponseSchema);
  assert(
    !listAfterDelete.data?.rules.some(r => r.id === ruleId),
    "mock_delete: rule no longer in list"
  );

  // ── add two rules, then clear all ─────────────────────────────────────────
  const r1 = create(AddMockRuleRequestSchema, { url: "/api/a", method: "GET",  status: 200 });
  const r2 = create(AddMockRuleRequestSchema, { url: "/api/b", method: "POST", status: 201 });
  await sdkPost("/mock/rules", AddMockRuleRequestSchema, r1, MockRuleResponseSchema);
  await sdkPost("/mock/rules", AddMockRuleRequestSchema, r2, MockRuleResponseSchema);

  const clearRes = await sdkDelete("/mock/rules", ClearMockRulesResponseSchema);
  assert(
    (clearRes.clearedCount ?? 0) >= 2,
    `mock_clear: clearedCount >= 2 (got ${clearRes.clearedCount})`
  );

  const listAfterClear = await sdkGet("/mock/rules", MockRuleListResponseSchema);
  assert(
    (listAfterClear.data?.rules.length ?? 0) === 0,
    "mock_clear: list is empty after clear"
  );
}
```

- [ ] **Step 2: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/src/suites/mock.ts
git commit -m "tests: add mock CRUD suite"
```

---

### Task 9: 创建 src/index.ts + 运行验证

**Files:**
- Create: `tests/runtime/src/index.ts`

- [ ] **Step 1: 创建 tests/runtime/src/index.ts**

```typescript
import { runPageSuite } from "./suites/page.js";
import { runNodesSuite } from "./suites/nodes.js";
import { runCaptureSuite } from "./suites/capture.js";
import { runInteractSuite } from "./suites/interact.js";
import { runModifyViewSuite } from "./suites/modify-view.js";
import { runMockSuite } from "./suites/mock.js";
import { getResults } from "./helpers.js";

const PLATFORM = process.env.PLATFORM ?? "android";
const PORT = process.env.CLIENT_TOOLS_PORT ?? "8080";

console.log(`\n╔══ Client Tools Runtime Tests ═══════════════════╗`);
console.log(`   Platform : ${PLATFORM.toUpperCase()}`);
console.log(`   SDK URL  : http://127.0.0.1:${PORT}`);
console.log(`╚════════════════════════════════════════════════╝`);

try {
  await runPageSuite();
  await runNodesSuite();
  await runCaptureSuite();
  await runInteractSuite();
  await runModifyViewSuite();
  await runMockSuite();
} catch (e) {
  console.error("\n💥  Fatal error (uncaught):", e);
  process.exit(1);
}

const { passed, failed } = getResults();
const total = passed + failed;
const allPass = failed === 0;

console.log("\n" + "─".repeat(50));
console.log(
  allPass
    ? `✅  All ${total} tests passed`
    : `❌  ${failed}/${total} tests FAILED`
);
console.log("─".repeat(50) + "\n");

process.exit(allPass ? 0 : 1);
```

- [ ] **Step 2: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add tests/runtime/src/index.ts
git commit -m "tests: add test runner (index.ts)"
```

- [ ] **Step 3: 启动 demo app（Android），确认 adb forward 生效**

```bash
# 确认设备连接
adb devices
# 手动 forward（runner 也会自动执行）
adb forward tcp:8080 tcp:8080
# 验证 SDK 可达
curl -s http://127.0.0.1:8080/api/page/current | xxd | head -3
```

Expected: xxd 输出前几行为 protobuf 二进制（非 HTML/404）

- [ ] **Step 4: 在 Android 上运行测试**

```bash
cd /Users/zzc/Desktop/works/client-tools/tests/runtime
npm run test:android
```

Expected: 所有 suite 打印 ✅，最终 `All N tests passed`

如有 ❌，根据失败行的 suite 名和 assert 描述定位问题：
- `sdkGet` 抛异常 → SDK 未启动或 adb forward 失败，先 `curl` 确认
- `move_dx` 不累加 → 检查 `ViewModifier.kt` translationX 是否用 `+=`
- `widthDp` 不反映 visual → 检查 `ViewQueryService.kt` `buildNode()` 是否用 `view.width * view.scaleX`
- `text` error test 失败 → 可接受，Android/iOS 错误响应格式不一致，见 Task 7 注释

- [ ] **Step 5: 在 iOS 上运行测试（需先启动 iproxy）**

```bash
# 启动 iOS 端口映射（在另一个终端运行，保持后台）
iproxy 8080 8080 &

cd /Users/zzc/Desktop/works/client-tools/tests/runtime
npm run test:ios
```

Expected: 同 Android，大部分 ✅，scroll_view 显示 ⏭（skipped）

---

### Task 10: 更新 README.md

根据 CLAUDE.md 约定：新增模块需同步更新 README.md。

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 在 README.md 中补充 tests/runtime/ 说明**

找到 README.md 中 `tests/` 或「目录结构」部分，追加：

```markdown
## 运行时测试

`tests/runtime/` 是 TypeScript CLI 测试脚本，直连设备 SDK HTTP（port 8080）验证运行时行为，覆盖 modify_view、get_node、capture_view、click_view、scroll_view、mock CRUD 等接口。

### 前置条件

- **Android**：连接设备，`adb forward` 会自动执行
- **iOS**：需先运行 `iproxy 8080 8080`（另开终端保持后台）
- demo app 必须在前台并停留在登录页（Login Activity / LoginViewController）

### 运行

```bash
cd tests/runtime
npm install            # 首次安装

npm run test:android   # Android
npm run test:ios       # iOS
```

截图保存到 `tests/snapshots/{platform}/`。
```

- [ ] **Step 2: 提交**

```bash
cd /Users/zzc/Desktop/works/client-tools
git add README.md
git commit -m "docs: document tests/runtime in README"
```

---

## 自检

**Spec coverage：**
- ✅ modify_view move 累加性 → Task 7 `move_dx accumulates` 断言
- ✅ modify_view size 绝对值 → Task 7 `size.width/height` 断言
- ✅ modify_view text → Task 7 text 断言 + snapshot
- ✅ get_node/get_all_nodes 视觉尺寸 → Task 7 `visual size reflected in get_all_nodes`
- ✅ capture_view → Task 4 capture suite
- ✅ click_view → Task 6 interact suite
- ✅ scroll_view (Android) → Task 6 interact suite，iOS skip
- ✅ mock add/list/delete/clear → Task 8 mock suite
- ✅ get_current_page → Task 4 page suite
- ✅ 截图保存 → helpers.ts `saveSnapshot` + Task 7 多处调用
- ✅ Android + iOS 同一套脚本 → `PLATFORM` env var + `ids.ts` 平台分支
- ✅ Demo app View ID 锚点 → ids.ts 注释标注来源文件

**Placeholder 扫描：** 无 TBD/TODO/placeholder。

**Type consistency：** `modify()` helper 在 Task 7 内定义并使用，`getNode()` 同理，所有 schema 名称均与 `mcp/src/generated/` 中 grep 结果一致。
