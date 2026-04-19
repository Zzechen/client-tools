const PORT = process.env.CLIENT_TOOLS_PORT ?? "8080";
const BASE_URL = `http://127.0.0.1:${PORT}`;
const DEFAULT_TIMEOUT_MS = 5000;
const DOM_TIMEOUT_MS = 8000;

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

export async function sdkGet(path: string): Promise<unknown> {
  const timeoutMs = path.startsWith("/dom") ? DOM_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
  const res = await fetchWithTimeout(`${BASE_URL}${path}`, { method: "GET" }, timeoutMs);
  return res.json();
}

export async function sdkPost(path: string, body: unknown): Promise<unknown> {
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

export function sdkEventUrl(): string {
  return `${BASE_URL}/api/events`;
}
