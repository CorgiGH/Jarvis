import "katex/dist/katex.min.css";
import "./index.css";
import { StrictMode, useCallback, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate, useParams, useNavigate } from "react-router-dom";
import { App } from "./App";
import { VizDemoPage } from "./components/viz/VizDemoPage";
import { LectieSelectSortDemo } from "./components/viz/families/LectieSelectSortDemo";
import { LectieSelectSortBarsDemo } from "./components/viz/families/LectieSelectSortBarsDemo";
import { LectieSelectSortHelpDemo } from "./components/viz/families/LectieSelectSortHelpDemo";
import { LectieMergeSortDemo } from "./components/viz/families/LectieMergeSortDemo";
import { LectieGaussDemo } from "./components/viz/families/LectieGaussDemo";
import { MergeCompareDemo } from "./components/viz/families/MergeCompareDemo";
import { ThemeProvider } from "./theme/ThemeProvider";
import { BeatOrchestrator } from "./components/lesson/BeatOrchestrator";
import { getLesson } from "./lib/lesson";
import type { ApiLessonReply } from "./lib/lesson";
import { lessonStrings } from "./lib/lessonStrings";
import { MockExamShell } from "./components/MockExamShell";
import type { MockExamPhase, MockExamRubricVerdict } from "./components/MockExamShell";
import { DayOfShell } from "./components/DayOfShell";
import { OnboardingShell } from "./components/OnboardingShell";
import { PlacementShell } from "./components/PlacementShell";
import { ProofDrill } from "./components/practice/ProofDrill";
import { StepTraceDrill } from "./components/practice/StepTraceDrill";
import { CodePractice } from "./components/practice/CodePractice";
import { DeliverableTracker } from "./components/practice/DeliverableTracker";
import { listPracticeProblems } from "./lib/practiceApi";
import type { PracticeProblem } from "./lib/practiceApi";
import { practiceStrings } from "./lib/practiceStrings";
import { jarvisFetch } from "./lib/api";

/**
 * Plan-3 Task 6 — the lesson route now mounts the BeatOrchestrator. It loads the beats payload
 * (GET /api/v1/lesson/{kcId}); null/404/beats-absent → honest-degraded unavailable message.
 * The concrete drill-handoff navigation target is wired in Task 10 (fallback: /tutor/oggi).
 */
function BeatOrchestratorRoute() {
  const { kcId } = useParams<{ kcId: string }>();
  const navigate = useNavigate();
  const id = kcId ?? "";
  const [lesson, setLesson] = useState<ApiLessonReply | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    setLesson(undefined);
    getLesson(id)
      .then((r) => { if (!cancelled) setLesson(r); })
      .catch(() => { if (!cancelled) setLesson(null); });
    return () => { cancelled = true; };
  }, [id]);

  if (lesson === undefined) {
    return <div data-testid="lesson-screen" className="flex-1 p-6 font-mono text-xs text-page-fg/50 tracking-widest">{lessonStrings.loading}</div>;
  }
  if (lesson === null || !lesson.beats) {
    return (
      <div data-testid="lesson-screen" className="flex-1 p-6 font-mono">
        <div data-testid="lesson-unavailable" className="border-2 border-border-strong p-4 text-xs text-page-fg/60 tracking-wide">
          {lessonStrings.unavailable}
        </div>
      </div>
    );
  }
  // Task 10 replaces the fallback nav with the verified drill route.
  return <BeatOrchestrator kcId={id} lesson={lesson} onComplete={() => navigate("/oggi")} />;
}

/**
 * Plan-6 Task 13 (fix-round) — ExamRoute upgraded to call the real mock-exam start API so the
 * additive REQ-17 selectors (mock-exam-phase, mock-exam-synthetic-tag, mock-exam-rubric-result,
 * mock-exam-question) are exercisable in the e2e suite. A `?format` query param selects the
 * format ID; defaults to "<SUBJECT>-standard" (e.g. PA-standard for /exam/PA). Fully wired:
 * start → phase advance → submit → rubric results (REQ-11..17 browser path).
 */
function ExamRoute() {
  const { subject } = useParams<{ subject: string }>();
  const [questions, setQuestions] = useState<string[]>([]);
  const [examId, setExamId] = useState<string | null>(null);
  const [syntheticTag, setSyntheticTag] = useState<boolean | undefined>(undefined);
  const [phase, setPhase] = useState<MockExamPhase | undefined>(undefined);
  const [rubricResult, setRubricResult] = useState<MockExamRubricVerdict[] | undefined>(undefined);
  const [durationSeconds, setDurationSeconds] = useState(3600);
  const [loading, setLoading] = useState(true);

  const subj = subject ?? "";
  // Use format from query param, fallback to "<SUBJECT>-standard".
  const formatId = new URLSearchParams(window.location.search).get("format") ?? `${subj}-standard`;

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    jarvisFetch("/api/v1/mock-exam/start", {
      method: "POST",
      body: JSON.stringify({ subject: subj, n: 1, format_id: formatId }),
    })
      .then((r) => r.json())
      .then((body: Record<string, unknown>) => {
        if (cancelled) return;
        const qs = (body.questions as Array<{ question_id: string; kc_id: string; statement_ro?: string }> | undefined) ?? [];
        setExamId((body.exam_id as string | undefined) ?? null);
        setQuestions(qs.map((q) => q.statement_ro ?? q.kc_id));
        setSyntheticTag((body.synthetic_tag as boolean | undefined) ?? undefined);
        // Phase shape mirrors MockExamPhase (camelCase mapped from snake_case).
        const p = body.phase as { phase_index: number; label_ro: string; materials_allowed_ro: string; phase_count: number } | undefined;
        if (p) setPhase({ phaseIndex: p.phase_index, labelRo: p.label_ro, materialsAllowedRo: p.materials_allowed_ro, phaseCount: p.phase_count });
        const timer = body.timer as { duration_seconds: number } | undefined;
        if (timer) setDurationSeconds(timer.duration_seconds);
        setLoading(false);
      })
      .catch(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [subj, formatId]);

  const handleAdvancePhase = useCallback(() => {
    if (!examId) return;
    jarvisFetch(`/api/v1/mock-exam/${encodeURIComponent(examId)}/phase`, { method: "POST", body: JSON.stringify({}) })
      .then((r) => r.json())
      .then((body: Record<string, unknown>) => {
        const p = body.phase as { phase_index: number; label_ro: string; materials_allowed_ro: string; phase_count: number } | undefined;
        if (p) setPhase({ phaseIndex: p.phase_index, labelRo: p.label_ro, materialsAllowedRo: p.materials_allowed_ro, phaseCount: p.phase_count });
      })
      .catch(() => {});
  }, [examId]);

  const handleSubmit = useCallback((answers: string[]) => {
    if (!examId) return;
    const qs = answers.map((response, i) => ({ question_id: `q-${i}`, response }));
    jarvisFetch(`/api/v1/mock-exam/${encodeURIComponent(examId)}/submit`, {
      method: "POST",
      body: JSON.stringify({ exam_id: examId, answers: qs }),
    })
      .then((r) => r.json())
      .then((body: Record<string, unknown>) => {
        const rr = body.rubric_result as MockExamRubricVerdict[] | undefined;
        if (rr && rr.length > 0) setRubricResult(rr);
      })
      .catch(() => {});
  }, [examId]);

  if (loading) {
    return <div className="p-6 text-page-fg/50 font-mono text-xs">{practiceStrings.loading}</div>;
  }
  return (
    <MockExamShell
      subject={subj}
      questions={questions}
      timeLimitSeconds={durationSeconds}
      syntheticTag={syntheticTag}
      phase={phase}
      rubricResult={rubricResult}
      onAdvancePhase={phase && phase.phaseIndex < phase.phaseCount - 1 ? handleAdvancePhase : undefined}
      onSubmit={handleSubmit}
    />
  );
}

/**
 * Plan-6 Task 9 — Practice routes: ProofDrill + StepTraceDrill.
 * Loads the first available problem for the given subject+surface from
 * GET /api/v1/practice/problems and hands it to the surface component.
 */
function ProofDrillRoute() {
  const { subject } = useParams<{ subject: string }>();
  const [problem, setProblem] = useState<PracticeProblem | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    setProblem(undefined);
    listPracticeProblems(subject ?? "", "proof")
      .then((r) => {
        if (!cancelled) setProblem(r.problems[0] ?? null);
      })
      .catch(() => {
        if (!cancelled) setProblem(null);
      });
    return () => { cancelled = true; };
  }, [subject]);

  if (problem === undefined) {
    return <div className="p-6 text-page-fg/50">{practiceStrings.loading}</div>;
  }
  if (problem === null) {
    return <div className="p-6 text-page-fg/50">{practiceStrings.noProblems}</div>;
  }
  return <ProofDrill problem={problem} />;
}

function StepTraceDrillRoute() {
  const { subject } = useParams<{ subject: string }>();
  const [problem, setProblem] = useState<PracticeProblem | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    setProblem(undefined);
    listPracticeProblems(subject ?? "", "trace")
      .then((r) => {
        if (!cancelled) setProblem(r.problems[0] ?? null);
      })
      .catch(() => {
        if (!cancelled) setProblem(null);
      });
    return () => { cancelled = true; };
  }, [subject]);

  if (problem === undefined) {
    return <div className="p-6 text-page-fg/50">{practiceStrings.loading}</div>;
  }
  if (problem === null) {
    return <div className="p-6 text-page-fg/50">{practiceStrings.noProblems}</div>;
  }
  return <StepTraceDrill problem={problem} />;
}

/**
 * Plan-6 Task 10 — CodePractice route: loads the first code-surface problem for the subject
 * and renders CodePractice.
 */
function CodePracticeRoute() {
  const { subject } = useParams<{ subject: string }>();
  const [problem, setProblem] = useState<PracticeProblem | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    setProblem(undefined);
    listPracticeProblems(subject ?? "", "code")
      .then((r) => {
        if (!cancelled) setProblem(r.problems[0] ?? null);
      })
      .catch(() => {
        if (!cancelled) setProblem(null);
      });
    return () => { cancelled = true; };
  }, [subject]);

  if (problem === undefined) {
    return <div className="p-6 text-page-fg/50">{practiceStrings.loading}</div>;
  }
  if (problem === null) {
    return <div className="p-6 text-page-fg/50">{practiceStrings.noProblems}</div>;
  }
  return <CodePractice problem={problem} />;
}

// App is the shell — it renders the header/nav for every route and switches
// the <main> body by pathname. Routing each path to a bare standalone screen
// (the prior setup) dropped the nav, trapping the user with no in-app way back.
// ThemeProvider wraps everything so the palette choice recolors app-wide.
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <BrowserRouter basename="/tutor">
        <Routes>
          <Route path="/" element={<App />} />
          <Route path="/tasks" element={<App />} />
          <Route path="/settings/trust" element={<App />} />
          <Route path="/me" element={<App />} />
          <Route path="/review" element={<App />} />
          <Route path="/login" element={<App />} />
          <Route path="/welcome/ai-literacy" element={<App />} />
          <Route path="/viz-demo" element={<VizDemoPage />} />
          <Route path="/lectie-selectsort" element={<LectieSelectSortDemo />} />
          <Route path="/lectie-selectsort-bars" element={<LectieSelectSortBarsDemo />} />
          <Route path="/lectie-selectsort-help" element={<LectieSelectSortHelpDemo />} />
          <Route path="/lectie-mergesort" element={<LectieMergeSortDemo />} />
          <Route path="/lectie-gauss" element={<LectieGaussDemo />} />
          <Route path="/merge-compare" element={<MergeCompareDemo />} />
          <Route path="/oggi" element={<App />} />
          <Route path="/subjects" element={<App />} />
          <Route path="/lesson/:kcId" element={<BeatOrchestratorRoute />} />
          <Route path="/exam/:subject" element={<ExamRoute />} />
          <Route path="/day-of" element={<DayOfShell />} />
          <Route path="/welcome" element={<OnboardingShell onComplete={() => { window.location.href = '/tutor/'; }} />} />
          <Route path="/placement" element={<PlacementShell onComplete={() => { window.location.href = '/tutor/'; }} />} />
          {/* Plan-6 Task 9 — Practice surfaces I: ProofDrill + StepTraceDrill */}
          <Route path="/practice/proof/:subject" element={<ProofDrillRoute />} />
          <Route path="/practice/trace/:subject" element={<StepTraceDrillRoute />} />
          {/* Plan-6 Task 10 — Practice surfaces II: CodePractice + DeliverableTracker */}
          <Route path="/practice/code/:subject" element={<CodePracticeRoute />} />
          <Route path="/practice/deliverables" element={<DeliverableTracker />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
);
