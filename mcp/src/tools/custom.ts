import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { sdkGetText, sdkPostText, platformParam } from "../sdk-client.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerCustomTools(server: McpServer): void {
  server.tool(
    "list_custom_routes",
    "列出 app 层注册的所有自定义路由，包含路径、HTTP 方法、描述和参数说明（Android/iOS 通用）",
    { ...platformParam },
    async ({ platform }) => {
      try {
        const text = await sdkGetText(platform, "/custom/routes");
        return { content: [{ type: "text" as const, text }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "custom_call",
    "调用 app 层注册的自定义路由。响应格式：{\"code\":0,\"message\":\"ok\",\"data\":\"...\"} 或 {\"code\":-1,\"message\":\"...\",\"data\":null}（Android/iOS 通用）",
    {
      ...platformParam,
      path:   z.string().describe("路由路径，如 \"user/profile\"（不含 /custom/ 前缀）"),
      method: z.enum(["GET", "POST"]).describe("HTTP 方法"),
      body:   z.string().optional().describe("请求体字符串（POST 时使用，通常为 JSON）"),
    },
    async ({ platform, path, method, body }) => {
      try {
        const text = method === "GET"
          ? await sdkGetText(platform, `/custom/${path}`)
          : await sdkPostText(platform, `/custom/${path}`, body ?? "");
        return { content: [{ type: "text" as const, text }] };
      } catch (e) { return errResult(e); }
    }
  );
}
