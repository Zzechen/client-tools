import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost, sdkDelete } from "../client.js";
import { assert } from "../helpers.js";
import {
  MockRuleResponseSchema,
  MockRuleListResponseSchema,
  SimpleResponseSchema,
  ClearMockRulesResponseSchema,
} from "../../../../mcp/src/generated/api_pb.js";
import { AddMockRuleRequestSchema } from "../../../../mcp/src/generated/mock_pb.js";

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
