import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { jarvisFetch } from "../lib/api";
import { rankTask, type RankableTask } from "../lib/taskRanking";
import { TaskQuickStart } from "./TaskQuickStart";

interface TaskView extends RankableTask {}

/**
 * Phase 6.5: replaces TaskQuickStart as the default "no-task-pinned"
 * landing. Renders the user's tasks ranked by the spec composite score.
 * "+ Manual entry" expands the original TaskQuickStart inline as a
 * fallback for adding a task that isn't surfaced by any detector.
 */
export function ActiveTaskDashboard() {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<TaskView[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [showManual, setShowManual] = useState(false);
  const [detecting, setDetecting] = useState(false);

  useEffect(() => {
    jarvisFetch("/api/v1/tasks")
      .then(r => r.ok ? r.json() : { tasks: [] })
      .then((data: { tasks: TaskView[] }) => setTasks(data.tasks ?? []))
      .catch(() => setTasks([]))
      .finally(() => setLoaded(true));
  }, []);

  const ranked = [...tasks].sort((a, b) => rankTask(b) - rankTask(a));

  async function runDetection() {
    setDetecting(true);
    try {
      await jarvisFetch("/api/v1/task-detect/run", { method: "POST" });
      const r = await jarvisFetch("/api/v1/tasks");
      if (r.ok) {
        const data: { tasks: TaskView[] } = await r.json();
        setTasks(data.tasks ?? []);
      }
    } catch (_) {
      // best-effort
    } finally {
      setDetecting(false);
    }
  }

  return (
    <div data-testid="active-task-dashboard" className="p-6 font-mono text-sm">
      <div className="text-xs font-bold tracking-widest mb-2">
        ACTIVE TASKS · ranked by urgency × weight × readiness
      </div>
      {!loaded ? (
        <div className="text-page-fg/60">loading…</div>
      ) : ranked.length === 0 ? (
        <div data-testid="active-task-empty" className="text-page-fg/60 mb-4">
          No active tasks yet. Trigger detection or add one manually below.
        </div>
      ) : (
        <ul role="list" className="space-y-1 mb-4">
          {ranked.map(t => {
            const days = Math.round((new Date(t.deadline).getTime() - Date.now()) / 86400000);
            const dueTag = days < 0 ? `OVERDUE ${-days}d` : days === 0 ? "TODAY" : `${days}d`;
            return (
              <li key={t.id} data-testid="active-task-row" data-task-id={t.id}>
                <button
                  onClick={() => navigate(`/?taskId=${t.id}`)}
                  className="w-full text-left border border-border-strong p-2 hover:bg-accent-soft"
                >
                  <div className="text-xs font-bold tracking-widest">
                    {t.subject} · {dueTag}
                  </div>
                  <div className="text-sm">{t.title}</div>
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <div className="flex gap-2 mb-4">
        <button
          data-testid="active-task-detect-btn"
          onClick={runDetection}
          disabled={detecting}
          className="text-xs font-bold tracking-widest bg-panel-dark-bg text-panel-dark-fg px-3 py-2 sm:py-1 disabled:opacity-50"
        >
          {detecting ? "RUNNING…" : "RUN DETECTION"}
        </button>
        <button
          data-testid="active-task-manual-btn"
          onClick={() => setShowManual(s => !s)}
          className="text-xs font-bold tracking-widest bg-page-bg text-page-fg border-2 border-border-strong px-3 py-2 sm:py-1"
        >
          {showManual ? "− Hide manual entry" : "+ Manual entry"}
        </button>
      </div>
      {showManual && <TaskQuickStart />}
    </div>
  );
}
