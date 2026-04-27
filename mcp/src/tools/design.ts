import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { execFile } from "child_process";
import { promisify } from "util";
import { existsSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";

const execFileAsync = promisify(execFile);
const __dirname = dirname(fileURLToPath(import.meta.url));

const SCRIPTS_DIR = join(__dirname, "../../scripts/preprocess");
const PYTHON = join(SCRIPTS_DIR, ".venv/bin/python");
const SCRIPT = join(SCRIPTS_DIR, "preprocess.py");

function errResult(msg: string) {
  return {
    isError: true as const,
    content: [{ type: "text" as const, text: msg }],
  };
}

export function registerDesignTools(server: McpServer): void {
  server.tool(
    "extract_view_layout",
    "渲染设计稿 HTML，提取与 Android/iOS 运行时 View 对齐的布局节点数据，输出 design.json",
    {
      file: z.string().describe("设计稿 HTML 文件的绝对路径"),
      viewport: z.number().describe("设计稿渲染宽度（px），如 375、390、360"),
    },
    async ({ file, viewport }) => {
      try {
        if (!existsSync(PYTHON)) {
          return errResult(
            `preprocess .venv 未初始化，请先运行：\ncd ${SCRIPTS_DIR} && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt`
          );
        }
        if (!existsSync(SCRIPT)) {
          return errResult(`preprocess 脚本不存在：${SCRIPT}`);
        }
        if (!existsSync(file)) {
          return errResult(`HTML 文件不存在：${file}`);
        }

        const { stdout } = await execFileAsync(PYTHON, [
          SCRIPT,
          "--input", file,
          "--viewport", String(viewport),
        ]);

        const result = JSON.parse(stdout.trim());
        return {
          content: [{ type: "text" as const, text: JSON.stringify(result) }],
        };
      } catch (e) {
        return errResult(e instanceof Error ? e.message : String(e));
      }
    }
  );
}
