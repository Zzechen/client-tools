import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../client.js";
import { assert, sleep } from "../helpers.js";
import { ClickResponseSchema, ScrollResponseSchema, NodeResponseSchema } from "../../../../mcp/src/generated/api_pb.js";
import { ClickRequestSchema, ScrollRequestSchema } from "../../../../mcp/src/generated/modify_pb.js";
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
