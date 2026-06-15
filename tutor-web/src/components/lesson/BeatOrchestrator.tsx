import { useCallback, useMemo, useState } from "react";
import type { ApiLessonReply, ApiPredictOption } from "../../lib/lesson";
import { postBeatGrade } from "../../lib/beatGrade";
import { lessonStrings, BEAT_GLYPHS, beatKindLabel } from "../../lib/lessonStrings";
import { PredictBeat } from "./PredictBeat";
import { AttemptBeat } from "./AttemptBeat";
import { RevealBeat } from "./RevealBeat";
import { NameBeat } from "./NameBeat";
import { CheckBeat } from "./CheckBeat";
import { LessonErrorBoundary } from "./LessonErrorBoundary";

interface BeatOrchestratorProps {
  kcId: string;
  lesson: ApiLessonReply;
  /** Called with the kcId when the learner clicks the drill handoff (Task 10 wires the route). */
  onComplete: (kcId: string) => void;
}

/**
 * Plan-3 §0.9F — the gated lesson state machine. Replaces LessonScreen's 3-step flow (audit E1–E4).
 * ONE active beat; Next (beat-next-gate) disabled until the active beat's gate clears. Server grades
 * every gated beat via POST; the client never self-grades writes (spec §4.4).
 */
export function BeatOrchestrator({ kcId, lesson, onComplete }: BeatOrchestratorProps) {
  const beats = lesson.beats;
  const plan = beats?.plan ?? [];

  const [activeIdx, setActiveIdx] = useState(0);
  const [cleared, setCleared] = useState<Record<number, boolean>>({});
  const [busy, setBusy] = useState(false);
  const [predictIndex, setPredictIndex] = useState<number | null>(null);
  const [attemptIndex, setAttemptIndex] = useState<number | null>(null);
  const [checkSubmitted, setCheckSubmitted] = useState(false);
  const [checkFeedback, setCheckFeedback] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const predictedOption: ApiPredictOption | null = useMemo(() => {
    if (predictIndex == null || !beats?.predict) return null;
    return beats.predict.options[predictIndex] ?? null;
  }, [predictIndex, beats]);

  const markCleared = useCallback((idx: number) => {
    setCleared((c) => (c[idx] ? c : { ...c, [idx]: true }));
  }, []);

  if (!beats || plan.length === 0) {
    return (
      <div data-testid="lesson-screen" className="flex-1 p-6 font-mono">
        <div data-testid="lesson-unavailable" className="border-2 border-border-strong p-4 text-xs text-page-fg/60 tracking-wide">
          {lessonStrings.unavailable}
        </div>
      </div>
    );
  }

  const kind = plan[activeIdx];
  const isLast = activeIdx === plan.length - 1;
  const gateOpen = !!cleared[activeIdx] && !busy;

  async function commitPredict(index: number) {
    if (busy || predictIndex != null) return;
    setBusy(true);
    setPredictIndex(index);
    try {
      await postBeatGrade(kcId, {
        beat_type: "predict",
        selected_index: index,
        prediction_text: beats!.predict?.options[index]?.text ?? null,
      });
      markCleared(activeIdx);
    } finally {
      setBusy(false);
    }
  }

  async function commitAttempt(index: number) {
    if (busy || attemptIndex != null) return;
    setBusy(true);
    setAttemptIndex(index);
    try {
      await postBeatGrade(kcId, { beat_type: "attempt", selected_index: index });
      markCleared(activeIdx);
    } finally {
      setBusy(false);
    }
  }

  async function submitCheck(req: { selected_index?: number; free_input?: string }) {
    if (busy || checkSubmitted) return;
    setBusy(true);
    setCheckSubmitted(true);
    try {
      const reply = await postBeatGrade(kcId, { beat_type: "check", ...req });
      setCheckFeedback(reply.feedback_ro);
      markCleared(activeIdx);
      if (reply.lesson_complete) setDone(true);
    } finally {
      setBusy(false);
    }
  }

  function onNext() {
    if (!gateOpen) return;
    if (isLast) {
      setDone(true);
      return;
    }
    setActiveIdx((i) => i + 1);
  }

  if (done) {
    return (
      <div data-testid="lesson-complete" className="flex flex-col flex-1 items-center justify-center gap-4 p-8 font-mono">
        <h2 className="text-lg font-bold tracking-widest uppercase text-page-fg">{lessonStrings.complete}</h2>
        <p className="text-xs text-page-fg/70 tracking-wide text-center max-w-md leading-relaxed">{lessonStrings.completeBody}</p>
        <button
          data-testid="lesson-complete-handoff"
          onClick={() => onComplete(kcId)}
          className="border-2 border-accent bg-accent text-black font-bold text-xs tracking-widest uppercase px-6 py-3 shadow-hard hover:bg-accent-hover transition-colors"
        >
          {lessonStrings.handoff}
        </button>
      </div>
    );
  }

  return (
    <div data-testid="lesson-screen" className="flex flex-col h-full overflow-y-auto font-mono">
      {/* Pips */}
      <div data-testid="lesson-beat-pips" className="flex items-center justify-center gap-2.5 p-4 border-b-2 border-accent">
        {plan.map((k, i) => (
          <span
            key={i}
            data-pip
            aria-label={beatKindLabel(k)}
            className={
              "w-8 h-8 flex items-center justify-center border-2 text-sm transition-colors " +
              (i === activeIdx
                ? "border-accent bg-accent text-black font-bold shadow-hard"
                : i < activeIdx
                  ? "border-accent text-accent"
                  : "border-border-strong text-page-fg/40")
            }
          >
            {BEAT_GLYPHS[i] ?? i + 1}
          </span>
        ))}
      </div>

      {/* Active beat — wrapped in a per-beat error boundary (Plan 4b Task 1).
          key={activeIdx} resets the boundary state when the learner advances to a new beat.
          The boundary wraps ONLY the beat body; pips + gate controls stay outside so they
          remain mounted even when a beat component throws. A crashed beat must not open the gate
          (cleared[activeIdx] stays false while the fallback shows). */}
      <div data-testid="lesson-beat-active" className="flex flex-col gap-5 px-5 py-5 flex-1 max-w-3xl w-full mx-auto">
        <span className="font-bold uppercase tracking-widest text-[11px] text-accent">
          {BEAT_GLYPHS[activeIdx]} {beatKindLabel(kind)}
        </span>

        <LessonErrorBoundary key={activeIdx}>
          {kind === "predict" && beats.predict && (
            <PredictBeat predict={beats.predict} committedIndex={predictIndex} onCommit={commitPredict} />
          )}
          {kind === "attempt" && beats.attempt && (
            <AttemptBeat attempt={beats.attempt} committedIndex={attemptIndex} onCommitChoice={commitAttempt} />
          )}
          {kind === "reveal" && beats.reveal && (
            <RevealBeat reveal={beats.reveal} predictedOption={predictedOption} onGateClear={() => markCleared(activeIdx)} />
          )}
          {kind === "name" && beats.name && (
            <NameBeat name={beats.name} onGateClear={() => markCleared(activeIdx)} />
          )}
          {kind === "check" && beats.check && (
            <CheckBeat
              check={beats.check}
              submitted={checkSubmitted}
              feedbackRo={checkFeedback}
              onSubmitChoice={(i) => submitCheck({ selected_index: i })}
              onSubmitNumeric={(v) => submitCheck({ free_input: v })}
            />
          )}
        </LessonErrorBoundary>
      </div>

      {/* Gate controls */}
      <div className="flex items-center justify-between gap-4 px-5 py-4 border-t-2 border-accent">
        <button
          onClick={() => setActiveIdx((i) => Math.max(0, i - 1))}
          disabled={activeIdx === 0}
          className="border-2 border-border-strong px-4 py-2 text-xs tracking-wide text-page-fg disabled:opacity-30 hover:border-accent hover:text-accent transition-colors"
        >
          {lessonStrings.back}
        </button>
        <span className="text-xs text-accent-rule tracking-wide">
          {gateOpen ? "" : (kind === "reveal" ? lessonStrings.gateWatch : lessonStrings.gateAnswer)}
        </span>
        <button
          data-testid="beat-next-gate"
          onClick={onNext}
          disabled={!gateOpen}
          className="border-2 border-accent bg-accent text-black font-bold text-xs tracking-widest uppercase px-6 py-2 shadow-hard disabled:opacity-30 disabled:cursor-not-allowed hover:enabled:bg-accent-hover transition-colors"
        >
          {isLast ? lessonStrings.finish : lessonStrings.next}
        </button>
      </div>
    </div>
  );
}
