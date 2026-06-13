import "katex/dist/katex.min.css";
import "./index.css";
import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate, useParams, useNavigate } from "react-router-dom";
import { App } from "./App";
import { VizDemoPage } from "./components/viz/VizDemoPage";
import { ThemeProvider } from "./theme/ThemeProvider";
import { BeatOrchestrator } from "./components/lesson/BeatOrchestrator";
import { getLesson } from "./lib/lesson";
import type { ApiLessonReply } from "./lib/lesson";
import { lessonStrings } from "./lib/lessonStrings";
import { MockExamShell } from "./components/MockExamShell";
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

function ExamRoute() {
  const { subject } = useParams<{ subject: string }>();
  return (
    <MockExamShell
      subject={subject ?? ""}
      questions={[]}
      timeLimitSeconds={3600}
      onSubmit={() => {}}
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
