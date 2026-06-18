/**
 * vision-judge.mjs — the K-run VISION JUDGE wrapper (spec §3.0 #6, §6.3, T11).
 *
 * Spawns the free Claude Max relay (`claude --print --output-format text` with ANTHROPIC_API_KEY
 * UNSET) K=3 fresh subprocesses per frame, delivers the image by appending its ABSOLUTE PATH into the
 * prompt text (the CLI has no --image flag; the agentic CLI opens the file with its Read tool — proven
 * this session, grounded in src/main/kotlin/jarvis/ClaudeMaxLlm.kt:36-44), and reduces the K parsed
 * verdicts via verdict-parser.mjs.
 *
 * NO paid API: ANTHROPIC_API_KEY is stripped from the child env (`env -u` equivalent) so the call
 * routes through the Claude Max subscription relay, never the metered API. --allowedTools Read lets the
 * nested agent open the image but nothing else (no writes, no bash).
 *
 * The judge gets ONLY: the defect-blind per-family rubric text (rubrics/<rubricKey>.txt) + the figure's
 * DECLARED INPUT (what the renderer itself consumes — array/grid-kind/reveal-beat/frontier-ids) + the
 * output contract embedded in the rubric. It is NEVER handed the catalog, the defect id, or any
 * correctness oracle (expected value / active cell / probability / highlight set). See spec §4.2.
 *
 * Pure reduction lives in verdict-parser.mjs (reduceMajority); this file owns only the I/O + spawn.
 *
 * Usage as a module:
 *   import { judgeFrame } from "./vision-judge.mjs";
 *   const r = await judgeFrame({ imagePath, rubricText, declaredInput, k:3 });
 *   // r = { perRun:[{verdict,checks,raw,exitCode,error}], majority, kPass, kFail, parseErrors }
 *
 * Usage as a CLI smoke (K=1 by default to save relay calls):
 *   node vision-judge.mjs <abs-image-path> <rubricKey> [k] ['<declared-input-text>']
 */

import { spawn } from "node:child_process";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve, isAbsolute } from "node:path";
import { parseVerdict, reduceMajority } from "./verdict-parser.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUBRICS_DIR = resolve(HERE, "rubrics");
const CLAUDE_BIN = process.env.JARVIS_CLAUDE_BIN || "claude";
const PER_CALL_TIMEOUT_MS = 180_000; // a single vision judgement; generous for the relay

/** Load a defect-blind rubric by key (family name or 'class-diagram'). Fails loud if absent. */
export function loadRubric(rubricKey) {
  const p = resolve(RUBRICS_DIR, `${rubricKey}.txt`);
  if (!existsSync(p)) {
    const avail = ["seq-array", "sort-merge", "matrix-grid", "graph-tree", "chart-dist", "class-diagram"];
    throw new Error(`[vision-judge] no rubric '${rubricKey}.txt' under ${RUBRICS_DIR} (have: ${avail.join(", ")})`);
  }
  return readFileSync(p, "utf8");
}

/**
 * Build the single-shot prompt for one judge call. Mirrors the ClaudeMaxLlm append format exactly:
 * the absolute image path goes in an "[Image attached …]" line so the nested CLI opens it with Read.
 *
 * @param {string} rubricText      — the defect-blind positive spec (rubrics/<key>.txt).
 * @param {string} imageAbsPath    — ABSOLUTE path to the bare cropped PNG.
 * @param {string} [declaredInput] — the figure's declared input ONLY (NO oracle). May be omitted.
 */
export function buildPrompt(rubricText, imageAbsPath, declaredInput) {
  if (!isAbsolute(imageAbsPath)) {
    throw new Error(`[vision-judge] image path must be ABSOLUTE (got '${imageAbsPath}')`);
  }
  const declared = declaredInput && String(declaredInput).trim()
    ? `\n\nDECLARED INPUT for this figure (what the renderer was handed — this is NOT a correctness oracle, only context for which elements should appear):\n${String(declaredInput).trim()}`
    : "";
  return (
    `${rubricText.trim()}${declared}\n\n` +
    `[Image attached for analysis — open and view this file: ${imageAbsPath}]`
  );
}

/**
 * One judge subprocess: spawn `claude --print --output-format text --allowedTools Read`, with
 * ANTHROPIC_API_KEY stripped (free relay), write the prompt to stdin, return {stdout, exitCode, error}.
 * Never throws on a child failure — a failed call surfaces as a non-zero exitCode / error string so the
 * caller can map it to an UNPARSEABLE abstention (conservative) rather than crashing the K-loop.
 */
export function runOnce(prompt) {
  return new Promise((resolveP) => {
    // Strip ANTHROPIC_API_KEY (and the lowercase variant) from the child env — the `env -u` proven
    // path. This forces the CLI through the Claude Max subscription relay, never the metered API.
    const childEnv = { ...process.env };
    delete childEnv.ANTHROPIC_API_KEY;
    delete childEnv.anthropic_api_key;

    const args = ["--print", "--output-format", "text", "--allowedTools", "Read"];
    if (process.env.JARVIS_CLAUDE_MODEL) args.push("--model", process.env.JARVIS_CLAUDE_MODEL);

    let child;
    try {
      child = spawn(CLAUDE_BIN, args, { env: childEnv });
    } catch (e) {
      resolveP({ stdout: "", exitCode: -1, error: `spawn failed: ${e.message}` });
      return;
    }

    let stdout = "";
    let stderr = "";
    let settled = false;
    const finish = (res) => { if (!settled) { settled = true; resolveP(res); } };

    const timer = setTimeout(() => {
      try { child.kill("SIGKILL"); } catch { /* already gone */ }
      finish({ stdout, exitCode: -2, error: `timeout after ${PER_CALL_TIMEOUT_MS}ms` });
    }, PER_CALL_TIMEOUT_MS);

    child.stdout.on("data", (d) => { stdout += d.toString("utf8"); });
    child.stderr.on("data", (d) => { stderr += d.toString("utf8"); });
    child.on("error", (e) => { clearTimeout(timer); finish({ stdout, exitCode: -1, error: `child error: ${e.message}` }); });
    child.on("close", (code) => {
      clearTimeout(timer);
      finish({ stdout, exitCode: code, error: code === 0 ? null : (stderr.trim().slice(0, 400) || `exit ${code}`) });
    });

    child.stdin.write(prompt);
    child.stdin.end();
  });
}

/**
 * Judge ONE frame with K fresh subprocesses (default K=3, odd per §6.3). Sequential by default so a
 * single frame never floods the relay; the orchestrator pools across frames. Returns the per-run
 * parses + the majority reduction.
 *
 * A run whose subprocess failed (non-zero exit / timeout / spawn error) is parsed as UNPARSEABLE
 * (conservative §6.3: a malformed/absent reply never silently passes a BAD frame) and counted as a
 * parse error.
 *
 * @returns {{ perRun: Array, majority:'PASS'|'FAIL', kPass:number, kFail:number,
 *             kUnparseable:number, parseErrors:number, unanimous:boolean }}
 */
export async function judgeFrame({ imagePath, rubricText, rubricKey, declaredInput, k = 3 } = {}) {
  if (!imagePath) throw new Error("[vision-judge] imagePath is required");
  const text = rubricText ?? loadRubric(rubricKey);
  if (!text) throw new Error("[vision-judge] rubricText or a resolvable rubricKey is required");
  const prompt = buildPrompt(text, imagePath, declaredInput);

  const perRun = [];
  for (let i = 0; i < k; i++) {
    const { stdout, exitCode, error } = await runOnce(prompt);
    let parsed;
    if (exitCode !== 0) {
      // Subprocess failure ⇒ treat as an abstention (UNPARSEABLE), not a guessed verdict.
      parsed = { verdict: "UNPARSEABLE", checks: [], raw: stdout };
    } else {
      parsed = parseVerdict(stdout);
    }
    perRun.push({ ...parsed, exitCode, error: error || null });
  }

  const red = reduceMajority(perRun);
  const parseErrors = perRun.filter((r) => r.verdict === "UNPARSEABLE").length;
  return {
    perRun,
    majority: red.decision,
    kPass: red.kPass,
    kFail: red.kFail,
    kUnparseable: red.kUnparseable,
    parseErrors,
    unanimous: red.unanimous,
  };
}

// ── CLI smoke entrypoint ────────────────────────────────────────────────────────────────────────
// node vision-judge.mjs <abs-image-path> <rubricKey> [k] ['<declared-input>']
const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url));
if (isMain) {
  const [, , imageArg, rubricKey, kArg, declaredArg] = process.argv;
  if (!imageArg || !rubricKey) {
    console.error("usage: node vision-judge.mjs <abs-image-path> <rubricKey> [k] ['<declared-input>']");
    process.exit(1);
  }
  const imagePath = isAbsolute(imageArg) ? imageArg : resolve(process.cwd(), imageArg);
  if (!existsSync(imagePath)) {
    console.error(`[vision-judge] image not found: ${imagePath}`);
    process.exit(1);
  }
  const k = kArg ? Number(kArg) : 1; // CLI default K=1 to save relay calls during a smoke
  judgeFrame({ imagePath, rubricKey, declaredInput: declaredArg, k })
    .then((r) => {
      console.log(JSON.stringify({
        imagePath, rubricKey, k,
        majority: r.majority, kPass: r.kPass, kFail: r.kFail, kUnparseable: r.kUnparseable,
        parseErrors: r.parseErrors, unanimous: r.unanimous,
        perRun: r.perRun.map((x) => ({ verdict: x.verdict, exitCode: x.exitCode, error: x.error, checks: x.checks })),
      }, null, 2));
      console.log("\n----- raw run 0 stdout (first 1500 chars) -----");
      console.log((r.perRun[0] && r.perRun[0].raw || "").slice(0, 1500));
    })
    .catch((e) => { console.error("[vision-judge] FATAL:", e.message); process.exit(1); });
}
