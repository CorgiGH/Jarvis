// tools/surface-y-tripwire.mjs
//
// Behavioral-competence tripwire — postprocesses a Surface Y transcript and
// flags runs where the persona shows hyper-competence (no confusion, no
// sidekick use, first-try-flawless submit) AND surfaces ZERO findings.
// Diagnostic only — never blocks.
//
// Spec: docs/superpowers/specs/2026-05-15-surface-claude-cli-provider-design.md
// Council 1 (mandate): .claude/council-cache/council-1778839098-y-competence-band.md
// Council 2 (refinement): .claude/council-cache/council-1778881175-y-tripwire-design.md
//   First Principles 2x2: only (competent transcript ∧ zero findings) is invalid;
//   a competent persona that surfaces friction findings is high-signal.

const CONFUSION_KEYWORDS = ["confused", "don't know", "dont know", "unclear", "[leak?]", "stuck", "no idea"];

const DEFAULT_THRESHOLDS = {
  min_steps_for_zero_confusion_flag: 6,
  submit_step_index_max_for_flawless_flag: 3,
};

/**
 * Flag a Y run as suspect if persona shape looks competent AND no friction
 * findings were surfaced.
 *
 * @param {Array} transcript — persona step list.
 * @param {number} [findingsCount=0] — count of UX-friction findings surfaced
 *   (defined by surface-y.mjs as: `transcript.filter(t => t.observation &&
 *   t.action !== "error" && t.action !== "stuck").length`). Default 0 means
 *   "treat as if no findings surfaced" — i.e. apply the strictest gate.
 * @param {object} [thresholds=DEFAULT_THRESHOLDS] — magic-number knobs.
 */
export function flagSuspectRun(transcript, findingsCount = 0, thresholds = DEFAULT_THRESHOLDS) {
  const signals = computeSignals(transcript);
  const rationale = [];

  // Path A: zero-confusion AND zero-ask_sidekick across a long run.
  // AND-gated on findings_count == 0 per council 1778881175 first-principles
  // refinement: a competent persona that nevertheless surfaces friction
  // findings is high-signal, not invalid.
  if (
    transcript.length >= thresholds.min_steps_for_zero_confusion_flag &&
    signals.ask_sidekick_count === 0 &&
    signals.confusion_step_count === 0 &&
    findingsCount === 0
  ) {
    rationale.push(
      `Run ran ${transcript.length} steps with zero ask_sidekick AND zero confusion-keyword observations AND zero findings — ` +
      `a real naive student would have used at least one, OR a competent persona that found UX friction would be high-signal.`,
    );
  }

  // Path B: first submit at step <= N with no prior friction.
  // Same AND-gate: clean fast submit IS suspect only if no findings surfaced.
  const submitIdx = transcript.findIndex(t => t.action === "submit");
  if (
    submitIdx !== -1 &&
    submitIdx <= thresholds.submit_step_index_max_for_flawless_flag &&
    transcript.slice(0, submitIdx + 1).every(t =>
      t.action !== "ask_sidekick" && t.action !== "give_up" && !t.error
    ) &&
    findingsCount === 0
  ) {
    rationale.push(
      `submit at step ${submitIdx + 1} with no prior ask_sidekick, give_up, or error, AND zero findings — ` +
      `clean first-try answer with nothing surfaced.`,
    );
  }

  return {
    suspect: rationale.length > 0,
    signals,
    thresholds,
    findings_count: findingsCount,
    rationale,
  };
}

function computeSignals(transcript) {
  const ask_sidekick_count = transcript.filter(t => t.action === "ask_sidekick").length;
  const confusion_step_count = transcript.filter(t => {
    const obs = (t.observation || "").toLowerCase();
    return (
      CONFUSION_KEYWORDS.some(k => obs.includes(k)) ||
      t.action === "give_up" ||
      t.action === "ask_sidekick"
    );
  }).length;
  return { ask_sidekick_count, confusion_step_count };
}
