/**
 * Plan-4b Task 4 / §0.9G — Lane A's chrome strings file.
 * All learner-visible strings are in Romanian (RO). Identifiers are in English (EN).
 * This file owns:
 *   - `shellLabels`: the AlgoStepperShell chrome labels passed from FigureReveal
 *   - `figureFallback`: the honest-degraded message when a figure cannot load
 *
 * Plan-4b Task 7 extends this file with the swept chrome of the 8 lesson-surface
 * components (§0.9G; INV-8.3). Each section is tagged with the originating component.
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

// ---------------------------------------------------------------------------
// Task 7 sweep — 8 lesson-surface components
// ---------------------------------------------------------------------------

/** Scratchpad (Scratchpad.tsx) */
export const scratchpad = {
  heading: "SCRATCHPAD",
  ariaLabel: "Notițe de lucru — salvate local în browser",
  placeholder: "răspunsuri, fragmente inserate ajung aici…",
  statusSaving: "se salvează…",
  statusSaved: "salvat",
  statusError: "salvare eșuată",
  /** Used in the save-error tooltip title when status=error. */
  saveFailedDefault: "salvare eșuată",
} as const;

/** TaskQuickStart (TaskQuickStart.tsx) */
export const taskQuickStart = {
  loading: "se încarcă spațiul de lucru…",
  sectionQuickStart: "START RAPID",
  noTasksHint: "Nicio sarcină încă. Alege una pentru a începe — Jarvis adaugă context per conversație.",
  tasksHint: (count: number) =>
    `Ai ${count} sarcin${count === 1 ? "ă activă" : "i active"}. Alege una sau începe un subiect nou.`,
  sectionYourTasks: "SARCINILE TALE",
  sectionPresets: "SARCINI PREDEFINITE",
  creating: "se creează…",
  footerHint: "Sarcină personalizată?",
  footerLink: "/tutor/tasks",
} as const;

/** ConceptDrawer (ConceptDrawer.tsx) */
export const conceptDrawer = {
  closeAriaLabel: "Închide detaliile conceptului",
  loading: "se încarcă…",
  empty: "Nicio referință trecută pentru acest concept",
  loadErrorPrefix: "referințe indisponibile — ",
} as const;

/** KnowledgeLedger (KnowledgeLedger.tsx) */
export const knowledgeLedger = {
  heading: "REGISTRU CUNOȘTINȚE",
  closeAriaLabel: "Închide registrul",
  loading: "se încarcă…",
  empty: "nicio lacună înregistrată",
  loadErrorPrefix: "imposibil de încărcat lacunele — ",
  openTaskAriaLabel: (topic: string) => `Deschide sarcina pentru lacuna: ${topic}`,
  capNotice: (top: number, total: number, hidden: number) =>
    `afișez primele ${top} din ${total} după reutilizare · ${hidden} ascunse`,
} as const;

/** Sidekick (Sidekick.tsx) */
export const sidekick = {
  heading: "ASISTENT",
  expandAriaLabel: "Extinde asistentul",
  collapseAriaLabel: "Restrânge asistentul",
  idleHint:
    "Selectează orice text din spațiul de lucru — apare un buton mic <?> lângă selecție; apasă-l pentru a întreba asistentul.",
  loading: "se gândește…",
  error: "(LLM indisponibil; limită de rată?)",
  quotedPrefix: "> citat: ",
} as const;

/** ChatPane (ChatPane.tsx) */
export const chatPane = {
  inputPlaceholder: "mesaj · ctrl+enter trimite",
  sendButton: "TRIMITE",
  readOnlyBadge: "MOD CITIRE",
  refreshing: "se reîmprospătează…",
  previouslyFlagged: (count: number) => `MARCATE ANTERIOR (${count})`,
  headingPrefix: "ASISTENT · SARCINA",
  /** aria-label for the chat message log region (WCAG landmark). */
  chatMessages: "Mesaje de chat",
  /** Screen-reader label for the chat input (sr-only label, AT-visible). */
  chatInputLabel: "Mesaj Jarvis",
} as const;

/** TrustSettings (TrustSettings.tsx) */
export const trustSettings = {
  pageHeading: "GRANTURI DE ÎNCREDERE",
  pageDescription:
    "Fiecare grant permite daemonului să scrie în una sau mai multe căi pentru un timp limitat + număr de apeluri. Implicit 1h / 10 apeluri. Limită 8h. Revocare oricând.",
  sectionNewGrant: "GRANT NOU",
  scopeLabel: "Domeniu (glob file://)",
  ttlLabel: "Minute TTL (max 480 = 8h)",
  maxCallsLabel: "Apeluri max",
  grantButton: "ACORDĂ",
  granting: "se acordă…",
  activeSection: (count: number) => `ACTIVE (${count})`,
  loading: "se încarcă…",
  empty: "niciun grant — completează formularul de mai sus pentru a adăuga unul",
  revokeButton: "REVOCĂ",
  revokedBadge: "REVOCAT",
} as const;
