import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost, platformParam } from "../sdk-client.js";
import { writeFileSync, mkdirSync } from "fs";
import { join } from "path";
import {
  NodeListResponseSchema,
  NodeResponseSchema,
  ModifyResponseSchema,
  CaptureResponseSchema,
} from "../generated/api_pb.js";
import { ModifyViewRequestSchema } from "../generated/modify_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerViewTools(server: McpServer): void {
  server.tool(
    "capture_view",
    "截取指定 View 的截图，返回 PNG 图片供视觉分析（Android/iOS 通用）",
    {
      ...platformParam,
      id: z.string().describe("View 的 id"),
      save_dir: z.string().optional().describe("若提供，将截图保存到该目录，文件名为 {id}_{timestamp}.png"),
    },
    async ({ platform, id, save_dir }) => {
      try {
        const res = await sdkGet(platform, `/api/capture/${encodeURIComponent(id)}`, CaptureResponseSchema);
        const base64 = Buffer.from(res.imagePng).toString("base64");
        if (save_dir) {
          mkdirSync(save_dir, { recursive: true });
          const filepath = join(save_dir, `${id}_${Date.now()}.png`);
          writeFileSync(filepath, Buffer.from(res.imagePng));
        }
        return { content: [{ type: "image" as const, data: base64, mimeType: "image/png" }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_node",
    "查询原生 View 节点的屏幕位置和尺寸（Android/iOS 通用）",
    {
      ...platformParam,
      id: z.string().describe("View 的 id（Android resource id 不含包名前缀，iOS 为 accessibilityIdentifier）"),
    },
    async ({ platform, id }) => {
      try {
        const res = await sdkGet(platform, `/api/nodes/${encodeURIComponent(id)}`, NodeResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_all_nodes",
    "获取当前页面所有原生 View 节点的屏幕坐标和尺寸快照（Android/iOS 通用）",
    { ...platformParam },
    async ({ platform }) => {
      try {
        const res = await sdkGet(platform, "/api/nodes/all", NodeListResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data?.nodes) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "modify_view",
    "修改 View 的位置、尺寸或文案（Android/iOS 通用）。move_dx/move_dy 为增量偏移（dp），width/height 为目标尺寸绝对值（dp），text 替换文案",
    {
      ...platformParam,
      id:      z.string().describe("View 的 id（Android resource id 不含包名前缀，iOS 为 accessibilityIdentifier）"),
      move_dx: z.number().optional().describe("横向偏移增量（dp），正右"),
      move_dy: z.number().optional().describe("纵向偏移增量（dp），正下"),
      width:   z.number().optional().describe("目标宽度（dp），绝对值"),
      height:  z.number().optional().describe("目标高度（dp），绝对值"),
      text:    z.string().optional().describe("替换文案内容（要求 view 为 TextView/UILabel/UITextField）"),
    },
    async ({ platform, id, move_dx, move_dy, width, height, text }) => {
      try {
        const req = create(ModifyViewRequestSchema, {
          id,
          ...((move_dx !== undefined || move_dy !== undefined) && { move: {
            ...(move_dx !== undefined && { dx: move_dx }),
            ...(move_dy !== undefined && { dy: move_dy }),
          }}),
          ...((width !== undefined || height !== undefined) && { size: {
            ...(width  !== undefined && { width  }),
            ...(height !== undefined && { height }),
          }}),
          ...(text !== undefined && { text: { content: text } }),
        });
        const res = await sdkPost(platform, "/api/modify", ModifyViewRequestSchema, req, ModifyResponseSchema);
        const msg = res.message ? res.message : "ok";
        return { content: [{ type: "text" as const, text: msg }] };
      } catch (e) { return errResult(e); }
    }
  );
}
