import { useState } from "react";
import { gradeTraceStep } from "../../lib/practiceApi";
import type { PracticeProblem, TraceStepReply } from "../../lib/practiceApi";
import { practiceStrings } from "../../lib/practiceStrings";

/**
 * Plan-6 Task 9 — Step-trace numeric drill surface (REQ-5/6, spec §6.2.2).
 *
 * Renders the algorithm skeleton table and lets the user enter a value for each
 * step one at a time. A wrong step is caught at that step — the user is blocked
 * from advancing until they get it right (or give up). Submits to
 * POST /api/v1/practice/trace/{id}/step per step.
 *
 * Testids: trace-drill-skeleton, trace-drill-step-input, trace-drill-step-verdict,
 *          trace-drill-submit-btn, trace-drill-giveup-btn.
 */

interface Props {
  problem: PracticeProblem;
}

interface StepResult {
  stepIndex: number;
  value: string;
  reply: TraceStepReply;
}

export function StepTraceDrill({ problem }: Props) {
  const steps = problem.trace_steps ?? [];
  const [currentStep, setCurrentStep] = useState(0);
  const [inputValue, setInputValue] = useState("");
  const [lastReply, setLastReply] = useState<TraceStepReply | null>(null);
  const [results, setResults] = useState<StepResult[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [complete, setComplete] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isFinished = complete || currentStep >= steps.length;

  async function handleSubmit() {
    if (isFinished || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const reply = await gradeTraceStep(problem.id, currentStep, inputValue);
      setLastReply(reply);
      setResults((prev) => [...prev, { stepIndex: currentStep, value: inputValue, reply }]);
      if (reply.verdict.passed) {
        // Advance to the next step; keep lastReply visible so the user sees
        // "Pas corect — continuă." until they start the next step.
        const nextStep = currentStep + 1;
        if (nextStep >= steps.length) {
          setComplete(true);
        } else {
          setCurrentStep(nextStep);
          setInputValue("");
          // lastReply stays set — cleared on the next submit (see setLastReply at top of handleSubmit)
        }
      }
      // If wrong: stay on the same step; keep lastReply visible (REQ-5 — caught at the step)
    } catch (e) {
      setError(String(e));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      {/* Skeleton table — all steps, past verdicts rendered (REQ-6 skeleton visibility) */}
      <table data-testid="trace-drill-skeleton" className="w-full border-collapse text-sm font-mono">
        <thead>
          <tr className="border-b border-border-strong">
            <th className="p-2 text-left text-page-fg/60 font-normal">Pas</th>
            <th className="p-2 text-left text-page-fg/60 font-normal">Descriere</th>
            <th className="p-2 text-left text-page-fg/60 font-normal">Valoare</th>
            <th className="p-2 text-left text-page-fg/60 font-normal">Verdict</th>
          </tr>
        </thead>
        <tbody>
          {steps.map((step) => {
            const pastResult = results.find((r) => r.stepIndex === step.index && r.reply.verdict.passed);
            const isCurrent = step.index === currentStep && !isFinished;
            return (
              <tr
                key={step.index}
                className={`border-b border-border-strong/40 ${isCurrent ? "bg-page-bg/60" : ""}`}
              >
                <td className="p-2 text-page-fg/50">{step.index + 1}</td>
                <td className="p-2">{step.label_ro ?? `Pasul ${step.index + 1}`}</td>
                <td className="p-2">
                  {pastResult ? (
                    <span className="text-green-700 dark:text-green-400">{pastResult.value}</span>
                  ) : isCurrent ? (
                    <span className="text-page-fg/40 italic">—</span>
                  ) : (
                    <span className="text-page-fg/20">—</span>
                  )}
                </td>
                <td className="p-2">
                  {pastResult ? (
                    <span className="text-green-600 text-xs">✓</span>
                  ) : null}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {/* Current step input + verdict */}
      {!isFinished && (
        <div className="flex flex-col gap-2">
          <label className="text-xs font-semibold text-page-fg/70">
            {practiceStrings.traceDrillStepPrompt} {currentStep + 1}
            {steps[currentStep]?.label_ro ? ` — ${steps[currentStep].label_ro}` : ""}
          </label>
          <input
            data-testid="trace-drill-step-input"
            type="text"
            className="rounded border border-border-strong p-2 font-mono text-sm bg-page-bg text-page-fg w-full"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            disabled={submitting}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSubmit();
            }}
          />

          {/* Per-step verdict (shown after wrong submission — REQ-5 caught at step) */}
          {lastReply && (
            <span
              data-testid="trace-drill-step-verdict"
              className={`text-xs font-semibold ${lastReply.verdict.passed ? "text-green-600" : "text-red-600"}`}
            >
              {lastReply.verdict.passed
                ? practiceStrings.traceDrillStepCorrect
                : practiceStrings.traceDrillStepWrong}
              {lastReply.feedback_ro && lastReply.feedback_ro !== (lastReply.verdict.passed ? practiceStrings.traceDrillStepCorrect : practiceStrings.traceDrillStepWrong) && (
                <span className="ml-1 text-page-fg/50">{lastReply.feedback_ro}</span>
              )}
            </span>
          )}

          <div className="flex gap-2">
            <button
              data-testid="trace-drill-submit-btn"
              className="rounded bg-accent px-4 py-2 text-sm font-semibold text-accent-fg disabled:opacity-50"
              onClick={handleSubmit}
              disabled={submitting || !inputValue.trim()}
            >
              {practiceStrings.traceDrillSubmitStep}
            </button>
            <button
              data-testid="trace-drill-giveup-btn"
              className="rounded border border-border-strong px-4 py-2 text-sm text-page-fg/60 disabled:opacity-50"
              onClick={() => setComplete(true)}
              disabled={submitting}
            >
              {practiceStrings.traceDrillGiveUpBtn}
            </button>
          </div>
        </div>
      )}

      {/* Completion banner */}
      {isFinished && (
        <div className="rounded border border-green-500 p-3 text-sm text-green-700 dark:text-green-400">
          {practiceStrings.traceDrillComplete}
        </div>
      )}

      {error && (
        <div className="text-xs text-red-600">{error}</div>
      )}
    </div>
  );
}
