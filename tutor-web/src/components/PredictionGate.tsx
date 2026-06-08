/**
 * PredictionGate — surface 0d.
 * Two (or more) hard-bordered buttons; student must commit a prediction before
 * seeing the next step. Gate locks once a prediction is submitted.
 * Renders nothing when options is empty.
 *
 * testids: prediction-gate · prediction-option-{i} · prediction-submitted
 */
import { useState } from "react";

interface PredictionGateProps {
  /** 2-4 Romanian prediction options. */
  options: string[];
  /** Called with the chosen option text when student commits. */
  onPredict: (option: string) => void;
}

export function PredictionGate({ options, onPredict }: PredictionGateProps) {
  const [committed, setCommitted] = useState<string | null>(null);

  if (!options || options.length === 0) return null;

  function handleSelect(option: string) {
    if (committed !== null) return; // gate locked
    setCommitted(option);
    onPredict(option);
  }

  if (committed !== null) {
    return (
      <div data-testid="prediction-gate" className="flex flex-col gap-2">
        <div
          data-testid="prediction-submitted"
          className="border-2 border-accent px-4 py-3 font-mono text-xs text-page-fg tracking-wide"
        >
          <span className="text-accent font-bold uppercase tracking-widest text-[10px] block mb-1">
            Predicție confirmată
          </span>
          {committed}
        </div>
      </div>
    );
  }

  return (
    <div data-testid="prediction-gate" className="flex flex-col gap-2">
      <span className="font-mono text-[10px] uppercase tracking-widest text-page-fg/50 mb-1">
        Alege o predicție:
      </span>
      {options.map((opt, i) => (
        <button
          key={i}
          data-testid={`prediction-option-${i}`}
          onClick={() => handleSelect(opt)}
          className="border-2 border-page-fg text-page-fg font-mono text-xs tracking-wide px-4 py-3 text-left hover:border-accent hover:text-accent transition-colors"
        >
          {opt}
        </button>
      ))}
    </div>
  );
}
