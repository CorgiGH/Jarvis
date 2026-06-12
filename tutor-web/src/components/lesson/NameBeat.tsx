import { useEffect } from "react";
import type { ApiBeatName } from "../../lib/lesson";
import { lessonStrings } from "../../lib/lessonStrings";
import { readMs } from "../../lib/dwell";
import { prefersReducedMotionNow } from "../../theme/applyTheme";

interface NameBeatProps {
  name: ApiBeatName;
  /** Fires once the name beat's dwell floor elapses (text-only beat: dwell is the only gate). */
  onGateClear: () => void;
}

/** NAME beat — definition + invariant + why-it-matters callout. */
export function NameBeat({ name, onGateClear }: NameBeatProps) {
  const reduced = prefersReducedMotionNow();
  useEffect(() => {
    if (reduced) { onGateClear(); return; }
    const t = setTimeout(onGateClear, readMs(name.definition + " " + name.invariant_statement + " " + name.why_matters));
    return () => clearTimeout(t);
  }, [reduced, name, onGateClear]);

  return (
    <div className="flex flex-col gap-3 font-mono">
      <div className="border-2 border-accent bg-accent text-black p-4 shadow-hard">
        <span className="font-bold uppercase tracking-widest text-[10px] block mb-1">{lessonStrings.definitionLabel}</span>
        <p className="text-sm leading-relaxed">{name.definition}</p>
      </div>
      <div className="border-2 border-border-strong p-3">
        <span className="font-bold uppercase tracking-widest text-[10px] text-accent block mb-1">{lessonStrings.invariantLabel}</span>
        <p className="text-xs text-page-fg leading-relaxed">{name.invariant_statement}</p>
      </div>
      <div className="border-l-4 border-accent pl-3">
        <span className="font-bold uppercase tracking-widest text-[10px] text-page-fg/50 block mb-1">{lessonStrings.whyLabel}</span>
        <p className="text-xs text-page-fg/80 leading-relaxed">{name.why_matters}</p>
      </div>
    </div>
  );
}
