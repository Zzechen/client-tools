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
  server.tool("get_current_page", "查询当前页面名称（Android/iOS 通用）", {}, async () => {
    try {
      const res = await sdkGet("/api/page/current", PageResponseSchema);
      return { content: [{ type: "text" as const, text: JSON.stringify({ pageName: res.data?.pageName, timestamp: res.data?.timestamp }) }] };
    } catch (e) { return errResult(e); }
  });

  server.tool(
    "click_view",
    "点击指定 id 的 View（Android/iOS 通用）。默认点击 view 中心，可通过 centerOffsetX/Y 偏移触点",
    {
      id: z.string().describe("View 的 id（Android resource id 不含包名前缀，iOS 为 accessibilityIdentifier）"),
      centerOffsetX: z.number().optional().describe("触点相对 view 中心的横向偏移 dp，正右，默认 0"),
      centerOffsetY: z.number().optional().describe("触点相对 view 中心的纵向偏移 dp，正下，默认 0"),
      index: z.number().int().min(0).optional().describe("同 id 有多个匹配时点第 N 个（0-based），缺省点第 0 个"),
    },
    async ({ id, centerOffsetX, centerOffsetY, index }) => {
      try {
        const req = create(ClickRequestSchema, {
          id,
          ...(centerOffsetX !== undefined && { centerOffsetX }),
          ...(centerOffsetY !== undefined && { centerOffsetY }),
          ...(index !== undefined && { index }),
        });
        const res = await sdkPost("/api/click", ClickRequestSchema, req, ClickResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ id: res.data?.id }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "scroll_view",
    "滚动指定 id 的 View，单位 dp（Android/iOS 通用）",
    {
      id: z.string().describe("View 的 id（Android resource id 不含包名前缀，iOS 为 accessibilityIdentifier）"),
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
