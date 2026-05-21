import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../client.js";
import { assert, assertApprox, saveSnapshot, sleep } from "../helpers.js";
import {
  NodeResponseSchema,
  NodeListResponseSchema,
  CaptureResponseSchema,
  ModifyResponseSchema,
} from "../../../../mcp/src/generated/api_pb.js";
import { ModifyViewRequestSchema } from "../../../../mcp/src/generated/modify_pb.js";
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
