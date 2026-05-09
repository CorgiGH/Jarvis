import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { jarvisFetch } from "../lib/api";

interface TaskView {
  id: string;
  subject: string;
  title: string;
  deadline: string;
  status: string;
}

interface Preset {
  subject: string;
  title: string;
  daysFromNow: number;
}

const PRESETS: Preset[] = [
  { subject: "PS", title: "Tema A — derivation example", daysFromNow: 12 },
  { subject: "PA", title: "Tema 5 — dynamic programming", daysFromNow: 9 },
  { subject: "POO", title: "Lab — class hierarchy refactor", daysFromNow: 5 },
  { subject: "ALO", title: "Lab — graph traversal", daysFromNow: 7 },
  { subject: "SO", title: "Lab — process scheduling", daysFromNow: 14 },
];

/**
 * Inline workspace onboarding card — appears when:
 *  - We're on /tutor/?taskId=TEST-TASK-A (default), AND
 *  - Either user has no real tasks OR /api/v1/tasks failed
 *
 * One-click presets create the task + redirect to it. No nav-page
 * detour. User goes from open-tab to working-task in 1 click.
 */
export function TaskQuickStart({ onCreated }: { onCreated?: (taskId: string) => void }) {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<TaskView[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    jarvisFetch("/api/v1/tasks")
      .then(async r => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        const data: { tasks: TaskView[] } = await r.json();
        setTasks(data.tasks);
      })
      .catch(e => setError((e as Error).message))
      .finally(() => setLoaded(true));
  }, []);

  async function createPreset(p: Preset) {
    setBusy(`${p.subject}-${p.title}`);
    setError(null);
    try {
      const deadline = new Date(Date.now() + p.daysFromNow * 86400000).toISOString();
      const r = await jarvisFetch("/api/v1/tasks", {
        method: "POST",
        body: JSON.stringify({ subject: p.subject, title: p.title, deadline }),
      });
      if (!r.ok) throw new Error(`HTTP ${r.status}: ${(await r.text()).slice(0, 200)}`);
      const created: TaskView = await r.json();
      onCreated?.(created.id);
      navigate(`/?taskId=${created.id}`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  if (!loaded) {
    return <div className="p-6 font-mono text-sm text-black/60">loading workspace…</div>;
  }

  return (
    <div data-testid="task-quickstart" className="p-6 font-mono text-sm">
      <div className="bg-yellow-100 border-l-4 border-yellow-500 p-4 mb-4">
        <div className="text-xs font-bold tracking-widest mb-1">QUICK START</div>
        <div className="text-sm text-black/80">
          {tasks.length === 0
            ? "No real tasks yet. Pick one to get going — Jarvis pulls subject + deadline + weak-concept context per chat turn."
            : `You have ${tasks.length} active task${tasks.length === 1 ? "" : "s"}. Pick one or jump into a fresh subject.`}
        </div>
      </div>

      {tasks.length > 0 && (
        <div className="mb-6">
          <div className="text-xs font-bold tracking-widest mb-2">YOUR TASKS</div>
          <ul className="space-y-2">
            {tasks.map(t => {
              const days = Math.round((new Date(t.deadline).getTime() - Date.now()) / 86400000);
              const dueTag = days < 0 ? `OVERDUE ${-days}d`
                : days === 0 ? "TODAY"
                : `${days}d`;
              return (
                <li key={t.id} data-testid="task-quickstart-row" data-task-id={t.id}>
                  <button
                    onClick={() => navigate(`/?taskId=${t.id}`)}
                    className="w-full text-left border border-black p-2 hover:bg-yellow-50"
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
        </div>
      )}

      <div className="text-xs font-bold tracking-widest mb-2">NEW TASK PRESETS</div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mb-4">
        {PRESETS.map(p => {
          const id = `${p.subject}-${p.title}`;
          return (
            <button
              key={id}
              data-testid={`task-preset-${p.subject}`}
              onClick={() => createPreset(p)}
              disabled={busy != null}
              className="text-left border-2 border-black bg-white hover:bg-yellow-50 p-2 disabled:opacity-50"
            >
              <div className="text-xs font-bold tracking-widest">
                + {p.subject} · {p.daysFromNow}d
              </div>
              <div className="text-sm">{p.title}</div>
              {busy === id && <div className="text-xs text-black/60 mt-1">creating…</div>}
            </button>
          );
        })}
      </div>

      <div className="text-xs text-black/60">
        Custom task? <a className="underline" href="/tutor/tasks">/tasks page</a>.
        Don't want this panel? Pick any task above.
      </div>

      {error && (
        <div data-testid="task-quickstart-error" className="mt-4 text-xs text-red-700">
          {error}
        </div>
      )}
    </div>
  );
}
