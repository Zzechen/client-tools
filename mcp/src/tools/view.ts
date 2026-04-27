import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../sdk-client.js";
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

const DpValue = z.union([z.number(), z.literal("wrap_content")]);

const ViewPropsZod = z.object({
  marginTopDiffDp: z.number().optional(),
  marginBottomDiffDp: z.number().optional(),
  marginLeftDiffDp: z.number().optional(),
  marginRightDiffDp: z.number().optional(),
  paddingTopDiffDp: z.number().optional(),
  paddingBottomDiffDp: z.number().optional(),
  paddingLeftDiffDp: z.number().optional(),
  paddingRightDiffDp: z.number().optional(),
  widthDp: DpValue.optional(),
  heightDp: DpValue.optional(),
  letterSpacingEm: z.number().optional(),
  lineSpacingExtraDp: z.number().optional(),
  includeFontPadding: z.boolean().optional(),
}).describe("View 布局属性，margin/padding 为差值（dp），width/height 为绝对值（dp）或 \"wrap_content\"");

export function registerViewTools(server: McpServer): void {
  server.tool(
    "capture_view",
    "截取指定 Android View 的截图，返回 PNG 图片供视觉分析",
    { id: z.string().describe("Android View 的 resource id") },
    async ({ id }) => {
      try {
        const res = await sdkGet(`/api/capture/${encodeURIComponent(id)}`, CaptureResponseSchema);
        const base64 = Buffer.from(res.imagePng).toString("base64");
        return { content: [{ type: "image" as const, data: base64, mimeType: "image/png" }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_node",
    "查询 Android 原生 View 节点的屏幕位置和尺寸",
    { id: z.string().describe("Android View 的 resource id（不含包名前缀，如 btn_login）") },
    async ({ id }) => {
      try {
        const res = await sdkGet(`/api/nodes/${encodeURIComponent(id)}`, NodeResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_all_nodes",
    "获取当前页面所有 Android View 节点的屏幕坐标和尺寸快照",
    {},
    async () => {
      try {
        const res = await sdkGet("/api/nodes/all", NodeListResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data?.nodes) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "modify_view",
    "修改 Android View 的布局属性（margin/padding/size），单位 dp；TextView 额外支持 letterSpacingEm、lineSpacingExtraDp、includeFontPadding",
    { id: z.string().describe("Android View 的 resource id"), props: ViewPropsZod },
    async ({ id, props }) => {
      try {
        const viewProps = {
          ...(props.marginTopDiffDp !== undefined && { marginTopDiffDp: props.marginTopDiffDp }),
          ...(props.marginBottomDiffDp !== undefined && { marginBottomDiffDp: props.marginBottomDiffDp }),
          ...(props.marginLeftDiffDp !== undefined && { marginLeftDiffDp: props.marginLeftDiffDp }),
          ...(props.marginRightDiffDp !== undefined && { marginRightDiffDp: props.marginRightDiffDp }),
          ...(props.paddingTopDiffDp !== undefined && { paddingTopDiffDp: props.paddingTopDiffDp }),
          ...(props.paddingBottomDiffDp !== undefined && { paddingBottomDiffDp: props.paddingBottomDiffDp }),
          ...(props.paddingLeftDiffDp !== undefined && { paddingLeftDiffDp: props.paddingLeftDiffDp }),
          ...(props.paddingRightDiffDp !== undefined && { paddingRightDiffDp: props.paddingRightDiffDp }),
          ...(props.widthDp !== undefined && { widthDp: String(props.widthDp) }),
          ...(props.heightDp !== undefined && { heightDp: String(props.heightDp) }),
          ...(props.letterSpacingEm !== undefined && { letterSpacingEm: props.letterSpacingEm }),
          ...(props.lineSpacingExtraDp !== undefined && { lineSpacingExtraDp: props.lineSpacingExtraDp }),
          ...(props.includeFontPadding !== undefined && { includeFontPadding: props.includeFontPadding }),
        };
        const req = create(ModifyViewRequestSchema, { id, props: viewProps });
        await sdkPost("/api/modify", ModifyViewRequestSchema, req, ModifyResponseSchema);
        return { content: [{ type: "text" as const, text: "ok" }] };
      } catch (e) { return errResult(e); }
    }
  );
}
