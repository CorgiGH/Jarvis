import { useSearchParams, Link } from "react-router-dom";
import { TutorWorkspace } from "./components/TutorWorkspace";

export function App() {
  const [params] = useSearchParams();
  const taskId = params.get("taskId") ?? "TEST-TASK-A";
  return (
    <div className="h-dvh flex flex-col">
      <div className="bg-black text-yellow-300 px-3 py-1 text-xs tracking-widest font-bold flex justify-between">
        <span>JARVIS · TUTOR · {taskId}</span>
        <span className="space-x-3">
          <Link to="/tasks" className="hover:underline">tasks</Link>
          <Link to="/settings/trust" className="hover:underline">trust</Link>
        </span>
      </div>
      <div className="flex-1 min-h-0">
        <TutorWorkspace pdfUrl="/test-task.pdf" taskId={taskId} />
      </div>
    </div>
  );
}
