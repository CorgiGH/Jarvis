/**
 * Plan-6 Task 9 — Romanian learner-facing chrome strings for the practice surfaces
 * (ProofDrill, StepTraceDrill, CodePractice, DeliverableTracker, DrillStack CHECK).
 * Identifiers EN, values RO. All learner-visible copy lives here; no hardcoded RO
 * strings may appear in the practice components (INV-8.3 grep gate).
 */
export const practiceStrings = {
  // ── Generic ──────────────────────────────────────────────────────────────────
  loading: "Se încarcă…",
  noProblems: "Nicio problemă disponibilă momentan.",
  submit: "Trimite",
  giveUp: "Renunț",
  correct: "Corect",
  incorrect: "Incorect",
  feedbackLabel: "Feedback:",

  // ── ProofDrill (REQ-2/3/4) ────────────────────────────────────────────────────
  proofDrillTitle: "Demonstrație pas cu pas",
  proofDrillFrameLabel: "Structura demonstrației",
  proofDrillSubstepsLabel: "Pașii demonstrației",
  proofDrillSubmitBtn: "Trimite demonstrația",
  proofDrillVerdictPass: "Corect",
  proofDrillVerdictFail: "Incorect",
  proofDrillScorePrefix: "Punctaj:",
  proofDrillAllCorrect: "Demonstrație completă!",
  proofDrillPartial: "Verifică pașii marcați.",

  // ── StepTraceDrill (REQ-5/6) ──────────────────────────────────────────────────
  traceDrillTitle: "Urmărire pas cu pas",
  traceDrillSkeletonLabel: "Scheletul algoritmului",
  traceDrillStepPrompt: "Valoarea la pasul",
  traceDrillSubmitStep: "Verifică pasul",
  traceDrillStepCorrect: "Pas corect — continuă.",
  traceDrillStepWrong: "Valoare incorectă — încearcă din nou.",
  traceDrillComplete: "Urmărire completă!",
  traceDrillGiveUpBtn: "Arată răspunsul",

  // ── CodePractice (REQ-7/8) ────────────────────────────────────────────────────
  codePracticeTitle: "Practică cod",
  codePracticeRunBtn: "Rulează",
  codePracticeGradeBtn: "Trimite spre evaluare",
  codePracticeReferenceLabel: "Soluție de referință:",
  codePracticeDegradedBanner: "Rularea codului indisponibilă pe acest server — verificat structural.",

  // ── DeliverableTracker (REQ-18..21) ──────────────────────────────────────────
  deliverableTrackerTitle: "Teme și proiecte",
  deliverableDeadlineUnknown: "Termen necunoscut",
  deliverablePrepDrillsLabel: "Exerciții pregătitoare:",
  deliverableNoPrepDrills: "Niciun exercițiu pregătitor disponibil.",
  deliverableHonestyLine:
    "Aplicația te pregătește pentru predare — nu notează predările reale; profesorul o face.",

  // ── DrillStack CHECK card (REQ-29, E8) ────────────────────────────────────────
  checkCardTitle: "VERIFICĂ",
  checkAttemptPlaceholder: "Răspunsul tău…",
  checkSubmitBtn: "Verifică răspunsul",
  checkGiveUpBtn: "Arată răspunsul",
  checkVerdictCorrect: "Corect!",
  checkVerdictIncorrect: "Incorect.",
} as const;
