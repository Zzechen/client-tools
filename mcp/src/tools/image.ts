import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { readFileSync } from "fs";
import { extname } from "path";
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../sdk-client.js";
import {
  PushImageRequestSchema,
  PushImageResponseSchema,
  ShowImageRequestSchema,
  ShowImageResponseSchema,
  ImageListResponseSchema,
} from "../generated/inspector_pb.js";

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
        let imageBytes: Uint8Array;
        let imageExt = ext ?? "png";
        if (file) {
          imageBytes = new Uint8Array(readFileSync(file));
          const e = extname(file).slice(1).toLowerCase();
          imageExt = (e === "jpg" || e === "jpeg") ? "jpg" : "png";
        } else if (image) {
          imageBytes = Uint8Array.from(Buffer.from(image, "base64"));
        } else {
          throw new Error("需要提供 file 或 image 参数");
        }
        const _now = new Date().toISOString();
        const ts = timestamp ?? (_now.slice(5, 10).replace("-", "") + "-" + _now.slice(11, 13) + _now.slice(14, 16));
        const req = create(PushImageRequestSchema, { tag, timestamp: ts, image: imageBytes, ext: imageExt });
        const res = await sdkPost("/inspector/push-image", PushImageRequestSchema, req, PushImageResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ tag: res.data?.tag, filePath: res.data?.filePath, fileSize: Number(res.data?.fileSize ?? 0) }) }] };
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
        const req = create(ShowImageRequestSchema, { tag, timestamp });
        const res = await sdkPost("/inspector/show-image", ShowImageRequestSchema, req, ShowImageResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ tag: res.data?.tag, opacity: res.data?.opacity }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "list_images",
    "返回设备上已保存的图片列表",
    {},
    async () => {
      try {
        const res = await sdkGet("/inspector/images", ImageListResponseSchema);
        const images = (res.data?.images ?? []).map(img => ({ ...img, size: Number(img.size) }));
        return { content: [{ type: "text" as const, text: JSON.stringify(images) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
