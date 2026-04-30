import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkGet, sdkPost } from "../sdk-client.js";
import { writeFileSync, mkdirSync } from "fs";
import { join } from "path";
import {
  NodeListResponseSchema,
  NodeResponseSchema,
  ModifyResponseSchema,
  CaptureResponseSchema,
} from "../generated/api_pb.js";
import { ModifyViewRequestSchema, ModifyViewIosRequestSchema } from "../generated/modify_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerViewTools(server: McpServer): void {
  server.tool(
    "capture_view",
    "截取指定 View 的截图，返回 PNG 图片供视觉分析",
    {
      id: z.string().describe("View 的 id"),
      save_dir: z.string().optional().describe("若提供，将截图保存到该目录，文件名为 {id}_{timestamp}.png"),
    },
    async ({ id, save_dir }) => {
      try {
        const res = await sdkGet(`/api/capture/${encodeURIComponent(id)}`, CaptureResponseSchema);
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
    { id: z.string().describe("View 的 id（Android resource id 不含包名前缀，iOS 为 accessibilityIdentifier）") },
    async ({ id }) => {
      try {
        const res = await sdkGet(`/api/nodes/${encodeURIComponent(id)}`, NodeResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_all_nodes",
    "获取当前页面所有原生 View 节点的屏幕坐标和尺寸快照（Android/iOS 通用）",
    {},
    async () => {
      try {
        const res = await sdkGet("/api/nodes/all", NodeListResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data?.nodes) }] };
      } catch (e) { return errResult(e); }
    }
  );

  // ===== modify_view_android =====
  const AndroidViewPropsZod = z.object({
    marginTopDiffDp: z.number().optional(),
    marginBottomDiffDp: z.number().optional(),
    marginLeftDiffDp: z.number().optional(),
    marginRightDiffDp: z.number().optional(),
    paddingTopDiffDp: z.number().optional(),
    paddingBottomDiffDp: z.number().optional(),
    paddingLeftDiffDp: z.number().optional(),
    paddingRightDiffDp: z.number().optional(),
    widthDp: z.union([z.number(), z.literal("wrap_content")]).optional(),
    heightDp: z.union([z.number(), z.literal("wrap_content")]).optional(),
    letterSpacingEm: z.number().optional(),
    lineSpacingExtraDp: z.number().optional(),
    includeFontPadding: z.boolean().optional(),
  }).describe("Android View 布局属性，margin/padding 为差值（dp），width/height 为绝对值（dp）或 \"wrap_content\"");

  server.tool(
    "modify_view_android",
    "修改 Android View 的布局属性（margin/padding/size），单位 dp；TextView 额外支持 letterSpacingEm、lineSpacingExtraDp、includeFontPadding",
    { id: z.string().describe("Android View 的 resource id"), props: AndroidViewPropsZod },
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

  // ===== modify_view_ios =====
  const IosTextPropsZod = z.object({
    content: z.string().optional().describe("替换 UILabel 文案内容"),
    letterSpacingEm: z.number().optional().describe("字间距，单位 em"),
    lineSpacingExtraDp: z.number().optional().describe("额外行间距，单位 dp"),
  }).describe("文字属性（传此对象则断言 view 为 UILabel，否则整个请求失败）");

  const IosViewPropsZod = z.object({
    translateXDp: z.number().optional().describe("X 轴位移绝对值（dp），屏幕空间，不受 scale 影响"),
    translateYDp: z.number().optional().describe("Y 轴位移绝对值（dp），屏幕空间，不受 scale 影响"),
    scaleX: z.number().optional().describe("X 轴缩放绝对值，1.0 为原始大小"),
    scaleY: z.number().optional().describe("Y 轴缩放绝对值，1.0 为原始大小"),
    widthDp: z.number().optional().describe("宽度绝对值（dp）"),
    heightDp: z.number().optional().describe("高度绝对值（dp）"),
    paddingTopDiffDp: z.number().optional(),
    paddingBottomDiffDp: z.number().optional(),
    paddingLeftDiffDp: z.number().optional(),
    paddingRightDiffDp: z.number().optional(),
    text: IosTextPropsZod.optional(),
  }).describe("iOS View 属性");

  server.tool(
    "modify_view_ios",
    "修改 iOS UIView 的 transform（位移/缩放）、尺寸、padding；传 text 字段则断言为 UILabel 并修改文字属性",
    { id: z.string().describe("iOS View 的 accessibilityIdentifier"), props: IosViewPropsZod },
    async ({ id, props }) => {
      try {
        const textProps = props.text ? {
          ...(props.text.content !== undefined && { content: props.text.content }),
          ...(props.text.letterSpacingEm !== undefined && { letterSpacingEm: props.text.letterSpacingEm }),
          ...(props.text.lineSpacingExtraDp !== undefined && { lineSpacingExtraDp: props.text.lineSpacingExtraDp }),
        } : undefined;

        const iosProps = {
          ...(props.translateXDp !== undefined && { translateXDp: props.translateXDp }),
          ...(props.translateYDp !== undefined && { translateYDp: props.translateYDp }),
          ...(props.scaleX !== undefined && { scaleX: props.scaleX }),
          ...(props.scaleY !== undefined && { scaleY: props.scaleY }),
          ...(props.widthDp !== undefined && { widthDp: props.widthDp }),
          ...(props.heightDp !== undefined && { heightDp: props.heightDp }),
          ...(props.paddingTopDiffDp !== undefined && { paddingTopDiffDp: props.paddingTopDiffDp }),
          ...(props.paddingBottomDiffDp !== undefined && { paddingBottomDiffDp: props.paddingBottomDiffDp }),
          ...(props.paddingLeftDiffDp !== undefined && { paddingLeftDiffDp: props.paddingLeftDiffDp }),
          ...(props.paddingRightDiffDp !== undefined && { paddingRightDiffDp: props.paddingRightDiffDp }),
          ...(textProps && { text: textProps }),
        };
        const req = create(ModifyViewIosRequestSchema, { id, props: iosProps });
        const res = await sdkPost("/api/modify/ios", ModifyViewIosRequestSchema, req, ModifyResponseSchema);
        const msg = res.message ? res.message : "ok";
        return { content: [{ type: "text" as const, text: msg }] };
      } catch (e) { return errResult(e); }
    }
  );
}
