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
import { ModifyViewAndroidRequestSchema, ModifyViewIosRequestSchema } from "../generated/modify_pb.js";

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
  const AndroidMarginPropsZod = z.object({
    topDiffDp:    z.number().optional(),
    bottomDiffDp: z.number().optional(),
    leftDiffDp:   z.number().optional(),
    rightDiffDp:  z.number().optional(),
  }).describe("margin 增量调整（dp）");

  const AndroidPaddingPropsZod = z.object({
    topDiffDp:    z.number().optional(),
    bottomDiffDp: z.number().optional(),
    leftDiffDp:   z.number().optional(),
    rightDiffDp:  z.number().optional(),
  }).describe("padding 增量调整（dp）");

  const AndroidSizePropsZod = z.object({
    width:  z.union([z.number(), z.literal("wrap_content")]).optional(),
    height: z.union([z.number(), z.literal("wrap_content")]).optional(),
  }).describe("尺寸设置（dp 数值或 wrap_content）");

  const AndroidTextPropsZod = z.object({
    letterSpacingEm:    z.number().optional(),
    lineSpacingExtraDp: z.number().optional(),
    includeFontPadding: z.boolean().optional(),
  }).describe("文字属性（传此对象则断言 view 为 TextView 或其子类，否则整个请求失败）");

  server.tool(
    "modify_view_android",
    "修改 Android View 的布局属性。参数按功能分组：margin/padding 为增量（dp），size 为绝对值；传 text 组则断言 view 为 TextView 子类，否则整体拒绝",
    {
      id:      z.string().describe("Android View 的 resource id（不含 @id/ 前缀）"),
      margin:  AndroidMarginPropsZod.optional(),
      padding: AndroidPaddingPropsZod.optional(),
      size:    AndroidSizePropsZod.optional(),
      text:    AndroidTextPropsZod.optional(),
    },
    async ({ id, margin, padding, size, text }) => {
      try {
        const androidProps = {
          ...(margin && { margin: {
            ...(margin.topDiffDp    !== undefined && { topDiffDp:    margin.topDiffDp }),
            ...(margin.bottomDiffDp !== undefined && { bottomDiffDp: margin.bottomDiffDp }),
            ...(margin.leftDiffDp   !== undefined && { leftDiffDp:   margin.leftDiffDp }),
            ...(margin.rightDiffDp  !== undefined && { rightDiffDp:  margin.rightDiffDp }),
          }}),
          ...(padding && { padding: {
            ...(padding.topDiffDp    !== undefined && { topDiffDp:    padding.topDiffDp }),
            ...(padding.bottomDiffDp !== undefined && { bottomDiffDp: padding.bottomDiffDp }),
            ...(padding.leftDiffDp   !== undefined && { leftDiffDp:   padding.leftDiffDp }),
            ...(padding.rightDiffDp  !== undefined && { rightDiffDp:  padding.rightDiffDp }),
          }}),
          ...(size && { size: {
            ...(size.width  !== undefined && (typeof size.width  === "number"
              ? { widthDp:  size.width  } : { widthWrapContent:  true })),
            ...(size.height !== undefined && (typeof size.height === "number"
              ? { heightDp: size.height } : { heightWrapContent: true })),
          }}),
          ...(text && { text: {
            ...(text.letterSpacingEm    !== undefined && { letterSpacingEm:    text.letterSpacingEm }),
            ...(text.lineSpacingExtraDp !== undefined && { lineSpacingExtraDp: text.lineSpacingExtraDp }),
            ...(text.includeFontPadding !== undefined && { includeFontPadding: text.includeFontPadding }),
          }}),
        };
        const req = create(ModifyViewAndroidRequestSchema, { id, props: androidProps });
        const res = await sdkPost("/api/modify/android", ModifyViewAndroidRequestSchema, req, ModifyResponseSchema);
        const msg = res.message ? res.message : "ok";
        return { content: [{ type: "text" as const, text: msg }] };
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
