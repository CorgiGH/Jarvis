import { useNavigate, useSearchParams } from "react-router-dom";

export interface ProblemStub {
  problemId: string;
  label: string;
}

interface ProblemStepperProps {
  problems: ProblemStub[];
  activeProblemIndex: number;
  onProblemSelect?: (index: number) => void;
}

export function parseProblemParam(raw: string | null): number {
  if (raw == null) return 0;
  const n = parseInt(raw, 10);
  if (isNaN(n) || n < 0) return 0;
  return n;
}

export function ProblemStepper({ problems, activeProblemIndex, onProblemSelect }: ProblemStepperProps) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  function handleClick(index: number) {
    const next = new URLSearchParams(searchParams);
    next.set("problem", String(index));
    navigate({ search: next.toString() });
    onProblemSelect?.(index);
  }

  return (
    <nav
      data-testid="problem-stepper"
      aria-label="Problem stepper"
      className="flex items-center gap-3 px-4 py-2 border-b-4 border-border-strong bg-accent-soft font-mono text-xs overflow-x-auto"
    >
      {problems.map((p, i) => {
        const active = i === activeProblemIndex;
        return (
          <button
            key={p.problemId}
            data-testid={`stepper-problem-${p.problemId}`}
            aria-current={active ? "true" : undefined}
            onClick={() => handleClick(i)}
            className={`flex items-center gap-1 tracking-widest whitespace-nowrap px-2 py-1 transition-colors ${
              active
                ? "text-page-fg font-bold bg-accent"
                : "text-page-fg/60 hover:text-page-fg hover:bg-accent/50"
            }`}
          >
            <span aria-hidden="true">{active ? "◉" : "○"}</span>
            <span>{p.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
