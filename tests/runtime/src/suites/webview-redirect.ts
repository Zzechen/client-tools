import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost, sdkDelete } from "../client.js";
import { assert } from "../helpers.js";
import {
  WebViewRedirectResponseSchema,
  WebViewRedirectListResponseSchema,
  SimpleResponseSchema,
  ClearWebViewRedirectsResponseSchema,
} from "../../../../mcp/src/generated/api_pb.js";
import { AddWebViewRedirectRequestSchema } from "../../../../mcp/src/generated/webview_redirect_pb.js";

export async function runWebViewRedirectSuite(): Promise<void> {
  console.log("\n↩️   webview redirects");

  // Clean state
  await sdkDelete("/webview/redirects", ClearWebViewRedirectsResponseSchema);

  // ── add ───────────────────────────────────────────────────────────────────
  const addReq = create(AddWebViewRedirectRequestSchema, {
    urlPattern: "https://example\\.com/page",
    targetUrl: "http://192.168.1.1:3000/page",
  });
  const addRes = await sdkPost(
    "/webview/redirects",
    AddWebViewRedirectRequestSchema,
    addReq,
    WebViewRedirectResponseSchema
  );
  assert((addRes.data?.id ?? "").length > 0, "webview_redirect_add returns non-empty id");
  assert(addRes.data?.urlPattern === "https://example\\.com/page", "add: urlPattern stored correctly");
  assert(addRes.data?.targetUrl === "http://192.168.1.1:3000/page", "add: targetUrl stored correctly");
  const ruleId = addRes.data!.id;

  // ── list contains added rule ──────────────────────────────────────────────
  const listRes = await sdkGet("/webview/redirects", WebViewRedirectListResponseSchema);
  const found = listRes.data?.rules.find(r => r.id === ruleId);
  assert(found != null, "list contains added rule by id");
  assert(found?.urlPattern === "https://example\\.com/page", "list rule.urlPattern correct");

  // ── delete specific rule ──────────────────────────────────────────────────
  await sdkDelete(`/webview/redirects/${ruleId}`, SimpleResponseSchema);
  const listAfterDelete = await sdkGet("/webview/redirects", WebViewRedirectListResponseSchema);
  assert(
    !listAfterDelete.data?.rules.some(r => r.id === ruleId),
    "delete: rule no longer in list"
  );

  // ── add two rules then clear all ──────────────────────────────────────────
  const r1 = create(AddWebViewRedirectRequestSchema, { urlPattern: "example\\.com/a", targetUrl: "http://local/a" });
  const r2 = create(AddWebViewRedirectRequestSchema, { urlPattern: "example\\.com/b", targetUrl: "http://local/b" });
  await sdkPost("/webview/redirects", AddWebViewRedirectRequestSchema, r1, WebViewRedirectResponseSchema);
  await sdkPost("/webview/redirects", AddWebViewRedirectRequestSchema, r2, WebViewRedirectResponseSchema);

  const clearRes = await sdkDelete("/webview/redirects", ClearWebViewRedirectsResponseSchema);
  assert((clearRes.clearedCount ?? 0) >= 2, `clear: clearedCount >= 2 (got ${clearRes.clearedCount})`);

  const listAfterClear = await sdkGet("/webview/redirects", WebViewRedirectListResponseSchema);
  assert((listAfterClear.data?.rules.length ?? 0) === 0, "clear: list is empty after clear");
}
