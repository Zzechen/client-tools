import { sdkGet } from "../client.js";
import { assert, saveSnapshot } from "../helpers.js";
import { CaptureResponseSchema } from "../../../../mcp/src/generated/api_pb.js";
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
