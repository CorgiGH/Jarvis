import { useState } from "react";
import { DrillCard } from "./DrillCard";
import type { DrillCardState } from "./DrillCard";
import { gradeDrill } from "../lib/drillGrader";
import type { GradeResult } from "../lib/drillGrader";
import { MathText } from "./MathText";

export interface DrillContent {
  drill: string;
  worked: string;
  definition: string;
  check: string;
  expectedAnswerHint: string;
  /**
   * Slice 3 spike: code-grading mode. When set, the DRILL input renders as a
   * monospace code textarea and `gradeDrill` is invoked with `language` +
   * `referenceSolution` + `rubricItems` so the backend uses the code-grading
   * prompt path. NO execution server-side; pure LLM-as-judge.
   */
  language?: "r" | "python" | "cpp" | "text";
  referenceSolution?: string;
  rubricItems?: string[];
}

interface DrillStackProps {
  taskId: string;
  problemId: string;
  content: DrillContent;
  onProblemComplete: (problemId: string) => void;
}

type StackPhase =
  | "idle"
  | "grading"
  | "correct"
  | "incorrect"
  | "given-up"
  | "check-done";

/**
 * Orchestrates 4 DrillCards in DRILL → WORKED → DEFINITION → CHECK order.
 *
 * State machine:
 *   idle      → user types attempt → click "CHECK ANSWER" → grading
 *   grading   → API responds correct   → correct  (WORKED/DEF/CHECK unlock, stagger 80ms)
 *   grading   → API responds incorrect → incorrect (cards stay locked, misconception banner)
 *   idle      → user clicks "GIVE UP"  → given-up  (POST giveUp=true, unlock all)
 *   correct   → user clicks "MARK CHECK DONE" → check-done → onProblemComplete fires
 *   given-up  → user clicks "MARK CHECK DONE" → check-done → onProblemComplete fires
 *
 * Animation #1 (unlock stagger) is driven by data-stagger-index + CSS
 * `animation-delay` set in DrillCard. No JS timers needed — CSS handles the 80ms cascade.
 */
export function DrillStack({
  taskId,
  problemId,
  content,
  onProblemComplete,
}: DrillStackProps) {
  const [attempt, setAttempt] = useState("");
  const [prediction, setPrediction] = useState("");
  const [phase, setPhase] = useState<StackPhase>("idle");
  const [gradeResult, setGradeResult] = useState<GradeResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const unlocked = phase === "correct" || phase === "given-up" || phase === "check-done";

  function drillState(): DrillCardState {
    if (phase === "check-done" || phase === "correct" || phase === "given-up") return "complete";
    return "open";
  }

  function secondaryState(): DrillCardState {
    if (!unlocked) return "locked";
    if (phase === "check-done") return "complete";
    return "open";
  }

  function checkState(): DrillCardState {
    if (!unlocked) return "locked";
    if (phase === "check-done") return "complete";
    return "open";
  }

  const isCode = !!content.language && content.language !== "text";

  async function handleCheckAnswer() {
    if (phase === "grading") return;
    setPhase("grading");
    setError(null);
    try {
      const result = await gradeDrill({
        taskId,
        problemId,
        problemStatement: content.drill,
        userAttempt: attempt,
        expectedAnswerHint: content.expectedAnswerHint,
        language: content.language,
        referenceSolution: content.referenceSolution,
        rubricItems: content.rubricItems,
        prediction: prediction.trim() || undefined,
      });
      setGradeResult(result);
      setPhase(result.correct ? "correct" : "incorrect");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error — please retry.");
      setPhase("idle");
    }
  }

  async function handleGiveUp() {
    if (phase === "grading") return;
    setPhase("grading");
    setError(null);
    try {
      const result = await gradeDrill({
        taskId,
        problemId,
        problemStatement: content.drill,
        userAttempt: "ATTEMPTED_NOT_SOLVED",
        expectedAnswerHint: content.expectedAnswerHint,
        giveUp: true,
        language: content.language,
        referenceSolution: content.referenceSolution,
        rubricItems: content.rubricItems,
        prediction: prediction.trim() || undefined,
      });
      setGradeResult(result);
      setPhase("given-up");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error — please retry.");
      setPhase("idle");
    }
  }

  function handleCheckDone() {
    setPhase("check-done");
    onProblemComplete(problemId);
  }

  return (
    <div data-testid="drill-stack" className="flex flex-col gap-4 p-4">
      {/* Card order: DRILL → WORKED → DEFINITION → CHECK (§D productive-failure inversion) */}

      {/* 1. DRILL card — always open */}
      <DrillCard
        cardType="DRILL"
        title="③ DRILL · YOUR TURN"
        state={drillState()}
        staggerIndex={0}
      >
        <MathText text={content.drill} className="mb-4 text-sm" />
        <div className="mb-3">
          <label
            htmlFor="drill-prediction-input"
            data-testid="drill-prediction-label"
            className="block mb-1 font-mono text-[10px] uppercase tracking-widest text-page-fg/70"
          >
            predict in plain language (optional) — what should the answer look like, before you write it?
          </label>
          <textarea
            id="drill-prediction-input"
            data-testid="drill-prediction-input"
            value={prediction}
            onChange={(e) => setPrediction(e.target.value)}
            disabled={unlocked || phase === "grading"}
            rows={2}
            placeholder="e.g., the histogram should look symmetric around 0, peaked, with heavier tails as b grows"
            className="w-full border-2 border-border-thin bg-page-bg font-mono text-xs p-2 resize-none focus:outline-none focus:border-accent disabled:opacity-50"
          />
          <div className="mt-1 text-[9px] uppercase tracking-widest text-page-fg/40">
            generation-effect: even a wrong guess BEFORE attempting locks in better than passive reading (Slamecka 1978).
          </div>
        </div>
        {isCode && content.rubricItems && content.rubricItems.length > 0 && (
          <div
            data-testid="drill-rubric"
            className="mb-3 border-l-4 border-border-strong bg-page-bg/40 px-3 py-2 text-[10px] font-mono leading-relaxed text-page-fg/80"
          >
            <div className="mb-1 font-bold uppercase tracking-wider text-page-fg/60">
              rubric — must satisfy all
            </div>
            <ul className="list-disc pl-4">
              {content.rubricItems.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        )}
        <textarea
          data-testid="drill-attempt-input"
          data-language={content.language ?? "text"}
          value={attempt}
          onChange={(e) => setAttempt(e.target.value)}
          disabled={unlocked || phase === "grading"}
          rows={isCode ? 14 : 3}
          spellCheck={isCode ? false : undefined}
          placeholder={
            isCode
              ? `# ${content.language?.toUpperCase()} code — write a complete script, no execution server-side`
              : "Type your answer here…"
          }
          className={
            isCode
              ? "w-full border-2 border-border-strong bg-page-bg font-mono text-xs leading-relaxed p-3 resize-y focus:outline-none focus:border-accent disabled:opacity-50 whitespace-pre"
              : "w-full border-2 border-border-strong bg-page-bg font-mono text-xs p-2 resize-none focus:outline-none focus:border-accent disabled:opacity-50"
          }
        />
        {gradeResult && (
          <div
            data-testid="grade-feedback"
            className={`mt-3 px-3 py-2 border-l-4 font-mono text-xs leading-relaxed whitespace-pre-wrap ${
              gradeResult.correct
                ? "border-accent bg-accent/10 text-page-fg"
                : "border-danger-text bg-danger-text/10 text-page-fg"
            }`}
          >
            {gradeResult.elaboratedFeedback}
          </div>
        )}
        {gradeResult && isCode && Object.keys(gradeResult.rubric).length > 0 && (
          <div
            data-testid="rubric-grade"
            className="mt-2 px-3 py-2 border-2 border-border-thin font-mono text-[10px] leading-relaxed"
          >
            <div className="mb-1 font-bold uppercase tracking-wider text-page-fg/60">
              rubric grade
            </div>
            <ul>
              {Object.entries(gradeResult.rubric).map(([k, v]) => (
                <li
                  key={k}
                  className={v ? "text-accent" : "text-danger-text"}
                  data-rubric-key={k}
                  data-rubric-pass={v}
                >
                  {v ? "[✓]" : "[✗]"} {k.replace(/_/g, " ")}
                </li>
              ))}
            </ul>
          </div>
        )}
        {gradeResult && !gradeResult.correct && gradeResult.misconception && (
          <div
            data-testid="misconception-banner"
            className="mt-2 px-3 py-1.5 bg-panel-dark-bg text-panel-dark-fg font-mono text-[10px] tracking-widest"
          >
            MISCONCEPTION · {gradeResult.misconception.replace(/_/g, " ")}
          </div>
        )}
        {error && (
          <div className="mt-2 text-danger-text font-mono text-xs">{error}</div>
        )}
        {!unlocked && (
          <div className="mt-3 flex gap-2">
            <button
              onClick={handleCheckAnswer}
              disabled={phase === "grading" || attempt.trim().length === 0}
              className="px-4 py-1.5 bg-accent text-page-fg font-mono text-xs font-bold tracking-widest border-2 border-border-strong hover:bg-accent-hover disabled:opacity-40 transition-all duration-[280ms] ease-in-out active:scale-95"
            >
              {phase === "grading" ? "GRADING…" : "CHECK ANSWER"}
            </button>
            <button
              onClick={handleGiveUp}
              disabled={phase === "grading"}
              className="px-4 py-1.5 text-page-fg/60 font-mono text-xs tracking-widest border-2 border-border-thin hover:text-page-fg hover:border-border-strong disabled:opacity-40"
            >
              give up — show solution
            </button>
          </div>
        )}
      </DrillCard>

      {/* 2. WORKED EXAMPLE card — locked until drill graded */}
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state={secondaryState()}
        staggerIndex={1}
      >
        <MathText text={content.worked} className="text-sm" />
      </DrillCard>

      {/* 3. DEFINITION card — locked until drill graded */}
      <DrillCard
        cardType="DEFINITION"
        title="① DEFINITION"
        state={secondaryState()}
        staggerIndex={2}
      >
        <MathText text={content.definition} className="text-sm" />
      </DrillCard>

      {/* 4. CHECK card — locked until drill graded */}
      <DrillCard
        cardType="CHECK"
        title="④ CHECK · TRANSFER"
        state={checkState()}
        staggerIndex={3}
      >
        <MathText text={content.check} className="mb-4 text-sm" />
        {unlocked && phase !== "check-done" && (
          <button
            onClick={handleCheckDone}
            className="px-4 py-1.5 bg-accent text-page-fg font-mono text-xs font-bold tracking-widest border-2 border-border-strong hover:bg-accent-hover transition-all duration-[280ms] ease-in-out active:scale-95"
          >
            MARK CHECK DONE
          </button>
        )}
      </DrillCard>
    </div>
  );
}
