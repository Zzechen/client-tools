import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGet, sdkPost } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

const DpValue = z.union([z.number(), z.literal("wrap_content")]);

const ViewPropsSchema = z.object({
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
}).describe("View 布局属性，margin/padding 为差值（dp），width/height 为绝对值（dp）或 \"wrap_content\"；letterSpacingEm 为字间距（em 单位），lineSpacingExtraDp 为额外行间距（dp），includeFontPadding 控制字体内置 padding");

async function fetchImageBase64(url: string): Promise<string> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const buf = await res.arrayBuffer();
  return Buffer.from(buf).toString("base64");
}

export function registerViewTools(server: McpServer): void {
  server.tool(
    "capture_view",
    "截取指定 Android View 的截图，返回 PNG 图片供视觉分析",
    {
      id: z.string().describe("Android View 的 resource id"),
    },
    async ({ id }) => {
      try {
        const base64 = await fetchImageBase64(`http://localhost:8080/api/capture/${encodeURIComponent(id)}`);
        return {
          content: [{ type: "image" as const, data: base64, mimeType: "image/png" }],
        };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_node",
    "查询 Android 原生 View 节点的屏幕位置和尺寸",
    {
      id: z.string().describe("Android View 的 resource id（不含包名前缀，如 btn_login）"),
    },
    async ({ id }) => {
      try {
        const result = await sdkGet(`/api/nodes/${encodeURIComponent(id)}`);
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_all_nodes",
    "获取当前页面所有 Android View 节点的屏幕坐标和尺寸快照",
    {},
    async () => {
      try {
        const result = await sdkGet("/api/nodes/all");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "modify_view",
    "修改 Android View 的布局属性（margin/padding/size），单位 dp；TextView 额外支持 letterSpacingEm、lineSpacingExtraDp、includeFontPadding",
    {
      id: z.string().describe("Android View 的 resource id"),
      props: ViewPropsSchema,
    },
    async ({ id, props }) => {
      try {
        const propsToSend = {
          ...props,
          widthDp: props.widthDp !== undefined ? String(props.widthDp) : undefined,
          heightDp: props.heightDp !== undefined ? String(props.heightDp) : undefined,
        };
        const result = await sdkPost("/api/modify", { id, props: propsToSend });
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
