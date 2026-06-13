import { useState } from "react";
import { gradeProof } from "../../lib/practiceApi";
import type { PracticeProblem, ProofGradeReply, ProofSubstepAnswer } from "../../lib/practiceApi";
import { practiceStrings } from "../../lib/practiceStrings";
import type { ItemVerdict } from "../../lib/drillGrader";

/**
 * Plan-6 Task 9 — Structured proof-drill surface (REQ-2/3/4, spec §6.2.1).
 *
 * Renders the proof fill-in frame and named sub-steps; submits per-substep answers
 * to POST /api/v1/practice/proof/{id}/grade; shows per-substep ItemVerdict scores.
 *
 * Testids: proof-drill-frame, proof-drill-substeps, proof-drill-substep-score,
 *          proof-drill-submit-btn.
 */

interface Props {
  problem: PracticeProblem;
}

export function ProofDrill({ problem }: Props) {
  const substeps = problem.proof_frame?.substeps ?? [];
  const [answers, setAnswers] = useState<Record<string, string>>(() =>
    Object.fromEntries(substeps.map((s) => [s.id, ""])),
  );
  const [reply, setReply] = useState<ProofGradeReply | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function verdictForId(id: string): ItemVerdict | undefined {
    return reply?.item_verdicts.find((v) => v.id === id);
  }

  async function handleSubmit() {
    setSubmitting(true);
    setError(null);
    try {
      const substepPayload: ProofSubstepAnswer[] = substeps.map((s) => ({
        id: s.id,
        text: answers[s.id] ?? "",
      }));
      const result = await gradeProof(problem.id, substepPayload);
      setReply(result);
    } catch (e) {
      setError(String(e));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      {/* Fill-in frame (REQ-3) */}
      <section data-testid="proof-drill-frame" className="border border-border-strong p-3 font-mono text-sm whitespace-pre-wrap">
        {problem.proof_frame?.template_ro ?? problem.statement_ro}
      </section>

      {/* Named sub-steps list (REQ-2) */}
      <section data-testid="proof-drill-substeps" className="flex flex-col gap-3">
        {substeps.map((step) => {
          const verdict = verdictForId(step.id);
          return (
            <div key={step.id} className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-page-fg/70">
                {step.label_ro}
              </label>
              <textarea
                className="w-full rounded border border-border-strong p-2 font-mono text-sm resize-y min-h-[60px] bg-page-bg text-page-fg"
                value={answers[step.id] ?? ""}
                onChange={(e) =>
                  setAnswers((prev) => ({ ...prev, [step.id]: e.target.value }))
                }
                disabled={submitting || reply !== null}
                aria-label={step.label_ro}
              />
              {/* Per-substep score verdict (REQ-4, shown after submit) */}
              {verdict !== undefined && (
                <span
                  data-testid="proof-drill-substep-score"
                  className={`text-xs font-semibold ${verdict.passed ? "text-green-600" : "text-red-600"}`}
                >
                  {verdict.passed
                    ? practiceStrings.proofDrillVerdictPass
                    : practiceStrings.proofDrillVerdictFail}
                  {verdict.points_earned !== null && verdict.points_max !== null && (
                    <span className="ml-1 text-page-fg/50">
                      ({verdict.points_earned}/{verdict.points_max})
                    </span>
                  )}
                </span>
              )}
            </div>
          );
        })}
      </section>

      {/* Feedback after submit */}
      {reply && (
        <div className="text-sm text-page-fg/70 border-t border-border-strong pt-2">
          {practiceStrings.feedbackLabel} {reply.feedback_ro}
        </div>
      )}

      {error && (
        <div className="text-xs text-red-600">{error}</div>
      )}

      {/* Submit button */}
      {!reply && (
        <button
          data-testid="proof-drill-submit-btn"
          className="self-start rounded bg-accent px-4 py-2 text-sm font-semibold text-accent-fg disabled:opacity-50"
          onClick={handleSubmit}
          disabled={submitting}
        >
          {practiceStrings.proofDrillSubmitBtn}
        </button>
      )}
    </div>
  );
}
