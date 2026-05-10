import { useState } from "react";
import { jarvisFetch } from "../lib/api";

export interface ProblemAnswer {
  problemId: string;
  attempt: string;
}

interface CompileSubmitCardProps {
  taskId: string;
  answers: ProblemAnswer[];
  onSubmitted?: () => void;
}

type SubmitPhase = "idle" | "submitting" | "done" | "error";

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

/**
 * Builds a plain-text LaTeX-friendly export from per-problem answers.
 * Format:
 *   \textbf{A1.} μ̂ = 8 (sample median)
 *   \textbf{A2.} σ̂² = 6.4 ...
 */
function buildLatexExport(answers: ProblemAnswer[]): string {
  return answers
    .map((a) => `\\textbf{${a.problemId}.} ${a.attempt}`)
    .join("\n\n");
}

/**
 * CompileSubmitCard — appears after all problems complete.
 *
 * Animation #18: slides up from below viewport via CSS class `animate-slide-up`
 * (400ms cubic-bezier). Under prefers-reduced-motion, animation class is
 * suppressed and card is rendered in-place instantly.
 *
 * Stitches per-problem answers into a LaTeX-friendly block for review,
 * then POSTs to /api/v1/tasks/{id}/submit with the stitched note.
 */
export function CompileSubmitCard({
  taskId,
  answers,
  onSubmitted,
}: CompileSubmitCardProps) {
  const [phase, setPhase] = useState<SubmitPhase>("idle");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const reduced = prefersReducedMotion();
  const latexExport = buildLatexExport(answers);

  async function handleSubmit() {
    if (phase !== "idle" && phase !== "error") return;
    setPhase("submitting");
    setErrorMsg(null);
    try {
      const res = await jarvisFetch(
        `/api/v1/tasks/${encodeURIComponent(taskId)}/submit`,
        {
          method: "POST",
          body: JSON.stringify({ note: latexExport }),
        }
      );
      if (!res.ok) {
        const msg = await res.text().catch(() => "");
        throw new Error(`HTTP ${res.status}: ${msg.slice(0, 160)}`);
      }
      setPhase("done");
      onSubmitted?.();
    } catch (e) {
      setErrorMsg(
        e instanceof Error ? e.message : "Submission failed — please retry."
      );
      setPhase("error");
    }
  }

  return (
    <div
      data-testid="compile-submit-card"
      className={`border-4 border-border-strong bg-page-bg font-mono text-xs ${
        !reduced ? "animate-slide-up" : ""
      }`}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 bg-panel-dark-bg text-panel-dark-fg border-b-4 border-border-strong">
        <span
          data-testid="compile-submit-heading"
          className="tracking-widest font-bold"
        >
          ⑤ COMPILE &amp; SUBMIT
        </span>
        <span className="text-[10px] tracking-widest text-panel-dark-fg/60">
          [SUBMIT]
        </span>
      </div>

      {/* LaTeX export block */}
      <div className="px-4 py-4">
        <div className="mb-2 tracking-widest text-page-fg/60 text-[10px]">
          STITCHED ANSWERS · LaTeX EXPORT
        </div>
        <pre
          data-testid="latex-export"
          className="whitespace-pre-wrap break-words bg-accent-soft border-2 border-border-thin px-3 py-3 leading-relaxed text-[11px]"
        >
          {latexExport}
        </pre>
      </div>

      {/* Submit row */}
      <div className="px-4 pb-4 flex flex-col gap-2">
        <button
          data-testid="submit-button"
          onClick={handleSubmit}
          disabled={phase === "submitting" || phase === "done"}
          className="px-6 py-2 bg-accent text-page-fg font-bold tracking-widest border-2 border-border-strong hover:bg-accent-hover disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-[280ms] ease-in-out active:scale-95"
        >
          {phase === "submitting"
            ? "SUBMITTING…"
            : phase === "done"
            ? "✓ SUBMITTED"
            : "MARK SUBMITTED"}
        </button>
        {phase === "error" && errorMsg && (
          <div
            data-testid="submit-error"
            className="text-danger-text tracking-widest text-[10px]"
          >
            {errorMsg}
          </div>
        )}
        {phase === "done" && (
          <div
            data-testid="submit-success"
            className="text-page-fg/70 tracking-widest text-[10px]"
          >
            SUBMITTED — check your task status for grade feedback.
          </div>
        )}
      </div>
    </div>
  );
}
