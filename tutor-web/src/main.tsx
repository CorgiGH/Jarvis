import "katex/dist/katex.min.css";
import "./index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate, useParams } from "react-router-dom";
import { App } from "./App";
import { VizDemoPage } from "./components/viz/VizDemoPage";
import { ThemeProvider } from "./theme/ThemeProvider";
import { LessonScreen } from "./components/LessonScreen";
import { MockExamShell } from "./components/MockExamShell";
import { DayOfShell } from "./components/DayOfShell";
import { OnboardingShell } from "./components/OnboardingShell";
import { PlacementShell } from "./components/PlacementShell";

function LessonScreenRoute() {
  const { kcId } = useParams<{ kcId: string }>();
  return <LessonScreen kcId={kcId ?? ""} />;
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
          <Route path="/lesson/:kcId" element={<LessonScreenRoute />} />
          <Route path="/exam/:subject" element={<ExamRoute />} />
          <Route path="/day-of" element={<DayOfShell />} />
          <Route path="/welcome" element={<OnboardingShell onComplete={() => { window.location.href = '/tutor/'; }} />} />
          <Route path="/placement" element={<PlacementShell onComplete={() => { window.location.href = '/tutor/'; }} />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
);
