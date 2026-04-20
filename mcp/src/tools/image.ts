import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { readFileSync } from "fs";
import { extname } from "path";
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
    "推送图片到设备叠加层并自动显示。优先使用 file 参数（本地绝对路径），其次 image base64 字符串",
    {
      tag: z.string().describe("图片标识，如 login、home"),
      file: z.string().optional().describe("本地图片文件的绝对路径（png/jpg），优先于 image 参数"),
      image: z.string().optional().describe("base64 编码的图片内容"),
      ext: z.enum(["png", "jpg"]).optional().describe("图片格式，缺省 png；使用 file 时自动推断"),
      timestamp: z.string().optional().describe("时间戳，格式 MMdd-HHmm，缺省自动生成"),
    },
    async ({ tag, file, image, ext, timestamp }) => {
      try {
        let imageData = image;
        let imageExt = ext ?? "png";
        if (file) {
          imageData = readFileSync(file).toString("base64");
          const e = extname(file).slice(1).toLowerCase();
          if (e === "jpg" || e === "jpeg") imageExt = "jpg";
          else imageExt = "png";
        }
        if (!imageData) throw new Error("需要提供 file 或 image 参数");
        const result = await sdkPost("/inspector/push-image", { tag, image: imageData, ext: imageExt, timestamp });
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
