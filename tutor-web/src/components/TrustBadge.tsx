import type { VerificationStatus } from "../lib/drillGrader";

interface TrustBadgeProps {
  /** Served per-KC verification status (gradeResult.verification_status). */
  status: VerificationStatus | null | undefined;
}

/**
 * Per-KC trust-boundary sentinel. The copy is PINNED — only ever "corespunde
 * cursului" (faithful; = "matches your lecture") or "neverificat"
 * (= "unverified"). NEVER claims correctness ("verified correct" / "corect").
 * Romanian because it sits on the learner surface (language-split rule). Falls
 * closed to neverificat for any non-faithful status. `data-faithful` drives the
 * rendering-boundary test that proves this badge never co-renders with the
 * generated provenance badge.
 */
export function TrustBadge({ status }: TrustBadgeProps) {
  if (!status) return null;
  const faithful = status === "faithful";
  return (
    <span
      data-testid="trust-badge"
      data-faithful={faithful ? "true" : "false"}
      data-verification-status={status}
      className={`inline-block border-2 px-2 py-0.5 font-mono text-[10px] tracking-widest ${
        faithful
          ? "border-accent bg-accent text-page-fg"
          : "border-border-thin bg-page-bg text-page-fg/70"
      }`}
    >
      {faithful ? "corespunde cursului" : "neverificat"}
    </span>
  );
}
