import type { MisconceptionPayload } from "../lib/drillGrader";
import { MathText } from "./MathText";

interface MisconceptionRibbonProps {
  /** Inline-served structured misconception (DISTINCT from the scalar grader code). */
  payload: MisconceptionPayload | null | undefined;
}

/**
 * "Your wrong intuition has a name — here is the cited refutation." Renders the
 * INLINE-served misconception_payload (NOT a separate fetch). The refutation is
 * pre-cited by the backend CitationGuard chokepoint; we only present it. When a
 * SourceRef is present we surface the citation pill (P0-2 cited serve path).
 *
 * The kicker renders the RAW `payload.id` (e.g. "OFF_BY_ONE") — misconception
 * ids are stable identifiers, not prose, so they are NOT run through formatEnum
 * (which would lowercase + de-underscore them into "off by one"). The raw code
 * is what the unit + e2e tests assert.
 */
export function MisconceptionRibbon({ payload }: MisconceptionRibbonProps) {
  if (!payload) return null;
  const { source } = payload;
  const cite = source
    ? `${source.doc}${source.page > 0 ? ` · p.${source.page}` : ""}`
    : null;

  return (
    <section
      data-testid="misconception-ribbon"
      data-content-block="misconception"
      className="mt-3 border-4 border-border-strong bg-page-bg font-mono"
    >
      <div
        data-testid="misconception-ribbon-kicker"
        className="px-3 py-1.5 border-b-4 border-border-strong bg-panel-dark-bg text-panel-dark-fg text-[10px] tracking-widest font-bold"
      >
        MISCONCEPTION · {payload.id}
      </div>
      <div className="px-4 py-3">
        <div data-testid="misconception-ribbon-refutation" className="text-xs leading-relaxed text-page-fg">
          <MathText text={payload.refutation} className="inline" />
        </div>
        {cite && (
          <span
            data-testid="misconception-ribbon-citation"
            className="mt-2 inline-block border-2 border-border-thin px-2 py-0.5 text-[10px] tracking-widest text-page-fg/80"
          >
            {cite}
          </span>
        )}
      </div>
    </section>
  );
}
