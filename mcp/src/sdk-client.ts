import { execSync } from "child_process";
import { fromBinary, toBinary, MessageShape, DescMessage } from "@bufbuild/protobuf";

const PORT = process.env.CLIENT_TOOLS_PORT ?? "8080";
const BASE_URL = `http://127.0.0.1:${PORT}`;
const DEFAULT_TIMEOUT_MS = 5000;
const DOM_TIMEOUT_MS = 8000;

function ensureAdbForward(): void {
  try {
    execSync(`adb forward tcp:${PORT} tcp:${PORT}`, { stdio: "ignore" });
  } catch {
    // adb not available or no device, ignore
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
  path: string,
  schema: T
): Promise<MessageShape<T>> {
  ensureAdbForward();
  const timeoutMs = path.startsWith("/dom") ? DOM_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
  const res = await fetchWithTimeout(`${BASE_URL}${path}`, { method: "GET" }, timeoutMs);
  const buf = new Uint8Array(await res.arrayBuffer());
  return fromBinary(schema, buf);
}

export async function sdkPost<Req extends DescMessage, Res extends DescMessage>(
  path: string,
  reqSchema: Req,
  reqMsg: MessageShape<Req>,
  resSchema: Res
): Promise<MessageShape<Res>> {
  ensureAdbForward();
  const body = toBinary(reqSchema, reqMsg);
  const res = await fetchWithTimeout(
    `${BASE_URL}${path}`,
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
  return fromBinary(resSchema, buf);
}

export async function sdkGetRaw(path: string): Promise<unknown> {
  ensureAdbForward();
  const timeoutMs = path.startsWith("/dom") ? DOM_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
  const res = await fetchWithTimeout(`${BASE_URL}${path}`, { method: "GET" }, timeoutMs);
  return res.json();
}

export async function sdkPostRaw(path: string, body: unknown): Promise<unknown> {
  ensureAdbForward();
  const res = await fetchWithTimeout(
    `${BASE_URL}${path}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
    DEFAULT_TIMEOUT_MS
  );
  return res.json();
}
