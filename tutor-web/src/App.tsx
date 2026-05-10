import { useEffect, useRef, useState } from "react";
import { useSearchParams, Link, useNavigate, useLocation } from "react-router-dom";
import { TutorWorkspace } from "./components/TutorWorkspace";
import { TaskQuickStart } from "./components/TaskQuickStart";
import { ActiveTaskDashboard } from "./components/ActiveTaskDashboard";
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
  const here = useLocation();
  const explicitTaskId = params.get("taskId");
  const pickMode = params.get("pick") === "1";  // explicit "go to QuickStart" flag
  const dedupedFlag = params.get("deduped") === "1";  // surfaced when POST /tasks deduped
  const [sessionReady, setSessionReady] = useState(false);

  // Strip ?deduped=1 after a tick so a refresh doesn't re-flash the banner.
  useEffect(() => {
    if (!dedupedFlag) return;
    const t = setTimeout(() => {
      setParams(prev => {
        const next = new URLSearchParams(prev);
        next.delete("deduped");
        return next;
      }, { replace: true });
    }, 3000);
    return () => clearTimeout(t);
  }, [dedupedFlag, setParams]);

  const sessionBootstrapped = useRef(false);
  useEffect(() => {
    if (sessionBootstrapped.current) return;
    sessionBootstrapped.current = true;
    ensureTutorSession().finally(() => setSessionReady(true));
  }, []);

  // Phase 4.4 cross-device sync: read jarvis_last_task server cookie on
  // cold mount. Server cookie wins over localStorage when both present.
  const [serverLastTask, setServerLastTask] = useState<string | null>(null);
  const [serverLastTaskLoaded, setServerLastTaskLoaded] = useState(false);
  useEffect(() => {
    let cancelled = false;
    jarvisFetch("/api/v1/last-task")
      .then(r => r.ok ? r.json() : null)
      .then((d: { taskId?: string } | null) => {
        if (cancelled) return;
        setServerLastTask(d?.taskId ?? null);
        setServerLastTaskLoaded(true);
      })
      .catch(() => { if (!cancelled) setServerLastTaskLoaded(true); });
    return () => { cancelled = true; };
  }, []);

  // Cold-start: restore last-used taskId. Explicit URL params don't gate on
  // /last-task (so direct ?taskId= still persists immediately). When no
  // explicit param is present, server cookie wins, then localStorage.
  const restoredOnce = useRef(false);
  useEffect(() => {
    if (restoredOnce.current) return;
    if (explicitTaskId) {
      restoredOnce.current = true;
      try { localStorage.setItem(LAST_TASK_KEY, explicitTaskId); } catch (_) {}
      return;
    }
    if (pickMode) { restoredOnce.current = true; return; }
    if (!serverLastTaskLoaded) return;  // only wait when restoring from absence
    restoredOnce.current = true;
    if (serverLastTask) {
      setParams({ taskId: serverLastTask }, { replace: true });
      return;
    }
    try {
      const last = localStorage.getItem(LAST_TASK_KEY);
      if (last && last !== "TEST-TASK-A") {
        setParams({ taskId: last }, { replace: true });
      }
    } catch (_) {}
  }, [explicitTaskId, pickMode, setParams, serverLastTask, serverLastTaskLoaded]);

  // Persist subsequent explicit taskId changes to localStorage so cold-reload
  // returns here. Not in the cold-start effect — that runs once. This fires
  // when the user navigates between real tasks.
  useEffect(() => {
    if (!restoredOnce.current) return;
    if (explicitTaskId) {
      try { localStorage.setItem(LAST_TASK_KEY, explicitTaskId); } catch (_) {}
    }
  }, [explicitTaskId]);

  // POST cookie when explicit task id changes so other devices sync.
  useEffect(() => {
    if (!explicitTaskId || explicitTaskId === "TEST-TASK-A") return;
    jarvisFetch("/api/v1/last-task", {
      method: "POST",
      body: JSON.stringify({ taskId: explicitTaskId }),
    }).catch(() => {});
  }, [explicitTaskId]);

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
      <header className="bg-panel-dark-bg text-panel-dark-fg px-4 py-3 flex items-center justify-between border-b-4 border-accent">
        <div className="flex items-center gap-3 min-w-0">
          <span className="text-lg font-bold tracking-widest">JARVIS · TUTOR</span>
          {!showQuickStart && (
            <>
              <span className="text-xs tracking-widest text-panel-dark-fg/80 truncate">{taskId}</span>
              <button
                onClick={pickAnotherTask}
                data-testid="pick-another-task-btn"
                className="text-xs tracking-widest bg-accent text-page-fg px-3 py-2 sm:px-2 sm:py-0.5 hover:bg-accent-hover"
              >
                × close
              </button>
            </>
          )}
        </div>
        <nav className="flex items-center gap-4 text-xs font-bold tracking-widest">
          <Link
            to="/?pick=1"
            aria-current={here.pathname === "/" ? "page" : undefined}
            className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
          >
            workspace
          </Link>
          <Link
            to="/tasks"
            aria-current={here.pathname === "/tasks" ? "page" : undefined}
            className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
          >
            tasks
          </Link>
          <Link
            to="/settings/trust"
            aria-current={here.pathname === "/settings/trust" ? "page" : undefined}
            className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
          >
            trust
          </Link>
        </nav>
      </header>
      <main className="flex-1 min-h-0 overflow-hidden bg-page-bg">
        {!sessionReady
          ? <div className="p-6 font-mono text-sm text-page-fg/60">setting up tutor session…</div>
          : showQuickStart
            ? <ActiveTaskDashboard />
            : <TutorWorkspace pdfUrl={`/api/v1/tasks/${encodeURIComponent(taskId)}/pdf`} taskId={taskId} dedupedNotice={dedupedFlag} />}
      </main>
    </div>
  );
}
