import { sdkGet } from "../client.js";
import { assert, assertApprox } from "../helpers.js";
import { NodeResponseSchema, NodeListResponseSchema } from "../../../../mcp/src/generated/api_pb.js";
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
