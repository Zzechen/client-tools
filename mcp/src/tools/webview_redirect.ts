import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkPost, sdkGet, sdkDelete, platformParam } from "../sdk-client.js";
import {
  WebViewRedirectResponseSchema,
  WebViewRedirectListResponseSchema,
  SimpleResponseSchema,
  ClearWebViewRedirectsResponseSchema,
} from "../generated/api_pb.js";
import { AddWebViewRedirectRequestSchema } from "../generated/webview_redirect_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerWebViewRedirectTools(server: McpServer): void {
  server.tool(
    "webview_redirect_add",
    "添加 WebView URL 重定向规则。App 加载 WebView 时若 URL 命中 urlPattern，则跳转到 targetUrl（原始 URL 的 query 参数会追加到目标 URL，原始参数优先）",
    {
      ...platformParam,
      urlPattern: z.string().describe("正则表达式，匹配原始 URL（不含 query 部分）"),
      targetUrl: z.string().describe("命中后重定向到的目标地址，如 http://192.168.1.x:3000/page"),
    },
    async ({ platform, urlPattern, targetUrl }) => {
      try {
        const req = create(AddWebViewRedirectRequestSchema, { urlPattern, targetUrl });
        const res = await sdkPost(platform, "/webview/redirects", AddWebViewRedirectRequestSchema, req, WebViewRedirectResponseSchema);
        return {
          content: [{
            type: "text" as const,
            text: JSON.stringify({ id: res.data?.id, urlPattern: res.data?.urlPattern, targetUrl: res.data?.targetUrl }),
          }],
        };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "webview_redirect_list",
    "列出所有当前生效的 WebView 重定向规则",
    { ...platformParam },
    async ({ platform }) => {
      try {
        const res = await sdkGet(platform, "/webview/redirects", WebViewRedirectListResponseSchema);
        const rules = (res.data?.rules ?? []).map(r => ({
          id: r.id,
          urlPattern: r.urlPattern,
          targetUrl: r.targetUrl,
        }));
        return { content: [{ type: "text" as const, text: JSON.stringify(rules) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "webview_redirect_delete",
    "按 id 删除一条 WebView 重定向规则",
    { ...platformParam, id: z.string().describe("规则 id，由 webview_redirect_add 返回") },
    async ({ platform, id }) => {
      try {
        await sdkDelete(platform, `/webview/redirects/${id}`, SimpleResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ success: true }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "webview_redirect_clear",
    "清空所有 WebView 重定向规则",
    { ...platformParam },
    async ({ platform }) => {
      try {
        const res = await sdkDelete(platform, "/webview/redirects", ClearWebViewRedirectsResponseSchema);
        return {
          content: [{
            type: "text" as const,
            text: JSON.stringify({ cleared_count: Number(res.clearedCount) }),
          }],
        };
      } catch (e) { return errResult(e); }
    }
  );
}
