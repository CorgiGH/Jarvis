import { useState, useCallback } from "react";
import { MockTimer } from "./MockTimer";
import { MockQuestionBlock } from "./MockQuestionBlock";
import { MockQuestionNav } from "./MockQuestionNav";
import { practiceStrings } from "../lib/practiceStrings";

/**
 * Plan-6 Task 11 (§6.2.4, REQ-11..17) — ADDITIVE mock-exam props.
 *
 * The legacy presentational contract ({subject, questions, timeLimitSeconds, onSubmit}) is UNCHANGED;
 * every additive prop below is OPTIONAL and defaults so a legacy caller renders byte-identically (the
 * component test pins both modes). When the additive props are supplied the shell renders the
 * permitted-materials phase, the synthetic-tag honesty banner, the per-G-item rubric breakdown
 * (post-submit), and the additive `mock-exam-*` selectors the spec lists (REQ-17).
 */

/** A current permitted-materials phase (mirrors the server ApiMockExamPhase, snake_case → camelCase). */
export interface MockExamPhase {
  phaseIndex: number;
  labelRo: string;
  materialsAllowedRo: string;
  phaseCount: number;
}

/** One per-G-item rubric verdict (mirrors the server ItemVerdict). */
export interface MockExamRubricVerdict {
  id: string;
  label: string;
  passed: boolean;
  points_earned?: number | null;
  points_max?: number | null;
}

interface MockExamShellProps {
  subject: string;
  questions: string[];
  timeLimitSeconds: number;
  onSubmit: (answers: string[]) => void;
  // ── ADDITIVE (all optional — legacy callers omit them) ──────────────────────
  phase?: MockExamPhase;
  syntheticTag?: boolean;
  rubricResult?: MockExamRubricVerdict[];
  /** Advance to the next permitted-materials phase (REQ-15). Absent → no phase control. */
  onAdvancePhase?: () => void;
}

export function MockExamShell({
  subject,
  questions,
  timeLimitSeconds,
  onSubmit,
  phase,
  syntheticTag,
  rubricResult,
  onAdvancePhase,
}: MockExamShellProps) {
  const [answers, setAnswers] = useState<string[]>(() =>
    Array(questions.length).fill("")
  );
  const [activeIndex, setActiveIndex] = useState(0);
  const [timeExpired, setTimeExpired] = useState(false);

  const allAnswered = answers.every((a) => a.trim().length > 0);

  const handleAnswer = useCallback((index: number, value: string) => {
    setAnswers((prev) => {
      const next = [...prev];
      next[index] = value;
      return next;
    });
  }, []);

  const handleExpire = useCallback(() => {
    setTimeExpired(true);
  }, []);

  const handleSubmit = useCallback(() => {
    onSubmit(answers);
  }, [answers, onSubmit]);

  return (
    <div
      data-testid="mock-exam-shell"
      className="flex flex-col h-full font-mono bg-page-bg text-page-fg"
    >
      {/* Sticky header: title + timer + nav */}
      <header className="sticky top-0 z-10 bg-page-bg border-b-4 border-border-strong px-4 py-2 flex flex-col gap-2">
        <div className="flex items-center justify-between gap-4">
          <span className="text-xs tracking-widest font-bold text-page-fg/70 uppercase">
            Examen simulare — {subject}
          </span>
          {/* ADDITIVE selector (REQ-17): mock-exam-timer wraps the existing timer. */}
          <div data-testid="mock-exam-timer">
            <MockTimer
              totalSeconds={timeLimitSeconds}
              onExpire={handleExpire}
            />
          </div>
        </div>

        {/* ADDITIVE: synthetic-tag honesty banner (REQ-12/17). */}
        {syntheticTag && (
          <p
            data-testid="mock-exam-synthetic-tag"
            className="text-[11px] tracking-widest text-danger-text border-2 border-danger-text px-2 py-1"
          >
            {practiceStrings.mockExamSyntheticTag}
          </p>
        )}

        {/* ADDITIVE: current permitted-materials phase (REQ-15/17). */}
        {phase && (
          <div
            data-testid="mock-exam-phase"
            className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] tracking-wide text-page-fg/80"
          >
            <span>
              {practiceStrings.mockExamPhaseLabel}{" "}
              <span className="font-bold">{phase.labelRo}</span>
            </span>
            <span className="text-page-fg/50">
              ({phase.phaseIndex + 1} {practiceStrings.mockExamPhaseProgress}{" "}
              {phase.phaseCount})
            </span>
            {phase.materialsAllowedRo.trim().length > 0 && (
              <span>
                {practiceStrings.mockExamPhaseMaterialsLabel}{" "}
                {phase.materialsAllowedRo}
              </span>
            )}
            {onAdvancePhase && phase.phaseIndex < phase.phaseCount - 1 && (
              <button
                data-testid="mock-exam-phase-advance"
                onClick={onAdvancePhase}
                className="px-2 py-0.5 text-[10px] font-bold tracking-widest border-2 border-border-strong hover:bg-accent hover:border-accent transition-colors"
              >
                {practiceStrings.mockExamPhaseAdvanceBtn}
              </button>
            )}
          </div>
        )}

        <div className="flex items-center gap-3">
          <MockQuestionNav
            count={questions.length}
            answers={answers}
            activeIndex={activeIndex}
            onSelect={setActiveIndex}
          />
          <button
            data-testid="mock-submit-btn"
            disabled={!allAnswered || timeExpired}
            onClick={handleSubmit}
            className="ml-auto px-4 py-1 text-xs font-bold tracking-widest border-2 border-border-strong bg-page-bg text-page-fg hover:bg-accent hover:border-accent hover:text-page-fg disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            TRIMITE
          </button>
        </div>
        {timeExpired && (
          <p className="text-danger-text text-xs tracking-widest">
            Timp expirat — răspunsurile au fost blocate.
          </p>
        )}
      </header>

      {/* Question blocks */}
      <main className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-4 p-4">
        {/* ADDITIVE: sub-question ordering hint (REQ-17 "sub-question ordering visible"). */}
        <p
          data-testid="mock-exam-question"
          className="text-[11px] tracking-wide text-page-fg/50"
        >
          {practiceStrings.mockExamSubQuestionOrderHint}
        </p>
        {questions.map((q, i) => (
          <MockQuestionBlock
            key={i}
            index={i}
            question={q}
            answer={answers[i] ?? ""}
            onAnswer={handleAnswer}
            isActive={i === activeIndex}
          />
        ))}

        {/* ADDITIVE: per-G-item rubric breakdown, post-submit (REQ-16/17). */}
        {rubricResult && rubricResult.length > 0 && (
          <section
            data-testid="mock-exam-rubric-result"
            className="border-2 border-border-strong p-3 flex flex-col gap-2"
          >
            <h3 className="text-xs font-bold tracking-widest uppercase text-page-fg/70">
              {practiceStrings.mockExamRubricResultTitle}
            </h3>
            <ul className="flex flex-col gap-1">
              {rubricResult.map((v) => (
                <li
                  key={v.id}
                  data-testid={`mock-exam-rubric-item-${v.id}`}
                  className="flex items-center justify-between gap-3 text-xs"
                >
                  <span className="flex items-center gap-2">
                    <span
                      className={`font-bold ${
                        v.passed ? "text-accent" : "text-danger-text"
                      }`}
                    >
                      {v.passed ? "✓" : "✗"}
                    </span>
                    <span>{v.label}</span>
                  </span>
                  <span className="text-page-fg/60">
                    {v.points_earned ?? 0} {practiceStrings.mockExamRubricPointsOf}{" "}
                    {v.points_max ?? 0}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        )}
      </main>
    </div>
  );
}
