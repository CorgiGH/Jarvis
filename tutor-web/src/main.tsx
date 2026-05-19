import "katex/dist/katex.min.css";
import "./index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { App } from "./App";
import { TrustSettings } from "./components/TrustSettings";
import { TasksScreen } from "./components/TasksScreen";
import { FsrsReview } from "./components/FsrsReview";
import { VizDemoPage } from "./components/viz/VizDemoPage";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter basename="/tutor">
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/tasks" element={<TasksScreen />} />
        <Route path="/settings/trust" element={<TrustSettings />} />
        <Route path="/review" element={<FsrsReview streak={0} />} />
        <Route path="/viz-demo" element={<VizDemoPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
