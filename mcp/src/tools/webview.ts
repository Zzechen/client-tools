import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkPost } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerWebviewTools(server: McpServer): void {
  server.tool(
    "push_html",
    "推送 HTML 内容到设备 WebView 叠加层并自动显示",
    {
      tag: z.string().describe("页面标识，如 login、home"),
      html: z.string().describe("完整 HTML 内容"),
      timestamp: z.string().optional().describe("时间戳，格式 MMdd-HHmm，缺省自动生成"),
    },
    async ({ tag, html, timestamp }) => {
      try {
        const result = await sdkPost("/webview/push-html", { tag, html, timestamp });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "show_webview",
    "切换显示设备上已保存的 HTML 文件",
    {
      tag: z.string().describe("页面标识"),
      timestamp: z.string().describe("时间戳，格式 MMdd-HHmm"),
    },
    async ({ tag, timestamp }) => {
      try {
        const result = await sdkPost("/webview/show", { tag, timestamp });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "hide_overlay",
    "隐藏 WebView 或图片叠加层",
    {
      type: z.enum(["webview", "image"]).optional().describe("缺省隐藏当前 activeTab"),
    },
    async ({ type }) => {
      try {
        const result = await sdkPost("/inspector/hide", type ? { type } : {});
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "adjust_overlay",
    "调整叠加层偏移量（增量 dp）和透明度（绝对值 0~1）",
    {
      type: z.enum(["webview", "image"]).optional().describe("缺省操作当前 activeTab"),
      offsetX: z.number().optional().describe("X 轴偏移增量，单位 dp"),
      offsetY: z.number().optional().describe("Y 轴偏移增量，单位 dp"),
      opacity: z.number().min(0).max(1).optional().describe("透明度绝对值 0.0~1.0"),
    },
    async ({ type, offsetX, offsetY, opacity }) => {
      try {
        const result = await sdkPost("/inspector/adjust", { type, offsetX, offsetY, opacity });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
