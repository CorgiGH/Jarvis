// Surface X invariant catalog (V1).
//
// Each invariant declares what MUST hold on a slice of the captured tutor
// event log. `scope(events)` returns the subset of events relevant to the
// invariant — Surface X's grader walks each invariant's scope, sends it
// to the LLM-as-rubric-judge with the invariant statement, and tags the
// session PASS / FAIL / INFO per the classification.
//
// 8 PASS_FAIL invariants gate the advisory deploy hook. 2 INFO invariants
// surface latency observations but never gate.

export const INVARIANTS = [
  {
    id: "INV-01",
    statement: "PREDICT textarea is filled before R-CODE textarea accepts input. The drill_grade event must be preceded in the same session by a page_nav or interaction event that captures the predict field non-empty.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => ["page_nav", "drill_grade"].includes(e.event_type)),
  },
  {
    id: "INV-02",
    statement: "When a drill_grade response indicates correctness=false, the response body cites at least one specific failed rubric criterion by name (the rubric_chips structure must include named failures).",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => e.event_type === "drill_grade"),
  },
  {
    id: "INV-03",
    statement: "When a sidekick_ask carries a selection that is >=0.7 Jaccard-similar to the corresponding drill's statement, the response model_resolved must be '(drill-self-paste-guard)' (the synthetic guard model name) - not a real LLM model.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => e.event_type === "sidekick_ask"),
  },
  {
    id: "INV-04",
    statement: "Within an active-drill window (between a page_nav that opens a drill and the corresponding drill_grade), no sidekick_ask event fires that targets the drill body via the inline-chip flow. Identified by sidekick_ask metadata.source = 'inline-chip' AND task state has active DRILL card.",
    classification: "PASS_FAIL",
    scope: (events) => events,
  },
  {
    id: "INV-05",
    statement: "When a sidekick_ask carries a corpus-eligible selection (Romanian / subject-vocab tokens), the response retrieved_context_summary has >=1 entry AND the llm_output_full contains at least one '(src: _extras/<subject>/...)' marker.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => e.event_type === "sidekick_ask"),
  },
  {
    id: "INV-06",
    statement: "Locked drill cards (WORKED / DEFINITION / CHECK) do not register interaction events while an active DRILL card is in 'open' state for the same task. Pre-mature open attempts must be rejected/no-op.",
    classification: "PASS_FAIL",
    scope: (events) => events,
  },
  {
    id: "INV-07",
    statement: "PDF stepper navigation (A1 -> A2 -> ... -> AN) preserves drill state: the drill_grade for problem k uses the predict/rcode captured before the page_nav to problem k+1.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => ["page_nav", "drill_grade"].includes(e.event_type)),
  },
  {
    id: "INV-08",
    statement: "Drill rubric_chip text in drill_grade.llm_output_full is human-readable, NOT raw snake_case. Specifically, no rubric_chip key value in the rendered response contains an alphanumeric substring matching /\\b[a-z]+_[a-z_]+\\b/ as user-facing text. The motivating bug example is 'uses_rlaplace_or_inverse_cdf_sampler'.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => e.event_type === "drill_grade"),
  },
  {
    id: "INV-09",
    statement: "[INFO] Sidekick latency observed. Surface latency_p95_ms per session for sidekick_ask events. Flag with status=INFO when p95 > 8000 ms with corpus-eligible request. Never gate.",
    classification: "INFO",
    scope: (events) => events.filter(e => e.event_type === "sidekick_ask"),
  },
  {
    id: "INV-10",
    statement: "[INFO] Grader latency observed. Surface latency_p95_ms per session for drill_grade events. Flag with status=INFO when p95 > 12000 ms. Never gate.",
    classification: "INFO",
    scope: (events) => events.filter(e => e.event_type === "drill_grade"),
  },
];

export function scopeFor(invId, events) {
  const inv = INVARIANTS.find(i => i.id === invId);
  if (!inv) throw new Error(`Unknown invariant: ${invId}`);
  return inv.scope(events);
}
