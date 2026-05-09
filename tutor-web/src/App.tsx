import { useSearchParams, Link } from "react-router-dom";
import { TutorWorkspace } from "./components/TutorWorkspace";

export function App() {
  const [params] = useSearchParams();
  const taskId = params.get("taskId") ?? "TEST-TASK-A";
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
      <main className="flex-1 min-h-0 overflow-hidden">
        <TutorWorkspace pdfUrl="/tutor/test-task.pdf" taskId={taskId} />
      </main>
    </div>
  );
}
