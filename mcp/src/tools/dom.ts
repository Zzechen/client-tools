import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGetRaw } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerDomTools(server: McpServer): void {
  server.tool(
    "dom_all",
    "返回 WebView 中所有 DOM 节点，坐标为屏幕绝对坐标（含 WebView 偏移换算）",
    {},
    async () => {
      try {
        const result = await sdkGetRaw("/dom/all");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "dom_by_id",
    "按 id 查询 WebView 中单个 DOM 节点的屏幕坐标和尺寸",
    { id: z.string().describe("DOM 元素的 id 属性值") },
    async ({ id }) => {
      try {
        const result = await sdkGetRaw(`/dom/${encodeURIComponent(id)}`);
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
