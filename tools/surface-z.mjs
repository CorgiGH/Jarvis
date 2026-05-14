// tools/surface-z.mjs
import { chromium } from "playwright";
import { writeFileSync, mkdirSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { callLlm as defaultCallLlm } from "./lib/openrouter.mjs";
import { LINT_EVAL_SCRIPT } from "./surface-z-lints.mjs";
import { getStamp } from "./lib/provenance.mjs";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

// Fix 2: Clean system prompt — persona + JSON schema only, no <INSERT> placeholders
const LAYPERSON_SYSTEM_PROMPT = `You are a non-designer Romanian uni student opening this study site for the first time. You're judging ONLY whether things look right to your eyes — not technical correctness.

Reply STRICT JSON:
{
  "severity": "blocking" | "readability" | "cosmetic" | "none",
  "observations": [{"what":"...","where":"<region>","why_it_hurts":"..."}],
  "one_liner": "<single sentence overall impression>"
}`;

// Fix 2: Helper that builds the user prompt with actual lint/dom data
function buildUserPrompt(lints, domText) {
  return `AUTO-LINTS (hints, extend with your own eye):
${JSON.stringify(lints)}

DOM TEXT EXCERPT (text-only fallback if vision unavailable):
${domText}`;
}

export async function sweepPages({
  pages = ["/tutor/", "/tutor/review"],
  viewports = [{ width: 1280, height: 800, name: "desktop" }],
  browser = null,
  callLlm = defaultCallLlm,
  outputDir = "docs/standin-findings",
  screenshotDir = "docs/standin-findings/screenshots",
  sessionId = `auto-${Date.now()}`,
  baseUrl = "https://corgflix.duckdns.org",
  authCookie = process.env.JARVIS_AUTH_COOKIE,
  model = "openai/gpt-oss-120b:free",
  textFallbackModel = "deepseek/deepseek-v4-flash:free",
} = {}) {
  process.env.SURFACE_VERSION = "z-v1.0";
  const ownsBrowser = !browser;
  if (!browser) browser = await chromium.launch({ headless: true });

  // Fix 1: try/finally around entire sweep so browser always closes
  try {
    mkdirSync(outputDir, { recursive: true });
    mkdirSync(screenshotDir, { recursive: true });

    const allFindings = [];
    let lastJudgeModel = null, lastPromptSha = null;

    for (const vp of viewports) {
      const ctx = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        storageState: authCookie ? {
          cookies: [{ name: "jarvis_auth", value: authCookie, domain: new URL(baseUrl).hostname, path: "/", secure: true, httpOnly: false }],
          origins: [],
        } : undefined,
      });

      // Fix 1: try/finally so ctx always closes
      try {
        for (const [pageIdx, path] of pages.entries()) {
          const page = await ctx.newPage();

          // Fix 1: try/finally so page always closes
          try {
            await page.goto(`${baseUrl}${path}`);
            await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
            const lints = await page.evaluate(LINT_EVAL_SCRIPT);
            // Page-index prefix keeps screenshot names unique even when two
            // distinct page paths sanitize to the same safePath (e.g.
            // /tutor/?a=b_c and /tutor/?a=b&c both → _tutor_a_b_c).
            const safePath = path.replace(/[^a-zA-Z0-9]+/g, "_");
            const idx = String(pageIdx).padStart(2, "0");
            const screenshotPath = join(screenshotDir, `Z-${sessionId}-${vp.name}-${idx}-${safePath}.png`);
            await page.screenshot({ path: screenshotPath, fullPage: true });

            // Fix 2: use buildUserPrompt helper instead of .replace() chains
            const domText = await page.evaluate("document.body.innerText.slice(0, 4000)");
            const userPrompt = buildUserPrompt(lints, domText);

            let llmResp;
            // Fix 3: graceful per-page skip on double LLM failure
            try {
              llmResp = await callLlm({
                apiKey: process.env.OPENROUTER_API_KEY_STANDIN ?? process.env.OPENROUTER_API_KEY,
                model,
                systemPrompt: LAYPERSON_SYSTEM_PROMPT,
                userPrompt,
                temperature: 0.2,
              });
            } catch (e) {
              try {
                llmResp = await callLlm({
                  apiKey: process.env.OPENROUTER_API_KEY_STANDIN ?? process.env.OPENROUTER_API_KEY,
                  model: textFallbackModel,
                  systemPrompt: LAYPERSON_SYSTEM_PROMPT,
                  userPrompt,
                  temperature: 0.2,
                });
              } catch (e2) {
                // Both models failed — record error finding and continue; page finally still runs
                allFindings.push({
                  path,
                  viewport: vp.name,
                  lints,
                  screenshot: screenshotPath,
                  severity: "error",
                  observations: [],
                  one_liner: `llm_unavailable: ${(e2 && e2.message) ? e2.message.slice(0, 120) : "unknown"}`,
                });
                continue;
              }
            }

            lastJudgeModel = llmResp.model_resolved;
            lastPromptSha = llmResp.prompt_sha256;

            let parsed = { severity: "none", observations: [], one_liner: "parse_error" };
            try { parsed = JSON.parse(llmResp.text); } catch {}

            allFindings.push({
              path,
              viewport: vp.name,
              lints,
              screenshot: screenshotPath,
              ...parsed,
            });
          } finally {
            // Fix 1: page always closed; .catch so close failure doesn't mask original error
            await page.close().catch(() => {});
          }
        }
      } finally {
        // Fix 1: ctx always closed
        await ctx.close().catch(() => {});
      }
    }

    const stamp = await getStamp(null, {
      judge_model_resolved: lastJudgeModel,
      judge_prompt_sha256: lastPromptSha,
    });
    const ts = stamp.ts_utc.replace(/[:.]/g, "-");
    const docPath = join(outputDir, `DRAFT-Z-${sessionId}-${ts}.md`);
    const md = [
      "---",
      "surface: Z",
      "mode: standalone",
      `session_id: ${sessionId}`,
      "provenance:",
      `  git_head: ${stamp.git_head}`,
      `  bundle_hash: ${stamp.bundle_hash}`,
      `  live_dom_fingerprint: ${stamp.live_dom_fingerprint ?? "null"}`,
      `  ts_utc: ${stamp.ts_utc}`,
      `  surface_version: ${stamp.surface_version}`,
      `  judge_model_resolved: ${stamp.judge_model_resolved ?? "null"}`,
      `  judge_prompt_sha256: ${stamp.judge_prompt_sha256 ?? "null"}`,
      `pages_visited: ${allFindings.length}`,
      "---",
      "",
      `# Surface Z findings — ${sessionId}`,
      "",
      "| Page | Viewport | Severity | One-liner |",
      "|------|----------|----------|-----------|",
      ...allFindings.map(f => `| ${f.path} | ${f.viewport} | ${f.severity} | ${f.one_liner ?? ""} |`),
      "",
      "## Detailed observations",
      ...allFindings.flatMap(f => [
        "",
        `### ${f.path} (${f.viewport}) — ${f.severity}`,
        "**Auto-lints:**",
        `- snake_case strings: ${f.lints.snake_case.length}`,
        ...f.lints.snake_case.map(s => `  - "${s.text}" at \`${s.selector}\``),
        `- low_contrast nodes: ${f.lints.low_contrast.length}`,
        `- small_font nodes: ${f.lints.small_font.length}`,
        `- horizontal_overflow: ${f.lints.h_overflow}`,
        "**Layperson observations:**",
        ...(f.observations ?? []).map(o => `- **${o.what}** (${o.where}): ${o.why_it_hurts}`),
        `**Screenshot:** \`${f.screenshot}\``,
      ]),
    ].join("\n");
    writeFileSync(docPath, md);
    return docPath;
  } finally {
    // Fix 1: browser always closed if we own it
    if (ownsBrowser) await browser.close().catch(() => {});
  }
}

// CLI
if (process.argv[1]?.endsWith("surface-z.mjs")) {
  const args = Object.fromEntries(process.argv.slice(2).map(a => {
    const m = a.match(/^--([^=]+)=(.+)$/); return m ? [m[1], m[2]] : [a.replace(/^--/, ""), true];
  }));
  const pages = args.pages ? args.pages.split(",") : ["/tutor/", "/tutor/review"];
  // Fix 5a: NaN guard for --viewports
  const viewports = args.viewports
    ? args.viewports.split(",").map(w => {
        const width = Number.isFinite(+w) ? +w : 1280;
        return { width, height: 800, name: `vp-${w}` };
      })
    : [{ width: 1280, height: 800, name: "desktop" }];
  const docPath = await sweepPages({
    pages,
    viewports,
    sessionId: args.session ?? `auto-${Date.now()}`,
    outputDir: resolve(REPO_ROOT, "docs/standin-findings"),
    screenshotDir: resolve(REPO_ROOT, "docs/standin-findings/screenshots"),
  });
  console.log(`Wrote: ${docPath}`);
}
