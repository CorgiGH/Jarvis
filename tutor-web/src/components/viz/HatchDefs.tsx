// tutor-web/src/components/viz/HatchDefs.tsx
import { INK } from "./theme";

// Shared SVG <defs> for the brutalist hatch patterns.
// Render once inside any <svg> that uses HATCH_LIGHT / HATCH_DENSE / HATCH_CROSS fills.
//
// COORDINATE-SPACE NOTE: patternUnits="userSpaceOnUse" means the 6px / 3px hatch
// pitch is measured in the HOST <svg>'s user-space, tuned for the AlgoStepperShell
// viewBox (0 0 480 360). A host <svg> with a very different viewBox scale will see
// a proportionally different visual hatch density — keep a comparable viewBox or
// scale the host accordingly.
export function HatchDefs() {
  return (
    <defs>
      <pattern
        id="hatch-light"
        patternUnits="userSpaceOnUse"
        width={6}
        height={6}
        patternTransform="rotate(45)"
      >
        <line x1={0} y1={0} x2={0} y2={6} stroke={INK} strokeWidth={1} />
      </pattern>
      <pattern
        id="hatch-dense"
        patternUnits="userSpaceOnUse"
        width={3}
        height={3}
        patternTransform="rotate(45)"
      >
        <line x1={0} y1={0} x2={0} y2={3} stroke={INK} strokeWidth={1} />
      </pattern>
      {/* Cross-hatch: two perpendicular sets of lines — 4th category slot. */}
      <pattern
        id="hatch-cross"
        patternUnits="userSpaceOnUse"
        width={6}
        height={6}
      >
        <line x1={0} y1={0} x2={0} y2={6} stroke={INK} strokeWidth={1} />
        <line x1={0} y1={0} x2={6} y2={0} stroke={INK} strokeWidth={1} />
      </pattern>
    </defs>
  );
}
