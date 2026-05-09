import { useEffect, useState } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { TutorWorkspace } from "./components/TutorWorkspace";
import { TaskQuickStart } from "./components/TaskQuickStart";
import { jarvisFetch } from "./lib/api";

const LAST_TASK_KEY = "jarvis.lastTaskId";

export function App() {
  const [params, setParams] = useSearchParams();
  const explicitTaskId = params.get("taskId");

  // Boot: if no taskId in URL, try the last-used taskId from
  // localStorage. Saves the user one click on every reload.
  useEffect(() => {
    if (explicitTaskId) {
      try { localStorage.setItem(LAST_TASK_KEY, explicitTaskId); } catch (_) {}
      return;
    }
    try {
      const last = localStorage.getItem(LAST_TASK_KEY);
      if (last && last !== "TEST-TASK-A") {
        setParams({ taskId: last }, { replace: true });
      }
    } catch (_) {}
  }, [explicitTaskId, setParams]);

  const taskId = explicitTaskId ?? "TEST-TASK-A";
  const isDefault = taskId === "TEST-TASK-A";

  // Verify the taskId actually exists server-side. If not (e.g. deleted
  // task referenced via stale URL), fall back to the QuickStart panel
  // so the user has something to do.
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

  return (
    <div className="h-dvh flex flex-col">
      <header className="bg-black text-yellow-300 px-4 py-3 flex items-center justify-between border-b-4 border-yellow-300">
        <div className="flex items-center gap-3">
          <span className="text-lg font-bold tracking-widest">JARVIS · TUTOR</span>
          <span className="text-xs tracking-widest text-yellow-200/80">{taskId}</span>
        </div>
        <nav className="flex items-center gap-4 text-xs font-bold tracking-widest">
          <Link to="/" className="hover:underline">workspace</Link>
          <Link to="/tasks" className="hover:underline">tasks</Link>
          <Link to="/settings/trust" className="hover:underline">trust</Link>
        </nav>
      </header>
      <main className="flex-1 min-h-0 overflow-hidden bg-white">
        {showQuickStart
          ? <TaskQuickStart />
          : <TutorWorkspace pdfUrl="/tutor/test-task.pdf" taskId={taskId} />}
      </main>
    </div>
  );
}
