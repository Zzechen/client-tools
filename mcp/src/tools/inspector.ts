import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { sdkGet } from "../sdk-client.js";
import { FileListResponseSchema } from "../generated/inspector_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerInspectorTools(server: McpServer): void {
  server.tool(
    "list_files",
    "返回设备上已保存的 HTML 文件列表（Android/iOS 通用）",
    {},
    async () => {
      try {
        const res = await sdkGet("/webview/files", FileListResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify(res.data?.files) }] };
      } catch (e) { return errResult(e); }
    }
  );
}
