// Surface X — trace-replay rubric-grader core.
//
// gradeOne(): sends ONE invariant statement + the scoped event slice to an
// LLM-as-judge, parses JSON reply into {status, evidence, reason}, threads
// envelope metadata (model_resolved, prompt_sha256, tokens, latency) back
// to the caller for finding-doc provenance.
//
// majorityVote(): collapses N independent runs of the same invariant to
// one verdict via plurality. Ties degrade to N_A so the deploy hook
// (Task 1.7) errs toward "not enough signal" rather than false-fail.
//
// ofN(): K-of-N exact-match gate for the calibration mode (Task 1.4).
// Compares judge verdict vs. hand-labelled gold per fixture trace and
// reports whether the catalog meets the agreement threshold.

import { callLlm as defaultCallLlm } from "./lib/openrouter.mjs";
import { callLlm as claudeCliCallLlm } from "./lib/claude-cli.mjs";
import { INVARIANTS } from "./surface-x-invariants.mjs";
import { getStamp } from "./lib/provenance.mjs";
import { writeFileSync, mkdirSync, readFileSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

export function resolveProvider({ provider = "openrouter" } = {}) {
  if (provider === "openrouter") return { name: "openrouter", callLlm: defaultCallLlm };
  if (provider === "claude-cli") return { name: "claude-cli", callLlm: claudeCliCallLlm };
  throw new Error(`unknown provider: ${provider} (expected: openrouter | claude-cli)`);
}

const GRADER_SYSTEM = `You are an invariant judge. Given a session trace and one invariant statement, return strict JSON:
{
  "status": "PASS" | "FAIL" | "N_A",
  "evidence": {"event_ids": [...], "excerpt": "..."},
  "reason": "<one sentence>"
}
N_A is reserved for invariants that don't apply to the given trace (e.g. no relevant events). Reply JSON only.`;

export async function gradeOne({
  invariantId,
  invariantStatement,
  events,
  callLlm = defaultCallLlm,
  apiKey = process.env.OPENROUTER_API_KEY_STANDIN ?? process.env.OPENROUTER_API_KEY,
  model = "openai/gpt-oss-120b:free",
  temperature = 0,
  seed = 42,
}) {
  const userPrompt = `INVARIANT: ${invariantId} — ${invariantStatement}\n\nSESSION EVENTS (jsonl):\n${events.map(e => JSON.stringify(e)).join("\n")}\n\nReply: <JSON only>`;
  const r = await callLlm({
    apiKey,
    model,
    systemPrompt: GRADER_SYSTEM,
    userPrompt,
    temperature,
    seed,
  });
  let parsed;
  try { parsed = JSON.parse(r.text); } catch { parsed = { status: "N_A", evidence: {}, reason: "parse_error" }; }
  return {
    status: parsed.status ?? "N_A",
    evidence: parsed.evidence ?? {},
    reason: parsed.reason ?? "",
    model_resolved: r.model_resolved,
    prompt_sha256: r.prompt_sha256,
    tokens_in: r.tokens_in,
    tokens_out: r.tokens_out,
    latency_ms: r.latency_ms,
  };
}

export function majorityVote(runs) {
  const counts = {};
  for (const r of runs) counts[r.status] = (counts[r.status] ?? 0) + 1;
  const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  if (sorted.length === 1) return runs[0];
  if (sorted[0][1] === sorted[1][1]) return { ...runs[0], status: "N_A", reason: "no majority" };
  return runs.find(r => r.status === sorted[0][0]);
}

export function ofN(pairs, thresholdPct = 0.80) {
  const total = pairs.length;
  const matched = pairs.filter(p => p.judge === p.gold).length;
  const k = Math.ceil(thresholdPct * total);
  return { matched, total, k, threshold_pct: thresholdPct, passed: matched >= k };
}

export async function gradeSession({
  sessionId,
  events,
  invariantIds = INVARIANTS.map(i => i.id),
  outputDir = "docs/standin-findings",
  callLlm,
  apiKey,
  model,
  runsPerInvariant = 3,
  providerName = null,
}) {
  process.env.SURFACE_VERSION = "x-v1.0";
  const results = [];
  let lastJudgeModel = null;
  let lastPromptSha = null;
  for (const id of invariantIds) {
    const inv = INVARIANTS.find(i => i.id === id);
    if (!inv) continue;
    const bracketed = inv.scope(events);
    if (bracketed.length === 0) {
      results.push({ id, classification: inv.classification, status: "N_A", reason: "no in-scope events", evidence: {} });
      continue;
    }
    const runs = [];
    for (let i = 0; i < runsPerInvariant; i++) {
      const r = await gradeOne({
        invariantId: id,
        invariantStatement: inv.statement,
        events: bracketed,
        callLlm, apiKey, model,
      });
      runs.push(r);
      lastJudgeModel = r.model_resolved;
      lastPromptSha = r.prompt_sha256;
    }
    const voted = majorityVote(runs);
    const final = {
      id,
      classification: inv.classification,
      status: inv.classification === "INFO" ? "INFO" : voted.status,
      evidence: voted.evidence,
      reason: voted.reason,
      latencies_ms: runs.map(r => r.latency_ms),
    };
    if (inv.classification === "INFO") {
      const sorted = bracketed.map(e => e.latency_ms ?? 0).filter(Boolean).sort((a, b) => a - b);
      final.latency_p95_ms = sorted[Math.floor(sorted.length * 0.95)] ?? null;
    }
    results.push(final);
  }

  const stamp = await getStamp(null, {
    judge_model_resolved: lastJudgeModel,
    judge_prompt_sha256: lastPromptSha,
    provider_name: providerName,
  });
  const ts = stamp.ts_utc.replace(/[:.]/g, "-");
  mkdirSync(outputDir, { recursive: true });
  const docPath = join(outputDir, `DRAFT-X-${sessionId}-${ts}.md`);
  const fm = [
    "---",
    "surface: X",
    `session_id: ${sessionId}`,
    "provenance:",
    `  git_head: ${stamp.git_head}`,
    `  bundle_hash: ${stamp.bundle_hash}`,
    `  live_dom_fingerprint: ${stamp.live_dom_fingerprint ?? "null"}`,
    `  ts_utc: ${stamp.ts_utc}`,
    `  surface_version: ${stamp.surface_version}`,
    `  judge_model_resolved: ${stamp.judge_model_resolved ?? "null"}`,
    `  judge_prompt_sha256: ${stamp.judge_prompt_sha256 ?? "null"}`,
    `  provider_name: ${stamp.provider_name ?? "null"}`,
    `invariants_run: ${results.length}`,
    "---",
    "",
    `# Surface X findings — session ${sessionId}`,
    "",
    "| Invariant | Status | Reason |",
    "|-----------|--------|--------|",
    ...results.map(r => `| ${r.id} | ${r.status} | ${(r.reason || "").slice(0, 120)} |`),
    "",
    "## Per-invariant detail",
    ...results.flatMap(r => [
      "",
      `### ${r.id} — ${r.status}`,
      `- classification: ${r.classification}`,
      r.evidence?.event_ids ? `- evidence event_ids: ${JSON.stringify(r.evidence.event_ids)}` : "",
      r.evidence?.excerpt ? `- excerpt: ${r.evidence.excerpt.slice(0, 200)}` : "",
      `- reason: ${r.reason ?? ""}`,
      r.latency_p95_ms !== undefined ? `- latency_p95_ms: ${r.latency_p95_ms}` : "",
    ].filter(Boolean)),
  ].join("\n");
  writeFileSync(docPath, fm);
  return docPath;
}

export function parseFixture(text) {
  const traces = [];
  const sections = text.split(/^### Trace /m).slice(1);
  for (const sec of sections) {
    const yamlMatch = sec.match(/```yaml\n([\s\S]+?)\n```/);
    if (!yamlMatch) continue;
    const yamlText = yamlMatch[1];
    const events = [];
    const labels = {};
    let inEvents = false, inLabels = false;
    for (const line of yamlText.split("\n")) {
      if (line.startsWith("events:")) { inEvents = true; inLabels = false; continue; }
      if (line.startsWith("labels:")) { inEvents = false; inLabels = true; continue; }
      if (inEvents && line.trim().startsWith("- ")) {
        const flow = line.trim().slice(2).trim();
        const m = flow.match(/^\{([\s\S]+)\}$/);
        if (m) {
          const obj = {};
          for (const pair of m[1].split(",")) {
            const [k, v] = pair.split(":").map(s => s.trim().replace(/^['"]|['"]$/g, ""));
            obj[k] = v;
          }
          events.push(obj);
        }
      }
      if (inLabels && /^\s+INV-\d{2}:/.test(line)) {
        const m = line.match(/^\s+(INV-\d{2}):\s*(\w+)\s*$/);
        if (m) labels[m[1]] = m[2];
      }
    }
    traces.push({ events, labels });
  }
  return traces;
}

export async function calibrateAgainstFixture({
  fixturePath,
  callLlm,
  apiKey,
  model,
  runsPerInvariant = 3,
  thresholdPct = 0.80,
}) {
  const text = readFileSync(fixturePath, "utf8");
  const traces = parseFixture(text);
  const pairs = [];
  for (const t of traces) {
    for (const [invId, goldStatus] of Object.entries(t.labels)) {
      const inv = INVARIANTS.find(i => i.id === invId);
      if (!inv) continue;
      const bracketed = inv.scope(t.events);
      const runs = [];
      for (let i = 0; i < runsPerInvariant; i++) {
        const r = await gradeOne({
          invariantId: invId,
          invariantStatement: inv.statement,
          events: bracketed,
          callLlm, apiKey, model,
        });
        runs.push(r);
      }
      const voted = majorityVote(runs);
      pairs.push({ invariantId: invId, judge: voted.status, gold: goldStatus });
    }
  }
  const gate = ofN(pairs, thresholdPct);
  return { ...gate, pairs };
}

function parseArgs() {
  const out = {};
  for (const a of process.argv.slice(2)) {
    const m = a.match(/^--([^=]+)=(.+)$/);
    if (m) out[m[1]] = m[2];
    else if (a.startsWith("--")) out[a.slice(2)] = true;
  }
  return out;
}

const USAGE = `Usage: node surface-x.mjs [options]
Options:
  --task=<id>                     Task ID filter for event log.
  --session=<id>                  Session ID filter for event log.
  --from=<ts>                     Lower-bound timestamp filter.
  --to=<ts>                       Upper-bound timestamp filter.
  --include-synthetic             Include synthetic events in the slice.
  --invariants=<csv|all>          Subset of invariant IDs to grade.
  --calibrate                     Use 3 runs/invariant (default 1 without).
  --from-fixture=<path>           Calibrate against gold-labelled fixture.
  --threshold=<float>             K-of-N agreement threshold (default 0.80).
  --model=<id>                    Override judge model.
  --provider=openrouter|claude-cli Provider routing (default openrouter).
  --ssh=<user@host>               SSH target for event log (default root@46.247.109.91).
`;

if (process.argv[1]?.endsWith("surface-x.mjs")) {
  const args = parseArgs();
  if (args.help || args.h) {
    console.log(USAGE);
    process.exit(0);
  }
  const provider = resolveProvider({ provider: args.provider ?? "openrouter" });
  if (args["from-fixture"]) {
    const r = await calibrateAgainstFixture({
      fixturePath: args["from-fixture"],
      runsPerInvariant: args.calibrate ? 3 : 1,
      thresholdPct: Number(args.threshold ?? 0.80),
      model: args.model,
      callLlm: provider.callLlm,
    });
    console.log(`Calibration: ${r.matched}/${r.total} match (K=${r.k}). Threshold=${r.threshold_pct}. Passed: ${r.passed}`);
    console.log("Per-pair:");
    for (const p of r.pairs) console.log(`  ${p.invariantId}: judge=${p.judge} gold=${p.gold} ${p.judge === p.gold ? "OK" : "MISS"}`);
    process.exit(r.passed ? 0 : 1);
  }
  const { readEvents, filterEvents } = await import("./lib/event-log-reader.mjs");
  const all = await readEvents({ sshTarget: args["ssh"] ?? "root@46.247.109.91" });
  const filtered = filterEvents(all, {
    task_id: args.task,
    session_id: args.session,
    from_ts: args.from,
    to_ts: args.to,
    include_synthetic: !!args["include-synthetic"],
    status_in: args["all-statuses"] ? null : ["ok"],
  });
  const sessionId = args.session ?? `auto-${Date.now()}`;
  const idsArg = args.invariants === "all" ? null : (args.invariants ?? "INV-01,INV-02,INV-03,INV-04,INV-05,INV-06,INV-07,INV-08,INV-09,INV-10");
  const docPath = await gradeSession({
    sessionId,
    events: filtered,
    invariantIds: idsArg ? idsArg.split(",") : undefined,
    runsPerInvariant: args.calibrate ? 3 : 1,
    outputDir: resolve(REPO_ROOT, "docs/standin-findings"),
    model: args.model,
    callLlm: provider.callLlm,
    providerName: provider.name,
  });
  console.log(`Wrote: ${docPath}`);
}
