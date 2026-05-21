import { fromBinary, toBinary, create, type MessageShape, type DescMessage, type MessageInitShape } from "@bufbuild/protobuf";
import { execSync } from "child_process";

const PORT = process.env.CLIENT_TOOLS_PORT ?? "8080";
const BASE_URL = `http://127.0.0.1:${PORT}`;
const PLATFORM = process.env.PLATFORM ?? "android";

function ensurePortForward(): void {
  if (PLATFORM === "android") {
    try {
      execSync(`adb forward tcp:${PORT} tcp:${PORT}`, { stdio: "ignore" });
    } catch { /* ignore: no device or adb not in PATH */ }
  }
  // iOS: assumes `iproxy 8080 8080` is already running externally
}

async function fetchBytes(url: string, init: RequestInit = {}): Promise<{ status: number; bytes: Uint8Array }> {
  const res = await fetch(url, init);
  const bytes = new Uint8Array(await res.arrayBuffer());
  return { status: res.status, bytes };
}

/**
 * HTTP GET → parse protobuf response.
 */
export async function sdkGet<T extends DescMessage>(
  path: string,
  schema: T
): Promise<MessageShape<T>> {
  ensurePortForward();
  const { bytes } = await fetchBytes(`${BASE_URL}${path}`);
  return fromBinary(schema, bytes);
}

/**
 * HTTP POST with protobuf body → parse protobuf response.
 * On non-2xx or parse failure, returns an empty message (fields are default values).
 */
export async function sdkPost<Req extends DescMessage, Res extends DescMessage>(
  path: string,
  reqSchema: Req,
  req: MessageShape<Req>,
  resSchema: Res
): Promise<MessageShape<Res>> {
  ensurePortForward();
  const body = toBinary(reqSchema, req);
  const { bytes } = await fetchBytes(`${BASE_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/x-protobuf" },
    body,
  });
  try {
    return fromBinary(resSchema, bytes);
  } catch {
    // Android error responses may be plain text (not protobuf); return empty proto.
    return create(resSchema, {} as MessageInitShape<Res>);
  }
}

/**
 * HTTP DELETE → parse protobuf response.
 */
export async function sdkDelete<Res extends DescMessage>(
  path: string,
  resSchema: Res
): Promise<MessageShape<Res>> {
  ensurePortForward();
  const { bytes } = await fetchBytes(`${BASE_URL}${path}`, { method: "DELETE" });
  return fromBinary(resSchema, bytes);
}
