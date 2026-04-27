import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../sdk-client.js";
import {
  PageResponseSchema,
  ClickResponseSchema,
  ScrollResponseSchema,
} from "../generated/api_pb.js";
import { ClickRequestSchema, ScrollRequestSchema } from "../generated/modify_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerPageTools(server: McpServer): void {
  server.tool("get_current_page", "查询当前 Android 页面名称", {}, async () => {
    try {
      const res = await sdkGet("/api/page/current", PageResponseSchema);
      return { content: [{ type: "text" as const, text: JSON.stringify({ pageName: res.data?.pageName, timestamp: res.data?.timestamp }) }] };
    } catch (e) { return errResult(e); }
  });

  server.tool("click_view", "点击指定 id 的 Android View", { id: z.string() }, async ({ id }) => {
    try {
      const req = create(ClickRequestSchema, { id });
      const res = await sdkPost("/api/click", ClickRequestSchema, req, ClickResponseSchema);
      return { content: [{ type: "text" as const, text: JSON.stringify({ id: res.data?.id }) }] };
    } catch (e) { return errResult(e); }
  });

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
        const req = create(ScrollRequestSchema, { id, dx, dy });
        const res = await sdkPost("/api/scroll", ScrollRequestSchema, req, ScrollResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ id: res.data?.id, dx: res.data?.dx, dy: res.data?.dy }) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
