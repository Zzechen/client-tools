import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGet, sdkPost } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerPageTools(server: McpServer): void {
  server.tool(
    "get_current_page",
    "查询当前 Android 页面名称",
    {},
    async () => {
      try {
        const result = await sdkGet("/api/page/current");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "click_view",
    "点击指定 id 的 Android View",
    { id: z.string() },
    async ({ id }) => {
      try {
        const result = await sdkPost("/api/click", { id });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "scroll_view",
    "滚动指定 id 的 Android View，单位 dp",
    {
      id: z.string(),
      dx: z.number().describe("横向滚动量，dp，正值向左滚"),
      dy: z.number().describe("竖向滚动量，dp，正值向上滚"),
    },
    async ({ id, dx, dy }) => {
      try {
        const result = await sdkPost("/api/scroll", { id, dx, dy });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
