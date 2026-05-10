import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { jarvisFetch } from "../lib/api";
import { useInFlight } from "../lib/inFlight";

interface TaskView {
  id: string;
  subject: string;
  title: string;
  deadline: string;
  status: string;
}

const SUBJECT_PRESETS = ["PA", "PS", "POO", "ALO", "SO"];

/**
 * Tutor task-context V1 — minimal task CRUD screen so the user can
 * actually seed real PS Tema A / PA Tema 5 etc. as task rows.
 * Without this, the V0 TaskHeaderBuilder has nothing to look up.
 *
 * Each task in the list links to the workspace pinned to that
 * taskId; chat then carries that taskId in its /api/chat payload
 * which triggers TaskHeaderBuilder injection on the server.
 */
export function TasksScreen() {
  const [tasks, setTasks] = useState<TaskView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [subject, setSubject] = useState("PS");
  const [title, setTitle] = useState("Tema A — derivation example");
  const [deadlineDays, setDeadlineDays] = useState(12);
  const create = useInFlight();

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const r = await jarvisFetch("/api/v1/tasks");
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const data: { tasks: TaskView[] } = await r.json();
      setTasks(data.tasks);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { refresh(); }, []);

  async function createTask() {
    setError(null);
    try {
      await create.run(async () => {
        const deadline = new Date(Date.now() + deadlineDays * 24 * 3600 * 1000).toISOString();
        const r = await jarvisFetch("/api/v1/tasks", {
          method: "POST",
          body: JSON.stringify({ subject, title, deadline }),
        });
        if (!r.ok) throw new Error(`HTTP ${r.status}: ${(await r.text()).slice(0, 200)}`);
        await refresh();
      });
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <div data-testid="tasks-screen" className="p-4 font-mono">
      <h1 className="text-lg font-bold tracking-widest mb-3">TASKS</h1>
      <p className="text-xs text-page-fg/60 mb-4">
        Create a task row to make Jarvis aware of what you're working on.
        Subject + title + deadline drive the in-chat task header.
      </p>

      <form data-testid="task-create-form"
            onSubmit={e => { e.preventDefault(); createTask(); }}
            className="border-2 border-border-strong p-3 mb-6">
        <div className="text-xs font-bold tracking-widest mb-2">NEW TASK</div>
        <label htmlFor="task-subject-input" className="block text-xs mb-1">Subject</label>
        <div className="flex gap-1 mb-2">
          {SUBJECT_PRESETS.map(s => (
            <button key={s}
              type="button"
              data-testid={`task-subject-${s}`}
              onClick={() => setSubject(s)}
              className={`text-xs font-bold tracking-widest px-2 py-1 border ${
                subject === s ? "bg-panel-dark-bg text-panel-dark-fg" : "bg-page-bg text-page-fg border-border-strong"
              }`}>
              {s}
            </button>
          ))}
          <input
            id="task-subject-input"
            data-testid="task-subject-custom"
            className="flex-1 border border-border-thin px-2 py-1 text-sm"
            value={subject}
            onChange={e => setSubject(e.target.value)}
          />
        </div>
        <label htmlFor="task-title-input" className="block text-xs mb-1">Title</label>
        <input
          id="task-title-input"
          data-testid="task-title"
          className="w-full border border-border-thin px-2 py-1 text-sm mb-2"
          value={title}
          onChange={e => setTitle(e.target.value)}
        />
        <label htmlFor="task-deadline-input" className="block text-xs mb-1">Deadline (days from now)</label>
        <input
          id="task-deadline-input"
          data-testid="task-deadline-days"
          type="number" min={0} max={365}
          className="w-full border border-border-thin px-2 py-1 text-sm mb-2"
          value={deadlineDays}
          onChange={e => setDeadlineDays(Math.max(0, parseInt(e.target.value, 10) || 0))}
        />
        <button
          type="submit"
          data-testid="task-create-btn"
          disabled={create.inFlight}
          className="text-xs font-bold tracking-widest bg-panel-dark-bg text-panel-dark-fg px-3 py-1 disabled:opacity-50"
        >
          CREATE
        </button>
        {create.showSpinner && (
          <span data-testid="task-create-spinner" aria-live="polite" className="ml-2 text-xs text-page-fg/60">creating…</span>
        )}
      </form>

      <div className="text-xs font-bold tracking-widest mb-2">ACTIVE ({tasks.length})</div>
      {loading ? <div className="text-sm">loading…</div> :
       tasks.length === 0 ? <div role="status" className="text-sm text-page-fg/60">no tasks yet — fill the NEW TASK form above to add one</div> :
       <ul data-testid="tasks-list" className="space-y-2">
         {tasks.map(t => {
           const days = Math.round((new Date(t.deadline).getTime() - Date.now()) / 86400000);
           const dueTag = days < 0 ? `OVERDUE ${-days}d`
             : days === 0 ? "TODAY"
             : `${days}d remaining`;
           return (
             <li key={t.id} data-testid="task-row" data-task-id={t.id}
                 className="border border-border-strong p-2">
               <div className="text-xs font-bold tracking-widest">
                 {t.subject} · {dueTag} · {t.status}
               </div>
               <div className="text-sm mt-1">{t.title}</div>
               <Link to={`/?taskId=${t.id}`}
                     data-testid="task-open-btn"
                     className="inline-block mt-2 text-xs font-bold tracking-widest bg-accent text-page-fg px-2 py-1">
                 OPEN
               </Link>
             </li>
           );
         })}
       </ul>}

      {error && (
        <div data-testid="tasks-error" className="mt-4 text-xs text-danger-text">
          {error}
        </div>
      )}
    </div>
  );
}
