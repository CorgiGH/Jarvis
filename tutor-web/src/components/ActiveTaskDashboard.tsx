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
  const [detectResult, setDetectResult] = useState<string | null>(null);
  const [detectError, setDetectError] = useState<string | null>(null);
  const [detectAt, setDetectAt] = useState<number | null>(null);

  useEffect(() => {
    jarvisFetch("/api/v1/tasks")
      .then(r => r.ok ? r.json() : { tasks: [] })
      .then((data: { tasks: TaskView[] }) => setTasks(data.tasks ?? []))
      .catch(() => setTasks([]))
      .finally(() => setLoaded(true));
  }, []);

  const ranked = tasks
    .filter(t => t.status === "ACTIVE" || t.status === "TODO" || t.status == null)
    .sort((a, b) => rankTask(b) - rankTask(a));

  async function runDetection() {
    setDetecting(true);
    setDetectResult(null);
    setDetectError(null);
    try {
      const r = await jarvisFetch("/api/v1/task-detect/run", { method: "POST" });
      if (r.ok) {
        const reply = await r.json() as { inserted: number; existing: number; total: number };
        setDetectResult(`${reply.inserted} new · ${reply.existing} existing · ${reply.total} discovered`);
      } else {
        setDetectError(`HTTP ${r.status}`);
      }
      const r2 = await jarvisFetch("/api/v1/tasks");
      if (r2.ok) {
        const data: { tasks: TaskView[] } = await r2.json();
        setTasks(data.tasks ?? []);
      }
    } catch (e) {
      setDetectError((e as Error).message);
    } finally {
      setDetecting(false);
      setDetectAt(Date.now());
    }
  }

  function formatRelativeAgo(ts: number): string {
    const diffSec = Math.round((Date.now() - ts) / 1000);
    if (diffSec < 60) return `${diffSec}s ago`;
    const diffMin = Math.round(diffSec / 60);
    if (diffMin < 60) return `${diffMin} min ago`;
    return `${Math.round(diffMin / 60)} hr ago`;
  }

  return (
    <div data-testid="active-task-dashboard" className="p-6 font-mono text-sm">
      <div className="text-xs font-bold tracking-widest mb-2">
        ACTIVE TASKS · ranked by urgency × weight × readiness
      </div>
      {!loaded ? (
        <div role="status" aria-live="polite" className="text-page-fg/60">loading…</div>
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
      <div className="flex gap-2 mb-2 flex-wrap">
        <button
          data-testid="active-task-detect-btn"
          onClick={runDetection}
          disabled={detecting}
          aria-busy={detecting}
          className="text-xs font-bold tracking-widest bg-panel-dark-bg text-panel-dark-fg px-3 py-2 sm:py-1 disabled:opacity-50"
        >
          {detecting ? "RUNNING…" : "RUN DETECTION"}
        </button>
        <button
          data-testid="active-task-manual-btn"
          onClick={() => setShowManual(s => !s)}
          aria-expanded={showManual}
          className="text-xs font-bold tracking-widest bg-page-bg text-page-fg border-2 border-border-strong px-3 py-2 sm:py-1"
        >
          {showManual ? "− Hide manual entry" : "+ Manual entry"}
        </button>
      </div>
      {detectResult && detectAt != null && (
        <div data-testid="active-task-detect-result"
             role="status" aria-live="polite"
             className="text-xs text-page-fg/80 mb-4 border border-border-thin bg-accent-soft px-2 py-1">
          detection: {detectResult} · last run {formatRelativeAgo(detectAt)}
        </div>
      )}
      {detectError && (
        <div data-testid="active-task-detect-error"
             role="alert"
             className="text-xs text-danger-text mb-4 border border-border-thin bg-page-bg px-2 py-1">
          detection failed: {detectError}
        </div>
      )}
      {showManual && <TaskQuickStart />}
    </div>
  );
}
