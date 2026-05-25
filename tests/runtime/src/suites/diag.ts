import { fromBinary, toBinary, create } from "@bufbuild/protobuf";
import { NodeResponseSchema, ModifyResponseSchema } from "../../../../mcp/src/generated/api_pb.js";
import { ModifyViewRequestSchema } from "../../../../mcp/src/generated/modify_pb.js";
import { execSync } from "child_process";

execSync("adb forward tcp:8080 tcp:8080", { stdio: "ignore" });
const BASE = "http://127.0.0.1:8080";

async function getNode(id: string) {
  const r = await fetch(`${BASE}/api/nodes/${encodeURIComponent(id)}`);
  const status = r.status;
  const b = new Uint8Array(await r.arrayBuffer());
  console.log(`  getNode(${id}): HTTP ${status}, bytes=${b.length}`);
  return fromBinary(NodeResponseSchema, b).data;
}

async function modifyView(opts: { id: string; move_dx?: number; width?: number }) {
  const req = create(ModifyViewRequestSchema, {
    id: opts.id,
    ...(opts.move_dx != null && { move: { dx: opts.move_dx } }),
    ...(opts.width  != null && { size: { width: opts.width } }),
  });
  const body = toBinary(ModifyViewRequestSchema, req);
  console.log(`  modify ${JSON.stringify(opts)}: body=${body.length}B`);
  const r = await fetch(`${BASE}/api/modify`, {
    method: "POST", headers: { "Content-Type": "application/x-protobuf" }, body,
  });
  const rb = new Uint8Array(await r.arrayBuffer());
  console.log(`  modify HTTP ${r.status}, resp bytes=${rb.length}`);
  try {
    const m = fromBinary(ModifyResponseSchema, rb);
    console.log(`  modify response ok, message="${m.message}"`);
  } catch {
    console.log(`  modify response parse failed (raw): ${Buffer.from(rb).toString("utf8").substring(0, 80)}`);
  }
  await new Promise(r => setTimeout(r, 300));
}

console.log("=== Step 1: baseline ===");
const base = await getNode("login_btn_submit");
console.log(`  baseline: screenX=${base?.screenX}, widthDp=${base?.widthDp}`);

console.log("\n=== Step 2: move_dx +20 ===");
await modifyView({ id: "login_btn_submit", move_dx: 20 });
const after1 = await getNode("login_btn_submit");
console.log(`  after: screenX=${after1?.screenX}, delta=${(after1?.screenX??0)-(base?.screenX??0)}`);

console.log("\n=== Step 3: size.width=200 ===");
await modifyView({ id: "login_btn_submit", width: 200 });
console.log("  calling getNode after width=200...");
try {
  const after2 = await getNode("login_btn_submit");
  console.log(`  after: widthDp=${after2?.widthDp}`);
} catch (e) {
  console.log(`  FAILED: ${e}`);
}
