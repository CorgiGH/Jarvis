import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { jarvisFetch } from "../lib/api";

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
      const deadline = new Date(Date.now() + deadlineDays * 24 * 3600 * 1000).toISOString();
      const r = await jarvisFetch("/api/v1/tasks", {
        method: "POST",
        body: JSON.stringify({ subject, title, deadline }),
      });
      if (!r.ok) throw new Error(`HTTP ${r.status}: ${(await r.text()).slice(0, 200)}`);
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <div data-testid="tasks-screen" className="p-4 font-mono">
      <h1 className="text-lg font-bold tracking-widest mb-3">TASKS</h1>
      <p className="text-xs text-black/60 mb-4">
        Create a task row to make Jarvis aware of what you're working on.
        Subject + title + deadline drive the in-chat task header.
      </p>

      <div data-testid="task-create-form" className="border-2 border-black p-3 mb-6">
        <div className="text-xs font-bold tracking-widest mb-2">NEW TASK</div>
        <label className="block text-xs mb-1">Subject</label>
        <div className="flex gap-1 mb-2">
          {SUBJECT_PRESETS.map(s => (
            <button key={s}
              data-testid={`task-subject-${s}`}
              onClick={() => setSubject(s)}
              className={`text-xs font-bold tracking-widest px-2 py-1 border ${
                subject === s ? "bg-black text-yellow-300" : "bg-white text-black border-black"
              }`}>
              {s}
            </button>
          ))}
          <input
            data-testid="task-subject-custom"
            className="flex-1 border border-black/30 px-2 py-1 text-sm"
            value={subject}
            onChange={e => setSubject(e.target.value)}
          />
        </div>
        <label className="block text-xs mb-1">Title</label>
        <input
          data-testid="task-title"
          className="w-full border border-black/30 px-2 py-1 text-sm mb-2"
          value={title}
          onChange={e => setTitle(e.target.value)}
        />
        <label className="block text-xs mb-1">Deadline (days from now)</label>
        <input
          data-testid="task-deadline-days"
          type="number" min={0} max={365}
          className="w-full border border-black/30 px-2 py-1 text-sm mb-2"
          value={deadlineDays}
          onChange={e => setDeadlineDays(Math.max(0, parseInt(e.target.value, 10) || 0))}
        />
        <button
          data-testid="task-create-btn"
          onClick={createTask}
          className="text-xs font-bold tracking-widest bg-black text-yellow-300 px-3 py-1"
        >
          CREATE
        </button>
      </div>

      <div className="text-xs font-bold tracking-widest mb-2">ACTIVE ({tasks.length})</div>
      {loading ? <div className="text-sm">loading…</div> :
       tasks.length === 0 ? <div className="text-sm text-black/60">(no tasks yet)</div> :
       <ul data-testid="tasks-list" className="space-y-2">
         {tasks.map(t => {
           const days = Math.round((new Date(t.deadline).getTime() - Date.now()) / 86400000);
           const dueTag = days < 0 ? `OVERDUE ${-days}d`
             : days === 0 ? "TODAY"
             : `${days}d remaining`;
           return (
             <li key={t.id} data-testid="task-row" data-task-id={t.id}
                 className="border border-black p-2">
               <div className="text-xs font-bold tracking-widest">
                 {t.subject} · {dueTag} · {t.status}
               </div>
               <div className="text-sm mt-1">{t.title}</div>
               <Link to={`/?taskId=${t.id}`}
                     data-testid="task-open-btn"
                     className="inline-block mt-2 text-xs font-bold tracking-widest bg-yellow-300 text-black px-2 py-1">
                 OPEN
               </Link>
             </li>
           );
         })}
       </ul>}

      {error && (
        <div data-testid="tasks-error" className="mt-4 text-xs text-red-700">
          {error}
        </div>
      )}
    </div>
  );
}
