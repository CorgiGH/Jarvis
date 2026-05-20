import "katex/dist/katex.min.css";
import "./index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { App } from "./App";
import { VizDemoPage } from "./components/viz/VizDemoPage";

// App is the shell — it renders the header/nav for every route and switches
// the <main> body by pathname. Routing each path to a bare standalone screen
// (the prior setup) dropped the nav, trapping the user with no in-app way back.
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter basename="/tutor">
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/tasks" element={<App />} />
        <Route path="/settings/trust" element={<App />} />
        <Route path="/review" element={<App />} />
        <Route path="/login" element={<App />} />
        <Route path="/welcome/ai-literacy" element={<App />} />
        <Route path="/viz-demo" element={<VizDemoPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
