import { type ReactNode } from "react";

export type CardType = "DRILL" | "WORKED" | "DEFINITION" | "CHECK";
export type DrillCardState = "locked" | "open" | "complete";

const PED_TAG: Record<CardType, string> = {
  DRILL: "[PRACTICE]",
  WORKED: "[CONCRETE]",
  DEFINITION: "[CONCRETE]",
  CHECK: "[CHECK]",
};

interface DrillCardProps {
  cardType: CardType;
  title: string;
  state: DrillCardState;
  staggerIndex: number;
  children: ReactNode;
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

/**
 * Single drill card shell.
 *
 * - `state="locked"` — hides body, shows lock message. No animation class.
 * - `state="open"` — renders children. If reduced-motion is OFF, adds
 *   `animate-slide-in` so parent can control stagger via CSS `animation-delay`.
 * - `state="complete"` — renders children, shows ☑ checkbox, green header.
 *
 * `data-stagger-index` is set for parent-driven animation stagger
 * (see DrillStack: 80ms delay per index via inline style).
 */
export function DrillCard({
  cardType,
  title,
  state,
  staggerIndex,
  children,
}: DrillCardProps) {
  const reduced = prefersReducedMotion();
  const animClass =
    state !== "locked" && !reduced ? "animate-slide-in" : "";

  return (
    <article
      data-testid="drill-card"
      data-state={state}
      data-card-type={cardType}
      data-stagger-index={staggerIndex}
      className={`border-4 border-border-strong bg-page-bg font-mono text-xs ${animClass}`}
      style={
        state !== "locked" && !reduced
          ? { animationDelay: `${staggerIndex * 80}ms` }
          : undefined
      }
    >
      {/* Header row */}
      <div
        data-testid="card-header"
        className={`flex items-center justify-between px-4 py-2 border-b-4 border-border-strong ${
          state === "complete"
            ? "complete bg-accent text-page-fg"
            : "bg-panel-dark-bg text-panel-dark-fg"
        }`}
      >
        <div className="flex items-center gap-3 min-w-0">
          {/* Checkbox: draws ☑ on complete */}
          <span
            data-testid="card-checkbox"
            role="checkbox"
            aria-checked={state === "complete" ? "true" : "false"}
            aria-label="Card complete"
            className={`text-base transition-all duration-[180ms] ease-out select-none ${
              state === "complete" ? "text-page-fg" : "text-page-fg/40"
            }`}
          >
            {state === "complete" ? "☑" : "☐"}
          </span>
          <span className="tracking-widest font-bold truncate">{title}</span>
        </div>
        <span
          data-testid="ped-tag"
          className="text-[10px] tracking-widest text-page-fg/60 shrink-0 ml-2"
        >
          {PED_TAG[cardType]}
        </span>
      </div>

      {/* Body */}
      {state === "locked" ? (
        <div
          data-testid="card-lock-message"
          className="px-4 py-6 text-page-fg/75 tracking-widest text-center"
        >
          🔒 attempt drill first
        </div>
      ) : (
        <div className="card-body px-4 py-4 leading-relaxed">{children}</div>
      )}
    </article>
  );
}
