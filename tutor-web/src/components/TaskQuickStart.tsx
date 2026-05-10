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
  /** Path under archival/ pointing to a real PDF. Stored as
   *  task.problemRef.path so /api/v1/tasks/{id}/pdf can serve
   *  it instantly (no manual scp). Verified present on VPS
   *  2026-05-09. */
  problemPath: string;
}

const PRESETS: Preset[] = [
  { subject: "PS",  title: "Tema A — derivation",
    daysFromNow: 12, problemPath: "_extras/PS/ps_hw/Tema_A.pdf" },
  { subject: "PA",  title: "Partial 2021",
    daysFromNow: 9,  problemPath: "_extras/PA/study_guide/source/Subiect partial 2021.pdf" },
  { subject: "POO", title: "Lecture C1 — intro",
    daysFromNow: 5,  problemPath: "_extras/POO/courses/poo_c1.pdf" },
  { subject: "ALO", title: "Seminar 1",
    daysFromNow: 7,  problemPath: "_extras/ALO/labs/alo_sem1.pdf" },
  { subject: "SO",  title: "Lecture — Linux intro",
    daysFromNow: 14, problemPath: "_extras/SO/lectures__OS1.1_Linux-intro_print-ro.pdf" },
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
        body: JSON.stringify({
          subject: p.subject,
          title: p.title,
          deadline,
          repo: "archival",
          problemPath: p.problemPath,
        }),
      });
      if (!r.ok) throw new Error(`HTTP ${r.status}: ${(await r.text()).slice(0, 200)}`);
      const created: TaskView = await r.json();
      onCreated?.(created.id);
      window.dispatchEvent(new CustomEvent("jarvis:task-created", { detail: { id: created.id } }));
      // 200 = server matched (subject,title) → existing task. 201 = fresh insert.
      // Pass `deduped=1` so the workspace can flash a banner; otherwise the user
      // sees no difference between "made a new task" and "re-opened an old one".
      const deduped = r.status === 200;
      navigate(`/?taskId=${created.id}${deduped ? "&deduped=1" : ""}`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  if (!loaded) {
    return <div className="p-6 font-mono text-sm text-page-fg/60">loading workspace…</div>;
  }

  return (
    <div data-testid="task-quickstart" className="p-6 font-mono text-sm">
      <div className="bg-accent-soft border-l-4 border-accent-rule p-4 mb-4">
        <div className="text-xs font-bold tracking-widest mb-1">QUICK START</div>
        <div className="text-sm text-page-fg/80">
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
              className="text-left border-2 border-border-strong bg-page-bg hover:bg-accent-soft p-2 disabled:opacity-50"
            >
              <div className="text-xs font-bold tracking-widest">
                + {p.subject} · {p.daysFromNow}d
              </div>
              <div className="text-sm">{p.title}</div>
              {busy === id && <div className="text-xs text-page-fg/60 mt-1">creating…</div>}
            </button>
          );
        })}
      </div>

      <div className="text-xs text-page-fg/60">
        Custom task? <a className="underline" href="/tutor/tasks">/tasks page</a>.
        Don't want this panel? Pick any task above.
      </div>

      {error && (
        <div data-testid="task-quickstart-error" className="mt-4 text-xs text-danger-text">
          {error}
        </div>
      )}
    </div>
  );
}
