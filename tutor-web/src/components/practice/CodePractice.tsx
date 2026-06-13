import { useState } from "react";
import { gradeCode, runCode } from "../../lib/practiceApi";
import type { CodeGradeReply, CodeRunReply, PracticeProblem } from "../../lib/practiceApi";
import { practiceStrings } from "../../lib/practiceStrings";

/**
 * Plan-6 Task 10 — Code practice surface (REQ-7/8, spec §6.2.3, R-6-Q9).
 *
 * Plain `<textarea>` (R-6-Q9 — CodeMirror DEFERRED; named follow-up in Task 14 manifest).
 * Language badge renders the exam-language constraint visibly (REQ-7).
 * Reference solution rendered ONLY from the grade reply — never held in component state
 * pre-attempt (INV-6.6 client half; the server half is in PracticeRoutes.kt).
 *
 * Testids: code-practice-editor, code-practice-language-badge, code-practice-run,
 *          code-practice-verdict, code-practice-reference (grade reply only).
 */

interface Props {
  problem: PracticeProblem;
}

/** Map the exam_language id to a human-readable RO label (REQ-7). */
function langLabel(lang: string | null | undefined): string {
  switch (lang) {
    case "alk":     return "Alk";
    case "r":       return "R";
    case "cpp":     return "C++";
    case "bash":    return "Bash";
    case "posix-c": return "POSIX C";
    case "python":  return "Python";
    default:        return lang ?? "necunoscut";
  }
}

export function CodePractice({ problem }: Props) {
  const [source, setSource] = useState("");
  const [runReply, setRunReply]     = useState<CodeRunReply | null>(null);
  const [gradeReply, setGradeReply] = useState<CodeGradeReply | null>(null);
  const [running, setRunning]   = useState(false);
  const [grading, setGrading]   = useState(false);
  const [error, setError]       = useState<string | null>(null);

  async function handleRun() {
    setRunning(true);
    setRunReply(null);
    setError(null);
    try {
      const r = await runCode(problem.id, source);
      setRunReply(r);
    } catch (e) {
      setError(String(e));
    } finally {
      setRunning(false);
    }
  }

  async function handleGrade() {
    setGrading(true);
    setGradeReply(null);
    setError(null);
    try {
      const r = await gradeCode(problem.id, source);
      setGradeReply(r);
    } catch (e) {
      setError(String(e));
    } finally {
      setGrading(false);
    }
  }

  const degraded = runReply?.degraded_legs_ro ?? gradeReply?.degraded_legs_ro ?? [];

  return (
    <div className="flex flex-col gap-4 p-4">
      {/* Title + problem statement */}
      <h2 className="text-sm font-semibold text-page-fg/70">{practiceStrings.codePracticeTitle}</h2>
      <p className="text-sm text-page-fg whitespace-pre-wrap">{problem.statement_ro}</p>

      {/* Language badge (REQ-7) */}
      <span
        data-testid="code-practice-language-badge"
        className="self-start rounded bg-accent/10 px-2 py-0.5 text-xs font-mono font-semibold text-accent"
      >
        {langLabel(problem.exam_language)}
        {problem.exam_language_constraints ? ` — ${problem.exam_language_constraints}` : ""}
      </span>

      {/* Code editor — plain textarea (R-6-Q9; CodeMirror DEFERRED) */}
      <textarea
        data-testid="code-practice-editor"
        className="w-full rounded border border-border-strong p-2 font-mono text-sm resize-y min-h-[200px] bg-page-bg text-page-fg"
        value={source}
        onChange={(e) => setSource(e.target.value)}
        disabled={running || grading}
        placeholder={`// ${langLabel(problem.exam_language)}`}
        aria-label={practiceStrings.codePracticeTitle}
      />

      {/* Action buttons */}
      <div className="flex gap-2">
        <button
          data-testid="code-practice-run"
          className="rounded bg-accent/20 px-4 py-2 text-sm font-semibold text-accent hover:bg-accent/30 disabled:opacity-50"
          onClick={handleRun}
          disabled={running || grading}
        >
          {practiceStrings.codePracticeRunBtn}
        </button>
        <button
          className="rounded bg-accent px-4 py-2 text-sm font-semibold text-accent-fg disabled:opacity-50"
          onClick={handleGrade}
          disabled={running || grading}
        >
          {practiceStrings.codePracticeGradeBtn}
        </button>
      </div>

      {/* Degraded banner (R-6-Q1 honesty) */}
      {degraded.length > 0 && (
        <div className="rounded border border-yellow-400 bg-yellow-50 p-2 text-xs text-yellow-800">
          {practiceStrings.codePracticeDegradedBanner}
          <ul className="mt-1 list-disc pl-4">
            {degraded.map((d, i) => <li key={i}>{d}</li>)}
          </ul>
        </div>
      )}

      {/* Run output */}
      {runReply && (
        <div className="flex flex-col gap-1 rounded border border-border-strong p-3 font-mono text-xs">
          {runReply.timed_out && (
            <p className="text-red-600">Timeout!</p>
          )}
          {!runReply.compiled && runReply.stderr_trunc && (
            <pre className="whitespace-pre-wrap text-red-700">{runReply.stderr_trunc}</pre>
          )}
          {runReply.compiled && runReply.stdout_trunc && (
            <pre className="whitespace-pre-wrap text-page-fg">{runReply.stdout_trunc}</pre>
          )}
        </div>
      )}

      {/* Grade verdict (REQ-8) */}
      {gradeReply && (
        <div
          data-testid="code-practice-verdict"
          className={`rounded border p-3 text-sm ${gradeReply.correct ? "border-green-400 bg-green-50 text-green-800" : "border-red-400 bg-red-50 text-red-800"}`}
        >
          <span className="font-semibold">
            {gradeReply.correct ? practiceStrings.correct : practiceStrings.incorrect}
          </span>
          {gradeReply.feedback_ro && (
            <p className="mt-1 text-xs">{gradeReply.feedback_ro}</p>
          )}
        </div>
      )}

      {/* Reference solution — rendered ONLY from the grade reply (INV-6.6 client half).
          This element is absent from the DOM until a grade attempt completes. */}
      {gradeReply?.reference_solution_ro && (
        <div
          data-testid="code-practice-reference"
          className="rounded border border-border-strong p-3"
        >
          <p className="mb-1 text-xs font-semibold text-page-fg/60">
            {practiceStrings.codePracticeReferenceLabel}
          </p>
          <pre className="whitespace-pre-wrap font-mono text-xs text-page-fg">
            {gradeReply.reference_solution_ro}
          </pre>
        </div>
      )}

      {error && (
        <div className="text-xs text-red-600">{error}</div>
      )}
    </div>
  );
}
