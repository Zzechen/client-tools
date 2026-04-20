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
}).describe("View 布局属性，margin/padding 为差值（dp），width/height 为绝对值（dp）或 \"wrap_content\"");

export function registerViewTools(server: McpServer): void {
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
    "修改 Android View 的布局属性（margin/padding/size），单位 dp",
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
