import { sdkGet } from "../client.js";
import { assert } from "../helpers.js";
import { PageResponseSchema } from "../../../../mcp/src/generated/api_pb.js";

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
