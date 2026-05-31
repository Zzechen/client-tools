import { execSync } from "child_process";
import { fromBinary, toBinary, MessageShape, DescMessage } from "@bufbuild/protobuf";
import { z } from "zod";

export type Platform = "android" | "ios";

/** 在每个 tool 的 input schema 中 spread 此对象即可加入 platform 参数 */
export const platformParam = {
  platform: z.enum(["android", "ios"]).describe(
    "目标平台：android（端口 8081）或 ios（端口 8080）"
  ),
} as const;

const PORTS: Record<Platform, number> = { android: 8081, ios: 8080 };
const DEFAULT_TIMEOUT_MS = 5000;
const DOM_TIMEOUT_MS = 8000;
const CUSTOM_TIMEOUT_MS = parseInt(process.env.CLIENT_TOOLS_CUSTOM_TIMEOUT_MS ?? "5000", 10);

function baseUrl(platform: Platform): string {
  return `http://127.0.0.1:${PORTS[platform]}`;
}

function ensureAdbForward(platform: Platform): void {
  if (platform === "android") {
    try {
      execSync(`adb forward tcp:${PORTS.android} tcp:${PORTS.android}`, { stdio: "ignore" });
    } catch {
      // adb not available or no device, ignore
    }
  }
}

export class SdkUnreachableError extends Error {
  constructor(cause: unknown) {
    super(`SDK unreachable: ${cause instanceof Error ? cause.message : String(cause)}`);
  }
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (e) {
    if ((e as Error).name === "AbortError") throw new SdkUnreachableError("request timed out");
    throw new SdkUnreachableError(e);
  } finally {
    clearTimeout(timer);
  }
}

export async function sdkGet<T extends DescMessage>(
  platform: Platform,
  path: string,
  schema: T
): Promise<MessageShape<T>> {
  ensureAdbForward(platform);
  const timeoutMs = path.startsWith("/dom") ? DOM_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
  const res = await fetchWithTimeout(`${baseUrl(platform)}${path}`, { method: "GET" }, timeoutMs);
  const buf = new Uint8Array(await res.arrayBuffer());
  return fromBinary(schema, buf);
}

export async function sdkPost<Req extends DescMessage, Res extends DescMessage>(
  platform: Platform,
  path: string,
  reqSchema: Req,
  reqMsg: MessageShape<Req>,
  resSchema: Res
): Promise<MessageShape<Res>> {
  ensureAdbForward(platform);
  const body = toBinary(reqSchema, reqMsg);
  const res = await fetchWithTimeout(
    `${baseUrl(platform)}${path}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/x-protobuf",
        "X-CT-Proto-Version": "1",
      },
      body,
    },
    DEFAULT_TIMEOUT_MS
  );
  const buf = new Uint8Array(await res.arrayBuffer());
  if (!res.ok) {
    let errMsg = `HTTP ${res.status}`;
    try {
      const errResp = fromBinary(resSchema, buf);
      const meta = (errResp as Record<string, unknown>).meta as { message?: string } | undefined;
      if (meta?.message) errMsg = meta.message;
    } catch { /* ignore parse failure, use HTTP status */ }
    throw new Error(errMsg);
  }
  return fromBinary(resSchema, buf);
}

export async function sdkDelete<Res extends DescMessage>(
  platform: Platform,
  path: string,
  resSchema: Res
): Promise<MessageShape<Res>> {
  ensureAdbForward(platform);
  const res = await fetchWithTimeout(`${baseUrl(platform)}${path}`, { method: "DELETE" }, DEFAULT_TIMEOUT_MS);
  const buf = new Uint8Array(await res.arrayBuffer());
  if (!res.ok) {
    let errMsg = `HTTP ${res.status}`;
    try {
      const errResp = fromBinary(resSchema, buf);
      const meta = (errResp as Record<string, unknown>).meta as { message?: string } | undefined;
      if (meta?.message) errMsg = meta.message;
    } catch { /* ignore */ }
    throw new Error(errMsg);
  }
  return fromBinary(resSchema, buf);
}

export async function sdkGetText(platform: Platform, path: string): Promise<string> {
  ensureAdbForward(platform);
  const res = await fetchWithTimeout(`${baseUrl(platform)}${path}`, { method: "GET" }, CUSTOM_TIMEOUT_MS);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.text();
}

export async function sdkPostText(platform: Platform, path: string, body: string): Promise<string> {
  ensureAdbForward(platform);
  const res = await fetchWithTimeout(
    `${baseUrl(platform)}${path}`,
    {
      method: "POST",
      headers: { "Content-Type": "text/plain" },
      body,
    },
    CUSTOM_TIMEOUT_MS
  );
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.text();
}
