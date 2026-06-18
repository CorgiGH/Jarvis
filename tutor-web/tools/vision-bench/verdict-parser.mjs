/**
 * verdict-parser.mjs — PURE, side-effect-free parser for the vision-judge's stdout (spec §3.0 #7, T10).
 *
 * Mirrors the unit-testable shape of frame-conjunction-core.mjs: NO browser, NO subprocess, NO I/O —
 * one exported pure function `parseVerdict(stdout) -> { verdict, checks, raw }` plus tiny helpers, so
 * the K-run judge wrapper (vision-judge.mjs) and the scorer both reduce identically and the parser is
 * exercisable without spawning `claude`.
 *
 * ── The output contract the judge prompts for (spec §4 "Output contract") ─────────────────────────
 *   VERDICT: PASS|FAIL
 *   CHECK <name|index>: PASS|FAIL — <pixel-grounded reason>
 *   CHECK <name|index>: PASS|FAIL — <pixel-grounded reason>
 *   ...
 *
 * ── The three terminal verdict states (spec §3.0 #7, §6.3) ────────────────────────────────────────
 *   • 'PASS'        — a VERDICT line said PASS *and* every parsed CHECK is PASS.
 *   • 'FAIL'        — a VERDICT line was found AND (it said FAIL OR any CHECK said FAIL). The
 *                     "any-CHECK-FAIL ⇒ VERDICT FAIL" rule is enforced HERE, not trusted from the LLM:
 *                     even if the model writes "VERDICT: PASS" while a CHECK reads FAIL, we downgrade
 *                     to FAIL (conservative — a single failed positive-spec check fails the figure).
 *   • 'UNPARSEABLE' — no VERDICT line could be found at all (malformed / refusal / empty / tool-noise).
 *                     DISTINCT from FAIL by design: the scorer (§6.3) maps UNPARSEABLE→FAIL for the
 *                     headline decision (a malformed reply must never silently pass a BAD frame) BUT
 *                     counts it as an abstention in the sensitivity row. Keeping the state distinct
 *                     here is what makes that downstream split possible.
 *
 * A note on robustness: the nested `claude --print` relay can prepend tool-use chatter ("Reading
 * file…"), markdown fences, or bold markers (`**VERDICT:** PASS`) around the contract lines. The
 * matchers below are deliberately lenient on surrounding punctuation/case/whitespace but strict on the
 * PASS|FAIL token itself, so genuine content is parsed while the verdict token is never guessed.
 */

// ── Verdict line: "VERDICT: PASS" / "**Verdict** - FAIL" / "verdict = pass". ──────────────────────
// Tolerates leading markdown/bullet noise, bold stars, and :/-/= separators; case-insensitive.
const VERDICT_RE = /^\s*[*_>\-#\s]*verdict[*_\s]*[:\-=]+\s*\**\s*(pass|fail)\b/i;

// ── Check line: "CHECK foo: PASS — reason" / "- CHECK 2 - FAIL: reason" / "**CHECK x:** pass". ─────
// Group 1 = the check name/index (may be empty, may itself contain hyphens like "axes-present"),
// Group 2 = PASS|FAIL, Group 3 = the reason (optional).
//
// Strategy: anchor on "check", then LAZILY consume any name text (group 1) until a separator
// (`:`/`-`/`–`/`—`/`=`) that is immediately followed by the strict PASS|FAIL token. Because the
// name is `[^\n]*?` (lazy, hyphen-permissive) and the PASS|FAIL token is the regex's true anchor,
// a hyphenated check name ("axes-present") is captured whole — the engine only stops at the
// separator that actually precedes the verdict token. The trailing `[*_\s]*` trims bold/space
// noise off the captured name.
const CHECK_RE = /^\s*[*_>\-#\s]*check\b[*_\s]*([^\n]*?)[*_\s]*[:\-–—=]+\s*\**\s*(pass|fail)\b\s*\**[:\-–—.]?\s*(.*)$/i;

/**
 * Parse one judge stdout blob into a terminal verdict.
 *
 * @param {string} stdout — raw text from `claude --print --output-format text`.
 * @returns {{ verdict:'PASS'|'FAIL'|'UNPARSEABLE', checks: Array<{name:string, status:'PASS'|'FAIL', reason:string}>, raw:string }}
 */
export function parseVerdict(stdout) {
  const raw = typeof stdout === "string" ? stdout : String(stdout ?? "");
  const lines = raw.split(/\r?\n/);

  const checks = [];
  let verdictToken = null; // 'PASS' | 'FAIL' | null  (the LITERAL token the model wrote, if any)

  for (const line of lines) {
    // CHECK lines first: a "CHECK …" line must not also be eaten by the VERDICT matcher
    // (it won't — VERDICT_RE anchors on the word "verdict"), but parse checks explicitly.
    const cm = line.match(CHECK_RE);
    if (cm) {
      const name = cm[1].trim();
      const status = cm[2].toUpperCase() === "PASS" ? "PASS" : "FAIL";
      const reason = (cm[3] || "").trim();
      checks.push({ name, status, reason });
      continue;
    }
    const vm = line.match(VERDICT_RE);
    if (vm) {
      // If multiple VERDICT lines appear (rare LLM repetition), the FIRST one wins — it is the
      // model's headline; later restatements don't override it. (Stable, order-deterministic.)
      if (verdictToken === null) {
        verdictToken = vm[1].toUpperCase() === "PASS" ? "PASS" : "FAIL";
      }
    }
  }

  // No VERDICT line at all ⇒ UNPARSEABLE (distinct from FAIL). Even if CHECK lines were found,
  // a reply missing its headline verdict is malformed against the contract — abstain, don't guess.
  if (verdictToken === null) {
    return { verdict: "UNPARSEABLE", checks, raw };
  }

  // Enforce "any CHECK FAIL ⇒ VERDICT FAIL" HERE — never trust the model's own headline over its
  // own per-check observations. A failed positive-spec check fails the figure regardless of the
  // headline token (the model sometimes writes PASS while listing a FAIL check).
  const anyCheckFailed = checks.some((c) => c.status === "FAIL");
  const verdict = verdictToken === "FAIL" || anyCheckFailed ? "FAIL" : "PASS";

  return { verdict, checks, raw };
}

/**
 * Reduce K per-frame verdicts into ONE decision (spec §6.3). Pure; used by vision-judge + scorer so
 * the reduction rule lives in exactly one place.
 *
 *   • decision = MAJORITY of the K verdicts, where UNPARSEABLE counts as FAIL (conservative) AND
 *     stays in the denominator (a malformed reply never silently passes a BAD frame).
 *   • Ties cannot occur for the default odd K (K=3); if an even K is ever used and the FAIL/PASS
 *     counts tie, the decision is FAIL (safety-biased, same direction as the UNPARSEABLE rule).
 *
 * @param {Array<{verdict:'PASS'|'FAIL'|'UNPARSEABLE'}>} perRun
 * @returns {{ decision:'PASS'|'FAIL', kPass:number, kFail:number, kUnparseable:number,
 *             failVotes:number, passVotes:number, unanimous:boolean }}
 */
export function reduceMajority(perRun) {
  const runs = Array.isArray(perRun) ? perRun : [];
  let kPass = 0;
  let kFail = 0;
  let kUnparseable = 0;
  for (const r of runs) {
    const v = r && r.verdict;
    if (v === "PASS") kPass++;
    else if (v === "FAIL") kFail++;
    else kUnparseable++;
  }
  // UNPARSEABLE folds into the FAIL vote for the decision, but is tracked separately for the
  // abstention rate + the sensitivity row downstream.
  const failVotes = kFail + kUnparseable;
  const passVotes = kPass;
  const decision = failVotes >= passVotes ? "FAIL" : "PASS"; // tie ⇒ FAIL (safety-biased)
  const unanimous = (kPass === runs.length && runs.length > 0) || (failVotes === runs.length && runs.length > 0);
  return { decision, kPass, kFail, kUnparseable, failVotes, passVotes, unanimous };
}
