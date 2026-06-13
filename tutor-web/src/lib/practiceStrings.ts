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

  // ── MockExam additive (REQ-11..17, Task 11) ──────────────────────────────────
  mockExamPhaseLabel: "Faza curentă:",
  mockExamPhaseMaterialsLabel: "Materiale permise:",
  mockExamPhaseProgress: "din",
  mockExamRubricResultTitle: "Barem pe puncte",
  mockExamRubricPointsOf: "din",
  mockExamSyntheticTag: "Subiect generat — nu provine dintr-un examen real",
  mockExamSubQuestionOrderHint: "Sub-întrebările se rezolvă în ordine (b folosește rezultatul de la a).",
  mockExamPhaseAdvanceBtn: "Faza următoare",

  // ── DrillStack CHECK card (REQ-29, E8) ────────────────────────────────────────
  checkCardTitle: "VERIFICĂ",
  checkAttemptPlaceholder: "Răspunsul tău…",
  checkSubmitBtn: "Verifică răspunsul",
  checkGiveUpBtn: "Arată răspunsul",
  checkVerdictCorrect: "Corect!",
  checkVerdictIncorrect: "Incorect.",

  // ── DrillStack chrome (card titles + button copy moved from inline, Task 12) ──
  drillCardTitle: "③ DRILL · YOUR TURN",
  workedCardTitle: "② WORKED EXAMPLE",
  definitionCardTitle: "① DEFINITION",
  checkCardTitleFull: "④ CHECK · TRANSFER",
  drillCheckAnswerBtn: "VERIFICĂ RĂSPUNSUL",
  drillGradingBtn: "EVALUARE…",
  drillGiveUpBtn: "renunț — arată soluția",
  drillAttemptPlaceholder: "Scrie răspunsul tău…",
  drillNetworkError: "Eroare de rețea — încearcă din nou.",
  drillConfidenceLabel: "cât de sigur ești?",
  drillPredictionLabel: "prezice în limbaj simplu (opțional) — cum ar trebui să arate răspunsul, înainte de a-l scrie?",
  drillPredictionHint: "efect de generare: chiar și o presupunere greșită ÎNAINTE de a încerca fixează mai bine decât citirea pasivă (Slamecka 1978).",
  drillPredictionPlaceholder: "ex.: histograma ar trebui să fie simetrică în jurul lui 0, ascuțită, cu cozi mai grele pe măsură ce b crește",
} as const;
