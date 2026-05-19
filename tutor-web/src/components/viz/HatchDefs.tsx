// tutor-web/src/components/viz/HatchDefs.tsx
import { INK } from "./theme";

// Shared SVG <defs> for the two brutalist hatch patterns.
// Render once inside any <svg> that uses HATCH_LIGHT / HATCH_DENSE fills.
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
    </defs>
  );
}
