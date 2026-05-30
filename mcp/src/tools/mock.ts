import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { readFileSync } from "fs";
import { sdkPost, sdkGet, sdkDelete, platformParam } from "../sdk-client.js";
import {
  MockRuleResponseSchema,
  MockRuleListResponseSchema,
  SimpleResponseSchema,
  ClearMockRulesResponseSchema,
} from "../generated/api_pb.js";
import { AddMockRuleRequestSchema } from "../generated/mock_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerMockTools(server: McpServer): void {
  server.tool(
    "mock_add",
    "从本地 JSON 文件注册一条 HTTP mock 规则，返回生成的规则 id",
    { ...platformParam, file: z.string().describe("规则 JSON 文件的绝对路径") },
    async ({ platform, file }) => {
      try {
        const json = JSON.parse(readFileSync(file, "utf-8"));
        const req = create(AddMockRuleRequestSchema, {
          url: json.url ?? "",
          method: json.method ?? "GET",
          delayMs: BigInt(json.delay_ms ?? 0),
          error: json.error ?? "",
          status: json.status ?? 200,
          headers: json.headers ?? {},
          body: json.body ?? "",
        });
        const res = await sdkPost(platform, "/mock/rules", AddMockRuleRequestSchema, req, MockRuleResponseSchema);
        return {
          content: [{
            type: "text" as const,
            text: JSON.stringify({ id: res.data?.id, url: res.data?.url, method: res.data?.method }),
          }],
        };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "mock_list",
    "列出所有当前生效的 mock 规则",
    { ...platformParam },
    async ({ platform }) => {
      try {
        const res = await sdkGet(platform, "/mock/rules", MockRuleListResponseSchema);
        const rules = (res.data?.rules ?? []).map(r => ({
          id: r.id,
          url: r.url,
          method: r.method,
          delayMs: Number(r.delayMs),
          error: r.error,
          status: r.status,
          headers: r.headers,
          body: r.body,
        }));
        return { content: [{ type: "text" as const, text: JSON.stringify(rules) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "mock_delete",
    "按 id 删除一条 mock 规则",
    { ...platformParam, id: z.string().describe("规则 id，由 mock_add 返回") },
    async ({ platform, id }) => {
      try {
        await sdkDelete(platform, `/mock/rules/${id}`, SimpleResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ success: true }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "mock_clear",
    "清空所有 mock 规则",
    { ...platformParam },
    async ({ platform }) => {
      try {
        const res = await sdkDelete(platform, "/mock/rules", ClearMockRulesResponseSchema);
        return {
          content: [{
            type: "text" as const,
            text: JSON.stringify({ cleared_count: Number(res.clearedCount) }),
          }],
        };
      } catch (e) { return errResult(e); }
    }
  );
}
