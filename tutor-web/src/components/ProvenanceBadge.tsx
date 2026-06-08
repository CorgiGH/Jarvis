import type { DrillProvenance } from "./DrillStack";

interface ProvenanceBadgeProps {
  provenance: DrillProvenance | null | undefined;
}

/**
 * The DISTINCT honest badge for AI-generated practice. Renders ONLY for
 * provenance.type === "generated". Its color + copy are deliberately segregated
 * from the faithful TrustBadge so the "matches your lecture" claim can NEVER
 * visually span generated content (council 1780928193 trust-leak fix). The
 * `data-provenance-type="generated"` + `data-faithful="false"` attributes back
 * the rendering-boundary invariant test (Task 12) and the e2e gate (Task 15).
 */
export function ProvenanceBadge({ provenance }: ProvenanceBadgeProps) {
  if (!provenance || provenance.type !== "generated") return null;
  return (
    <span
      data-testid="provenance-badge"
      data-provenance-type="generated"
      data-faithful="false"
      className="inline-block border-2 border-danger-text bg-danger-text/10 px-2 py-0.5 font-mono text-[10px] tracking-widest text-danger-text"
    >
      AI practice — not checked against your lecture
    </span>
  );
}
