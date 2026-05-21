import { mkdirSync, writeFileSync } from "fs";
import { join } from "path";

const PLATFORM = process.env.PLATFORM ?? "android";

// Snapshot output directory: tests/snapshots/{platform}/
export const SNAPSHOT_DIR = join("tests", "snapshots");

let _passed = 0;
let _failed = 0;

export function assert(condition: boolean, msg: string): void {
  if (condition) {
    console.log(`    ✅  ${msg}`);
    _passed++;
  } else {
    console.error(`    ❌  ${msg}`);
    _failed++;
  }
}

/**
 * Assert |actual - expected| <= tolerance.
 * Prints actual and expected values in the failure message.
 */
export function assertApprox(
  actual: number,
  expected: number,
  tolerance: number,
  msg: string
): void {
  const ok = Math.abs(actual - expected) <= tolerance;
  assert(ok, `${msg} (got ${actual.toFixed(2)}, expected ${expected.toFixed(2)} ±${tolerance})`);
}

/**
 * Save PNG bytes to tests/snapshots/{platform}/{name}_{timestamp}.png.
 */
export function saveSnapshot(name: string, pngBytes: Uint8Array): void {
  const dir = join(SNAPSHOT_DIR, PLATFORM);
  mkdirSync(dir, { recursive: true });
  const file = join(dir, `${name}_${Date.now()}.png`);
  writeFileSync(file, Buffer.from(pngBytes));
  console.log(`    📷  saved ${file}`);
}

export function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

export function getResults(): { passed: number; failed: number } {
  return { passed: _passed, failed: _failed };
}
