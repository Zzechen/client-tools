import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { create } from "@bufbuild/protobuf";
import { sdkPost, platformParam } from "../sdk-client.js";
import {
  InputTextRequestSchema,
  InputTextResponseSchema,
  GestureRequestSchema,
  GestureResponseSchema,
  WaitForRequestSchema,
  WaitForResponseSchema,
} from "../generated/interaction_pb.js";

function errResult(e: unknown) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
  };
}

export function registerInteractionTools(server: McpServer): void {

  server.tool(
    "input_text",
    "向指定 View 输入文本（Android/iOS 通用）。要求 View 为 EditText/UITextField/UITextView。",
    {
      ...platformParam,
      id: z.string().describe("View id"),
      text: z.string().describe("输入内容"),
      append: z.boolean().optional().describe("true=追加到现有内容，false=替换（默认）"),
    },
    async ({ platform, id, text, append }) => {
      try {
        const req = create(InputTextRequestSchema, { id, text, append: append ?? false });
        const res = await sdkPost(platform, "/api/input", InputTextRequestSchema, req, InputTextResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ ok: res.meta?.code === 0 }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "gesture",
    "对指定 View 执行手势：长按（long_press）、双击（double_tap）、滑动（swipe）。Android/iOS 通用。",
    {
      ...platformParam,
      id: z.string().describe("View id"),
      type: z.enum(["long_press", "double_tap", "swipe"]).describe("手势类型"),
      duration_ms: z.number().int().optional().describe("长按持续时间 ms（默认 500）"),
      direction: z.enum(["up", "down", "left", "right"]).optional().describe("滑动方向（swipe 必填）"),
      distance_dp: z.number().optional().describe("滑动距离 dp（默认 200）"),
      swipe_duration_ms: z.number().int().optional().describe("滑动动画时长 ms（默认 300）"),
    },
    async ({ platform, id, type, duration_ms, direction, distance_dp, swipe_duration_ms }) => {
      try {
        const req = create(GestureRequestSchema, {
          id,
          type,
          durationMs: duration_ms ?? 0,
          direction: direction ?? "",
          distanceDp: distance_dp ?? 0,
          swipeDurationMs: swipe_duration_ms ?? 0,
        });
        const res = await sdkPost(platform, "/api/gesture", GestureRequestSchema, req, GestureResponseSchema);
        return { content: [{ type: "text" as const, text: JSON.stringify({ ok: res.meta?.code === 0 }) }] };
      } catch (e) { return errResult(e); }
    }
  );

  server.tool(
    "wait_for",
    "等待指定 View 满足条件后返回。超时返回 met=false。Android/iOS 通用。",
    {
      ...platformParam,
      id: z.string().describe("View id"),
      condition: z.enum(["visible", "gone", "exists", "not_exists"]).describe(
        "visible=可见且存在；gone=不存在或不可见；exists=存在（不论可见性）；not_exists=不存在"
      ),
      timeout_ms: z.number().int().optional().describe("超时时间 ms（默认 5000）"),
      interval_ms: z.number().int().optional().describe("轮询间隔 ms（默认 200）"),
    },
    async ({ platform, id, condition, timeout_ms, interval_ms }) => {
      try {
        const req = create(WaitForRequestSchema, {
          id,
          condition,
          timeoutMs: timeout_ms ?? 0,
          intervalMs: interval_ms ?? 0,
        });
        const res = await sdkPost(platform, "/api/wait", WaitForRequestSchema, req, WaitForResponseSchema);
        return {
          content: [{
            type: "text" as const,
            text: JSON.stringify({ met: res.met, elapsed_ms: res.elapsedMs }),
          }],
        };
      } catch (e) { return errResult(e); }
    }
  );
}
