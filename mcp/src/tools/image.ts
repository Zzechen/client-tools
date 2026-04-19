import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkPost } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerImageTools(server: McpServer): void {
  server.tool(
    "push_image",
    "推送 base64 编码图片到设备叠加层并自动显示",
    {
      tag: z.string().describe("图片标识，如 login、home"),
      image: z.string().describe("base64 编码的图片内容"),
      ext: z.enum(["png", "jpg"]).optional().describe("图片格式，缺省 png"),
      timestamp: z.string().optional().describe("时间戳，格式 MMdd-HHmm，缺省自动生成"),
    },
    async ({ tag, image, ext, timestamp }) => {
      try {
        const result = await sdkPost("/inspector/push-image", { tag, image, ext, timestamp });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "show_image",
    "切换显示设备上已保存的图片",
    {
      tag: z.string().describe("图片标识"),
      timestamp: z.string().describe("时间戳，格式 MMdd-HHmm"),
    },
    async ({ tag, timestamp }) => {
      try {
        const result = await sdkPost("/inspector/show-image", { tag, timestamp });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
