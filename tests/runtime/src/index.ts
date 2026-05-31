import { runPageSuite } from "./suites/page.js";
import { runNodesSuite } from "./suites/nodes.js";
import { runCaptureSuite } from "./suites/capture.js";
import { runInteractSuite } from "./suites/interact.js";
import { runModifyViewSuite } from "./suites/modify-view.js";
import { runMockSuite } from "./suites/mock.js";
import { runWebViewRedirectSuite } from "./suites/webview-redirect.js";
import { getResults } from "./helpers.js";

const PLATFORM = process.env.PLATFORM ?? "android";
const DEFAULT_PORT = PLATFORM === "android" ? "8081" : "8080";
const PORT = process.env.CLIENT_TOOLS_PORT ?? DEFAULT_PORT;

console.log(`\n╔══ Client Tools Runtime Tests ═══════════════════╗`);
console.log(`   Platform : ${PLATFORM.toUpperCase()}`);
console.log(`   SDK URL  : http://127.0.0.1:${PORT}`);
console.log(`╚════════════════════════════════════════════════╝`);

try {
  await runPageSuite();
  await runNodesSuite();
  await runCaptureSuite();
  await runInteractSuite();
  await runModifyViewSuite();
  await runMockSuite();
  await runWebViewRedirectSuite();
} catch (e) {
  console.error("\n💥  Fatal error (uncaught):", e);
  process.exit(1);
}

const { passed, failed } = getResults();
const total = passed + failed;
const allPass = failed === 0;

console.log("\n" + "─".repeat(50));
console.log(
  allPass
    ? `✅  All ${total} tests passed`
    : `❌  ${failed}/${total} tests FAILED`
);
console.log("─".repeat(50) + "\n");

process.exit(allPass ? 0 : 1);
