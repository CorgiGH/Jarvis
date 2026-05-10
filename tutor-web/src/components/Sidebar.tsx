import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { jarvisFetch } from "../lib/api";

interface TaskView {
  id: string;
  subject: string;
  title: string;
  deadline: string;
  status: string;
}

/**
 * Mockup-gap bridge step 2: subjects + open-tasks sidebar.
 *
 * The brutalist-mono mockup at .superpowers/brainstorm/.../tutor-mockup-v4.html
 * shows a left rail with subjects + open tasks. Live shipped without it
 * (just header strip + workspace pane). This adds the rail.
 *
 * Tasks come from /api/v1/tasks; grouped by subject; click navigates to
 * /?taskId=<id>. "+ NEW TASK" button jumps to /?pick=1 (forces QuickStart).
 *
 * Hidden on mobile (sm:flex) so the 2-col PDF/chat workspace keeps its
 * full width on phones.
 */
export function Sidebar({ activeTaskId }: { activeTaskId?: string }) {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<TaskView[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    jarvisFetch("/api/v1/tasks")
      .then(r => r.ok ? r.json() : { tasks: [] })
      .then((data: { tasks: TaskView[] }) => setTasks(data.tasks ?? []))
      .catch(() => setTasks([]))
      .finally(() => setLoaded(true));
  }, [activeTaskId]);

  const grouped = useMemo(() => {
    const map = new Map<string, TaskView[]>();
    for (const t of tasks) {
      const arr = map.get(t.subject) ?? [];
      arr.push(t);
      map.set(t.subject, arr);
    }
    // Sort each subject's tasks by deadline ascending; subjects alphabetical.
    for (const arr of map.values()) {
      arr.sort((a, b) => a.deadline.localeCompare(b.deadline));
    }
    return [...map.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [tasks]);

  return (
    <aside
      data-testid="sidebar"
      className="hidden sm:flex flex-col w-48 border-r-4 border-border-strong bg-accent-soft font-mono text-xs overflow-y-auto"
    >
      <div className="bg-panel-dark-bg text-panel-dark-fg px-3 py-2 tracking-widest font-bold">
        TASKS
      </div>
      <button
        data-testid="sidebar-new-task"
        onClick={() => navigate("/?pick=1")}
        aria-label="Create a new tutor task"
        className="border-b-2 border-border-strong px-3 py-2 text-left bg-accent hover:bg-accent-hover font-bold tracking-widest"
      >
        + NEW TASK
      </button>
      {!loaded && <div className="px-3 py-2 text-page-fg/60">loading…</div>}
      {loaded && tasks.length === 0 && (
        <div data-testid="sidebar-empty" className="px-3 py-2 text-page-fg/60">
          no tasks yet — click NEW TASK
        </div>
      )}
      {grouped.map(([subject, subjectTasks]) => (
        <div key={subject} data-testid={`sidebar-subject-${subject}`}>
          <div className="px-3 py-1 mt-2 bg-page-fg/10 tracking-widest font-bold border-y border-border-thin">
            {subject}
          </div>
          <ul>
            {subjectTasks.map(t => {
              const days = Math.round(
                (new Date(t.deadline).getTime() - Date.now()) / 86400000,
              );
              const dueTag = days < 0 ? `${-days}d ago`
                : days === 0 ? "today"
                : `${days}d`;
              const active = t.id === activeTaskId;
              return (
                <li key={t.id}>
                  <button
                    data-testid="sidebar-task"
                    data-task-id={t.id}
                    onClick={() => navigate(`/?taskId=${t.id}`)}
                    aria-current={active ? "true" : undefined}
                    className={`w-full text-left px-3 py-1.5 border-b border-border-thin hover:bg-accent-soft ${
                      active ? "bg-accent font-bold" : ""
                    }`}
                    title={t.title}
                  >
                    <div className="truncate">{t.title}</div>
                    <div className="text-[10px] text-page-fg/60">{dueTag}</div>
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </aside>
  );
}
