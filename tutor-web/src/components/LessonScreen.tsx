/**
 * LessonScreen — T8-7.
 * Full lesson flow: Entry (0c) → TermLanding (0d) → RetrievalGate (0e).
 * Fetches GET /api/v1/lesson/{kcId} (via getLesson).
 * 404 → renders lesson-unavailable ("KC nu este încă verificat").
 *
 * Steps:
 *   "entry"     — LessonEntryBand + ConcreteQuestionBlock (surface 0c)
 *   "term"      — EchoBand + TermLanding + PredictionGate (surface 0d)
 *   "retrieval" — InvariantRule + RetrievalGate (surface 0e)
 *
 * testids: lesson-screen · lesson-step-entry · lesson-step-term ·
 *          lesson-step-retrieval · lesson-unavailable
 */
import { useEffect, useState } from "react";
import type { ApiLessonReply } from "../lib/lesson";
import { getLesson } from "../lib/lesson";
import { LessonEntryBand } from "./LessonEntryBand";
import { ConcreteQuestionBlock } from "./ConcreteQuestionBlock";
import { EchoBand } from "./EchoBand";
import { TermLanding } from "./TermLanding";
import { PredictionGate } from "./PredictionGate";
import { InvariantRule } from "./InvariantRule";
import { RetrievalGate } from "./RetrievalGate";

interface LessonScreenProps {
  /** The KC identifier, typically sourced from route param :kcId. */
  kcId: string;
}

type Step = "entry" | "term" | "retrieval" | "done";

type LoadState =
  | { status: "loading" }
  | { status: "unavailable" }
  | { status: "ready"; lesson: ApiLessonReply };

export function LessonScreen({ kcId }: LessonScreenProps) {
  const [load, setLoad] = useState<LoadState>({ status: "loading" });
  const [step, setStep] = useState<Step>("entry");
  const [entryAnswer, setEntryAnswer] = useState<string>("");

  useEffect(() => {
    let cancelled = false;
    setLoad({ status: "loading" });
    setStep("entry");
    setEntryAnswer("");

    getLesson(kcId)
      .then((res) => {
        if (cancelled) return;
        if (res === null) {
          setLoad({ status: "unavailable" });
        } else {
          setLoad({ status: "ready", lesson: res });
        }
      })
      .catch(() => {
        if (!cancelled) setLoad({ status: "unavailable" });
      });

    return () => { cancelled = true; };
  }, [kcId]);

  if (load.status === "loading") {
    return (
      <div data-testid="lesson-screen" className="flex-1 p-6 font-mono text-xs text-page-fg/50 tracking-widest">
        Se încarcă…
      </div>
    );
  }

  if (load.status === "unavailable") {
    return (
      <div data-testid="lesson-screen" className="flex-1 p-6 font-mono">
        <div
          data-testid="lesson-unavailable"
          className="border-2 border-border-strong p-4 text-xs text-page-fg/60 tracking-wide"
        >
          KC nu este încă verificat — revin mai târziu.
        </div>
      </div>
    );
  }

  const { lesson } = load;

  // Derive the question text: stem_template ?? kc_name_ro (honest-degraded, Romanian)
  const questionText = lesson.concrete_question_ro ?? `Ce știi despre ${lesson.kc_name_ro}?`;

  // Retrieval prompt: explanation or kc name
  const retrievalPrompt = lesson.explanation_ro
    ? `Explică cu propriile cuvinte: ${lesson.kc_name_ro}`
    : `Definește conceptul: ${lesson.kc_name_ro}`;

  function handleEntryAnswer(text: string) {
    setEntryAnswer(text);
    setStep("term");
  }

  function handlePredict(_option: string) {
    setStep("retrieval");
  }

  function handleRetrieval(_answer: string) {
    setStep("done");
  }

  return (
    <div data-testid="lesson-screen" className="flex flex-col h-full overflow-y-auto font-mono">

      {/* Step 0c — Entry */}
      {step === "entry" && (
        <div data-testid="lesson-step-entry" className="flex flex-col">
          <LessonEntryBand subject={lesson.kc_name_en} kcNameRo={lesson.kc_name_ro} />
          <ConcreteQuestionBlock question={questionText} onAnswer={handleEntryAnswer} />
        </div>
      )}

      {/* Step 0d — Term + EchoBand + PredictionGate */}
      {step === "term" && (
        <div data-testid="lesson-step-term" className="flex flex-col gap-4 p-4">
          {/* Echo the student's entry answer */}
          {entryAnswer && <EchoBand text={entryAnswer} />}

          {/* Highlight the new term */}
          <TermLanding termRo={lesson.term_ro} termEn={lesson.kc_name_en} />

          {/* Source quote (EchoBand reused for source echo) */}
          {lesson.echo_source_ro && (
            <div>
              <span className="font-mono text-[10px] uppercase tracking-widest text-page-fg/40 block mb-1">
                Din materialul tău:
              </span>
              <EchoBand text={lesson.echo_source_ro} />
            </div>
          )}

          {/* Prediction gate — empty options = gate disabled (degrade gracefully) */}
          {lesson.prediction_options.length > 0 && (
            <PredictionGate options={lesson.prediction_options} onPredict={handlePredict} />
          )}

          {/* If no prediction options, skip gate and go directly to retrieval */}
          {lesson.prediction_options.length === 0 && (
            <button
              onClick={() => setStep("retrieval")}
              className="self-start border-2 border-accent bg-accent text-black font-mono font-bold text-xs tracking-widest uppercase px-4 py-2 hover:bg-accent-hover transition-colors"
            >
              Continuă
            </button>
          )}
        </div>
      )}

      {/* Step 0e — RetrievalGate */}
      {(step === "retrieval" || step === "done") && (
        <div data-testid="lesson-step-retrieval" className="flex flex-col gap-4">
          {lesson.explanation_ro && (
            <InvariantRule body={lesson.explanation_ro} />
          )}
          {step === "retrieval" && (
            <RetrievalGate prompt={retrievalPrompt} onSubmit={handleRetrieval} />
          )}
          {step === "done" && (
            <div className="p-4 font-mono text-xs text-page-fg/60 tracking-wide border-t border-border-strong">
              Lecție completă. Bine făcut!
            </div>
          )}
        </div>
      )}
    </div>
  );
}
