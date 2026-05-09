import { useEffect, useRef, useState } from "react";
import { useSearchParams, Link, useNavigate } from "react-router-dom";
import { TutorWorkspace } from "./components/TutorWorkspace";
import { TaskQuickStart } from "./components/TaskQuickStart";
import { jarvisFetch } from "./lib/api";

const LAST_TASK_KEY = "jarvis.lastTaskId";

/** Bootstrap a tutor session via /api/v1/tutor/auto-session — sets
 *  jarvis_session + csrf cookies if missing. Idempotent. */
async function ensureTutorSession(): Promise<void> {
  try {
    const csrf = document.cookie.match(/(?:^|;\s*)csrf=([^;]+)/)?.[1];
    if (csrf) return;
    await fetch("/api/v1/tutor/auto-session", { credentials: "include" });
  } catch (_) {}
}

export function App() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const explicitTaskId = params.get("taskId");
  const pickMode = params.get("pick") === "1";  // explicit "go to QuickStart" flag
  const [sessionReady, setSessionReady] = useState(false);

  useEffect(() => {
    ensureTutorSession().finally(() => setSessionReady(true));
  }, []);

  // Cold-start ONLY: restore last-used taskId from localStorage.
  // Subsequent navigation (workspace link → /) does NOT re-restore;
  // user can escape back to QuickStart by clicking workspace.
  const restoredOnce = useRef(false);
  useEffect(() => {
    if (restoredOnce.current) {
      // Persist the latest explicit taskId so cold-reload returns here.
      if (explicitTaskId) {
        try { localStorage.setItem(LAST_TASK_KEY, explicitTaskId); } catch (_) {}
      }
      return;
    }
    restoredOnce.current = true;
    if (explicitTaskId) {
      try { localStorage.setItem(LAST_TASK_KEY, explicitTaskId); } catch (_) {}
      return;
    }
    if (pickMode) return;  // user explicitly asked for QuickStart
    try {
      const last = localStorage.getItem(LAST_TASK_KEY);
      if (last && last !== "TEST-TASK-A") {
        setParams({ taskId: last }, { replace: true });
      }
    } catch (_) {}
  }, [explicitTaskId, pickMode, setParams]);

  const taskId = explicitTaskId ?? "TEST-TASK-A";
  const isDefault = taskId === "TEST-TASK-A" || pickMode;

  const [taskExists, setTaskExists] = useState<boolean | null>(null);
  useEffect(() => {
    if (isDefault) { setTaskExists(false); return; }
    let cancelled = false;
    jarvisFetch("/api/v1/tasks")
      .then(async r => {
        if (cancelled) return;
        if (!r.ok) { setTaskExists(false); return; }
        const data: { tasks: { id: string }[] } = await r.json();
        setTaskExists(data.tasks.some(t => t.id === taskId));
      })
      .catch(() => { if (!cancelled) setTaskExists(false); });
    return () => { cancelled = true; };
  }, [taskId, isDefault]);

  const showQuickStart = isDefault || taskExists === false;

  function pickAnotherTask() {
    try { localStorage.removeItem(LAST_TASK_KEY); } catch (_) {}
    navigate("/?pick=1", { replace: true });
  }

  return (
    <div className="h-dvh flex flex-col">
      <header className="bg-black text-yellow-300 px-4 py-3 flex items-center justify-between border-b-4 border-yellow-300">
        <div className="flex items-center gap-3 min-w-0">
          <span className="text-lg font-bold tracking-widest">JARVIS · TUTOR</span>
          {!showQuickStart && (
            <>
              <span className="text-xs tracking-widest text-yellow-200/80 truncate">{taskId}</span>
              <button
                onClick={pickAnotherTask}
                data-testid="pick-another-task-btn"
                className="text-xs tracking-widest bg-yellow-300 text-black px-2 py-0.5 hover:bg-yellow-200"
              >
                × close
              </button>
            </>
          )}
        </div>
        <nav className="flex items-center gap-4 text-xs font-bold tracking-widest">
          <Link to="/?pick=1" className="hover:underline">workspace</Link>
          <Link to="/tasks" className="hover:underline">tasks</Link>
          <Link to="/settings/trust" className="hover:underline">trust</Link>
        </nav>
      </header>
      <main className="flex-1 min-h-0 overflow-hidden bg-white">
        {!sessionReady
          ? <div className="p-6 font-mono text-sm text-black/60">setting up tutor session…</div>
          : showQuickStart
            ? <TaskQuickStart />
            : <TutorWorkspace pdfUrl="/tutor/test-task.pdf" taskId={taskId} />}
      </main>
    </div>
  );
}
