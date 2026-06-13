/**
 * Plan-4b Task 4 / §0.9G — Lane A's chrome strings file.
 * All learner-visible strings are in Romanian (RO). Identifiers are in English (EN).
 * This file owns:
 *   - `shellLabels`: the AlgoStepperShell chrome labels passed from FigureReveal
 *   - `figureFallback`: the honest-degraded message when a figure cannot load
 *
 * Task 7 extends this file with the swept chrome of the 8 lesson-surface components.
 *
 * INV-8.3: all learner-visible literals in scoped components must be imported from
 * chromeStrings / practiceStrings / lessonStrings — never hardcoded in the component.
 */

import type { ShellLabels } from "../components/viz/AlgoStepperShell";

/**
 * RO chrome labels for the AlgoStepperShell on the lesson surface.
 * These are the FIRST real `labels=` values passed to the shell (closes the Plan-3 Task-14 carry).
 * EN defaults in the shell remain for the demo gallery — omitting labels there is correct.
 */
export const shellLabels: ShellLabels = {
  frame: "Cadru",
  reset: "resetează",
  share: "🔗 distribuie",
  voiceOn: "🔊 voce pornită",
  voiceOff: "🔇 voce oprită",
  predict: "⚡ Prezice",
  play: "▶ redă",
} as const;

/**
 * Shown in FigureReveal when the instance fetch / parse / registry dispatch fails
 * and the component degrades to the stepped-text fallback path.
 * The learner sees this + the text steps; no console.error escapes (only console.warn).
 */
export const figureFallback =
  "Figura nu s-a putut încărca — pașii sunt afișați ca text.";
