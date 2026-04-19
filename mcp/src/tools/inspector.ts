import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { sdkGet } from "../sdk-client.js";
import { eventMonitor } from "../event-monitor.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerInspectorTools(server: McpServer): void {
  server.tool(
    "list_files",
    "返回设备上已保存的 HTML 文件列表",
    {},
    async () => {
      try {
        const result = await sdkGet("/webview/files");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "list_images",
    "返回设备上已保存的图片列表",
    {},
    async () => {
      try {
        const result = await sdkGet("/inspector/images");
        return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "get_last_event",
    "返回最新页面切换事件快照（由后台 SSE 连接缓存）。无事件时返回 { event: null }",
    {},
    async () => {
      const lastEvent = eventMonitor.getLastEvent();
      const result = lastEvent ?? { event: null };
      return { content: [{ type: "text" as const, text: JSON.stringify(result) }] };
    }
  );
}
