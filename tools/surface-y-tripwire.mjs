// tools/surface-y-tripwire.mjs
//
// Behavioral-competence tripwire — postprocesses a Surface Y transcript and
// flags runs where the persona shows hyper-competence (no confusion, no
// sidekick use, first-try-flawless submit). Diagnostic only — never blocks.
//
// Spec: docs/superpowers/specs/2026-05-15-surface-claude-cli-provider-design.md
// Council: .claude/council-cache/council-1778839098-y-competence-band.md

const CONFUSION_KEYWORDS = ["confused", "don't know", "dont know", "unclear", "[leak?]", "stuck", "no idea"];

const DEFAULT_THRESHOLDS = {
  min_steps_for_zero_confusion_flag: 6,
  submit_step_index_max_for_flawless_flag: 3,
};

export function flagSuspectRun(transcript, thresholds = DEFAULT_THRESHOLDS) {
  const signals = computeSignals(transcript);
  const rationale = [];

  // Path A: zero-confusion AND zero-ask_sidekick across a long run.
  if (
    transcript.length >= thresholds.min_steps_for_zero_confusion_flag &&
    signals.ask_sidekick_count === 0 &&
    signals.confusion_step_count === 0
  ) {
    rationale.push(
      `Run ran ${transcript.length} steps with zero ask_sidekick AND zero confusion-keyword observations — ` +
      `a real naive student would have used at least one.`,
    );
  }

  // Path B: first submit at step <= N with no prior friction.
  const submitIdx = transcript.findIndex(t => t.action === "submit");
  if (
    submitIdx !== -1 &&
    submitIdx <= thresholds.submit_step_index_max_for_flawless_flag &&
    transcript.slice(0, submitIdx + 1).every(t =>
      t.action !== "ask_sidekick" && t.action !== "give_up" && !t.error
    )
  ) {
    rationale.push(
      `submit at step ${submitIdx + 1} with no prior ask_sidekick, give_up, or error — ` +
      `model produced a clean first-try answer.`,
    );
  }

  return {
    suspect: rationale.length > 0,
    signals,
    thresholds,
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
