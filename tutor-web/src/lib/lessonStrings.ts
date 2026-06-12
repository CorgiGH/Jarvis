/**
 * Plan-3 Task 6 — Romanian learner-facing chrome strings for the BeatOrchestrator surface only
 * (spec §8.2; the app-wide sweep is Plan 4). Every string the learner SEES on the lesson route
 * lives here so the §8.3 INV-8.3 chrome grep (no hardcoded EN learner literals outside the strings
 * file) stays green for this surface. Beat CONTENT (prompts/options/callouts) is per-KC data and
 * comes from the wire payload, NOT from here.
 */
export const lessonStrings = {
  /** Next/continue control (the gate button). */
  next: "Continuă",
  /** Final-beat label on the Next button. */
  finish: "Termină lecția",
  /** Back control. */
  back: "Înapoi",
  /** Gate-blocked message when predict not yet committed. */
  gateAnswer: "Răspunde ca să continui",
  /** Gate-blocked message on a reveal not yet stepped to the end / dwell not met. */
  gateWatch: "Parcurge toți pașii ca să continui",
  /** Submit control on attempt/check. */
  submit: "Trimite",
  /** Step counter prefix, rendered "pas k/N". */
  step: "pas",
  /** Beat-section eyebrow labels (paired with the glyphs). */
  predictLabel: "PREZICE",
  attemptLabel: "ÎNCEARCĂ",
  revealLabel: "PRIVEȘTE",
  nameLabel: "ACUM ARE UN NUME",
  checkLabel: "VERIFICĂ",
  /** Reveal echo banner prefix: "Tu ai prezis: …". */
  echoPrefix: "Tu ai prezis:",
  /** NameBeat callout sub-labels. */
  definitionLabel: "Definiție",
  invariantLabel: "Invariant",
  whyLabel: "De ce contează",
  /** Loading + completion. */
  loading: "Se încarcă…",
  complete: "Lecție completă",
  completeBody: "Bine făcut. Hai să exersezi acum acest concept.",
  /** The drill-handoff control on the completion screen. */
  handoff: "Începe exercițiile",
  /** Honest-degraded fallback when the KC has no servable beats / 404. */
  unavailable: "Această lecție nu este încă disponibilă — revin mai târziu.",
} as const;

/** The five beat glyphs, indexed by beat ordinal in the served plan (NOT by beat kind). */
export const BEAT_GLYPHS = ["①", "②", "③", "④", "⑤"] as const;

/** Beat-kind → eyebrow label (RO). */
export function beatKindLabel(kind: string): string {
  switch (kind) {
    case "predict": return lessonStrings.predictLabel;
    case "attempt": return lessonStrings.attemptLabel;
    case "reveal": return lessonStrings.revealLabel;
    case "name": return lessonStrings.nameLabel;
    case "check": return lessonStrings.checkLabel;
    default: return kind;
  }
}
