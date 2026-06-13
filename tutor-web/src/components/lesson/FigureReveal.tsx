/**
 * Plan-4b Task 4 — FigureReveal (spec §4.6/§4.7/§5.3; §0.9A).
 * Consumes ApiBeatReveal.figure exactly as frozen in lock §NEW-L ({family_id, instance_id},
 * snake_case) and the §0.9B viz-serve reply. Zero wire changes — cite §NEW-L.
 *
 * When reveal.figure is set, FigureReveal IS the reveal:
 *   - Fetches the instance via GET /api/v1/viz/{instanceId} (§0.9B)
 *   - Dispatches on family_id via familyRegistry
 *   - Mounts the family (which mounts AlgoStepperShell) with RO chrome from chromeStrings
 *   - onGateClear fires once the final frame has been reached at least once (§4.1)
 *   - On any fetch/parse/registry failure: degrades to the stepped-text path +
 *     figureFallback message, zero console.error (gate 4c: caught, not silent or escalated)
 *
 * Selectors (§4.7):
 *   - wrapper: data-testid="beat-figure-scrubber"
 *   - own step counter: data-testid="scrubber-step-counter" showing "pas k/N"
 *   - shell contributes: graph-tree-step-back/-step-fwd/-reset/-play + range scrubber
 *
 * The predict echo banner renders above this component (RevealBeat moves it above the branch).
 * The stepped-text path is the boundary fallback — the authored reveal.steps are NOT dead.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { jarvisFetch } from "../../lib/api";
import { lessonStrings } from "../../lib/lessonStrings";
import { shellLabels, figureFallback } from "../../lib/chromeStrings";
import { familyRegistry } from "../viz/families/familyRegistry";
import type { ApiFigureBinding, ApiRevealStep } from "../../lib/lesson";

/** §0.9B ApiVizInstanceReply shape — snake_case on the wire (backend casing wins). */
interface ApiVizInstanceReply {
  id: string;
  subject: string;
  family_id: string;
  language: string;
  data_json: string;
}

interface FigureRevealProps {
  figure: ApiFigureBinding;
  /** The authored reveal steps — used as fallback when the figure fails to load/render. */
  steps: ApiRevealStep[];
  predictedOption: null | { text: string; callback: string; correct: boolean };
  onGateClear: () => void;
}

type FigureState =
  | { status: "loading" }
  | { status: "ready"; reply: ApiVizInstanceReply }
  | { status: "fallback"; reason: string };

/**
 * Stepped-text fallback: the real RevealBeat stepped-text path, extracted so both the
 * fallback and the boundary degrade to identical behaviour — spec §0.9A: "the fallback
 * IS the real text path", not a stub.
 */
function SteppedTextReveal({
  steps,
  onGateClear,
}: {
  steps: ApiRevealStep[];
  onGateClear: () => void;
}) {
  const n = steps.length;
  const [idx, setIdx] = useState(0);
  const [reachedEnd, setReachedEnd] = useState(n === 1);
  const gateRef = useRef(false);

  useEffect(() => {
    if (idx === n - 1 && !reachedEnd) setReachedEnd(true);
  }, [idx, n, reachedEnd]);

  useEffect(() => {
    if (reachedEnd && !gateRef.current) {
      gateRef.current = true;
      onGateClear();
    }
  }, [reachedEnd, onGateClear]);

  const back = useCallback(() => setIdx((i) => Math.max(0, i - 1)), []);
  const fwd = useCallback(() => setIdx((i) => Math.min(n - 1, i + 1)), [n]);

  const step = steps[idx];
  return (
    <div data-testid="beat-figure-scrubber" className="flex flex-col gap-3 font-mono">
      <div className="border-2 border-border-strong p-4 flex flex-col gap-2 shadow-hard">
        <p className="text-sm text-page-fg leading-relaxed">{step.text}</p>
        <p className="border-l-4 border-accent pl-3 text-xs text-page-fg/80 leading-relaxed">
          {step.callout}
        </p>
      </div>
      <div className="flex items-center gap-3">
        <button
          data-step-back
          onClick={back}
          disabled={idx === 0}
          className="border-2 border-page-fg px-3 py-1 text-xs tracking-wide text-page-fg disabled:opacity-30 hover:border-accent hover:text-accent"
        >
          {lessonStrings.back}
        </button>
        <span data-testid="scrubber-step-counter" className="text-xs text-page-fg/60 tracking-wide">
          {lessonStrings.step} {idx + 1}/{n}
        </span>
        <button
          data-step-fwd
          onClick={fwd}
          disabled={idx === n - 1}
          className="border-2 border-page-fg px-3 py-1 text-xs tracking-wide text-page-fg disabled:opacity-30 hover:border-accent hover:text-accent"
        >
          {lessonStrings.next}
        </button>
      </div>
    </div>
  );
}

export function FigureReveal({ figure, steps, predictedOption, onGateClear }: FigureRevealProps) {
  const [figureState, setFigureState] = useState<FigureState>({ status: "loading" });

  // Whether the gate has already fired — fire only once (§4.1).
  const gateFiredRef = useRef(false);
  // Internal counter state driven by the family's onStep callback.
  const [stepDisplay, setStepDisplay] = useState({ idx: 0, lastIdx: 0 });

  // Fetch the viz instance on mount. Result is read-only; no DB, no LLM (§0.9B).
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await jarvisFetch(
          `/api/v1/viz/${encodeURIComponent(figure.instance_id)}`,
        );
        if (!res.ok) {
          console.warn(
            `FigureReveal: fetch ${figure.instance_id} returned ${res.status} — fallback`,
          );
          if (!cancelled) setFigureState({ status: "fallback", reason: `HTTP ${res.status}` });
          return;
        }
        const reply = (await res.json()) as ApiVizInstanceReply;
        // family_id consistency check: if the wire reply's family_id doesn't match the
        // binding's family_id, that's a data error — degrade rather than dispatch on the
        // wrong family and render garbage (§0.9A, the ghost-component class).
        if (reply.family_id !== figure.family_id) {
          console.warn(
            `FigureReveal: binding family_id "${figure.family_id}" != reply family_id "${reply.family_id}" — fallback`,
          );
          if (!cancelled) setFigureState({ status: "fallback", reason: "family_id mismatch" });
          return;
        }
        // Verify the family is registered before committing to "ready".
        if (!familyRegistry[reply.family_id]) {
          console.warn(
            `FigureReveal: family_id "${reply.family_id}" not in familyRegistry — fallback`,
          );
          if (!cancelled) setFigureState({ status: "fallback", reason: "unknown family" });
          return;
        }
        if (!cancelled) setFigureState({ status: "ready", reply });
      } catch (err) {
        console.warn(`FigureReveal: fetch error for ${figure.instance_id} — fallback`, err);
        if (!cancelled) setFigureState({ status: "fallback", reason: String(err) });
      }
    }
    load();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [figure.instance_id, figure.family_id]);

  // onStep callback for the family — updates the display counter + fires the gate.
  // Wrapped in useCallback; the dep list is minimal to avoid spurious re-fires.
  const handleStep = useCallback(
    (idx: number, lastIdx: number) => {
      // Bail when the value is unchanged so a redundant onStep fire cannot churn a new
      // object literal into state and trigger an unnecessary re-render (defense-in-depth
      // against the render loop; the primary fix is the stable onStep ref in the family).
      setStepDisplay((prev) =>
        prev.idx === idx && prev.lastIdx === lastIdx ? prev : { idx, lastIdx },
      );
      if (idx === lastIdx && !gateFiredRef.current) {
        gateFiredRef.current = true;
        onGateClear();
      }
    },
    [onGateClear],
  );

  if (figureState.status === "loading") {
    return (
      <div data-testid="beat-figure-scrubber" className="font-mono text-xs text-page-fg/60">
        <span>{lessonStrings.loading}</span>
      </div>
    );
  }

  if (figureState.status === "fallback") {
    return (
      <div className="flex flex-col gap-3">
        <p
          data-testid="figure-fallback-message"
          className="text-xs text-page-fg/60 font-mono border-l-4 border-border-strong pl-3 py-1"
        >
          {figureFallback}
        </p>
        <SteppedTextReveal steps={steps} onGateClear={onGateClear} />
      </div>
    );
  }

  // "ready" — mount the family
  const { reply } = figureState;
  const FamilyRenderer = familyRegistry[reply.family_id];
  // FamilyRenderer is guaranteed to be defined: we checked in load() above.

  return (
    <div data-testid="beat-figure-scrubber" className="flex flex-col gap-3">
      {/** Own step counter (§4.7): "pas k/N" driven by the family's onStep callback. */}
      <span
        data-testid="scrubber-step-counter"
        className="text-xs text-page-fg/60 tracking-wide font-mono"
      >
        {lessonStrings.step} {stepDisplay.idx + 1}/{stepDisplay.lastIdx + 1}
      </span>
      <FamilyRenderer
        instanceId={reply.id}
        dataJson={reply.data_json}
        language={reply.language}
        labels={shellLabels}
        onStep={handleStep}
      />
    </div>
  );
}
