import type { ReactNode } from "react";
import type { ShellLabels, ShellLayout } from "../viz/AlgoStepperShell";
import { GraphTreeFamily } from "../viz/families/GraphTreeFamily";
import { ChartDistributionFamily } from "../viz/families/ChartDistributionFamily";
import { SequenceArrayFamily } from "../viz/families/SequenceArrayFamily";
import { MatrixGridFamily } from "../viz/families/MatrixGridFamily";
import { ClassDiagramFamily } from "../viz/families/ClassDiagramFamily";

/**
 * LessonFigureShell — the dark, FLOATING figure surface for the lesson reveal beat.
 *
 * Generalizes the proven LectieSelectSortDemo treatment (which is hardcoded to the seq-array prefix)
 * to ANY of the four families used in lessons. It does two things:
 *
 *  1. Renders the family with `variant="dark"` + a dark shell `layout` (canvasBg = the page bg,
 *     bottom controls). The family's dark renderFrame bakes LITERAL hex colours into the SVG
 *     presentation attributes — the only mechanism that reliably renders (var() in an SVG fill/stroke
 *     attribute is not honored across engines), and the same path the seq-array dark skin already
 *     proves on /tutor/lectie-selectsort.
 *
 *  2. Emits ONE prefix-agnostic <style> block, scoped to this wrapper's own class, that:
 *       - strips the shell's white box (border / background / padding) so the figure FLOATS on the
 *         dark stage — no white island;
 *       - restyles the playback bar (◀ ▶ reset/share/voice/play + scrubber) to the lectie
 *         brutalist-yellow look (solid-yellow enabled, ghost-bordered disabled, yellow scrubber);
 *       - centers + tightens the controls column under the figure.
 *     The selectors target the shell's STABLE class names (.algo-stepper-shell-wrapper /
 *     .algo-stepper-shell-svg) + the [data-testid$="-controls"] suffix, so they work for every
 *     family's testIdPrefix (graph-tree / chart-dist / seq-array / mg) without naming any one prefix.
 *
 * The SVG geometry/layout is UNCHANGED — only colours + the surrounding chrome float. The light skin
 * (gallery + e2e + the trace-match harness) is the default and is untouched.
 */

const DARK_BG = "#0e0e0e";
const ACCENT = "#fde047";
const ACCENT_INK = "#000000";
const GHOST_LINE = "#555555";
const FAINT = "#888888";
const MONO = '"JetBrains Mono", ui-monospace, Consolas, monospace';

/** The dark shell layout shared by all families on the lesson surface. seq-array adds its own short
 *  viewBoxH internally when variant="dark"; the other three pack into their default viewBox. */
const DARK_LAYOUT: ShellLayout = { canvasBg: DARK_BG, controls: "bottom", maxWidth: 760 };

interface LessonFigureShellProps {
  familyId: string;
  instanceId: string;
  dataJson: string;
  language: string;
  labels?: ShellLabels;
  onStep?: (idx: number, lastIdx: number) => void;
}

/** Dispatch the four lesson families, each with variant="dark" + the dark float layout. Returns null
 *  for an unknown family (the caller already validated against familyRegistry before mounting, so this
 *  is defensive — it never silently renders the wrong family). */
function renderDarkFamily(props: LessonFigureShellProps): ReactNode {
  const { familyId, instanceId, dataJson, language, labels, onStep } = props;
  const common = { instanceId, dataJson, language, labels, onStep, variant: "dark" as const, layout: DARK_LAYOUT };
  switch (familyId) {
    case "graph-tree":
      return <GraphTreeFamily {...common} />;
    case "chart-dist":
      return <ChartDistributionFamily {...common} />;
    case "seq-array":
      return <SequenceArrayFamily {...common} />;
    case "matrix-grid":
      return <MatrixGridFamily {...common} />;
    case "class-diagram":
      // Family 7 (AMENDMENT 2026-06-17) — static-structure UML. Single canonical frame; the dark skin
      // bakes literal hex into the SVG like the others. Structure gated by classDiagramStructure.ts.
      return <ClassDiagramFamily {...common} />;
    default:
      return null;
  }
}

export function LessonFigureShell(props: LessonFigureShellProps): ReactNode {
  return (
    <div className="lesson-figure-dark" style={{ width: "100%" }}>
      <style>{`
        /* Drop the shell's white/black box chrome — the figure FLOATS on the dark stage (lectie look). */
        .lesson-figure-dark .algo-stepper-shell-wrapper {
          border: none !important;
          background: transparent !important;
          padding: 0 !important;
          gap: 10px !important;
        }
        /* The dark SVG canvas paints the page bg, no border, capped height so the whole figure +
           playback bar fits the lesson band even at the short (1536×648) target. The SVG keeps
           width:100% so it scales down on narrow columns. */
        .lesson-figure-dark .algo-stepper-shell-svg {
          border: none !important;
          background: ${DARK_BG} !important;
          max-height: min(42vh, 320px) !important;
          min-height: 0 !important;
        }
        /* Playback bar = a clean centered lectie bar. */
        .lesson-figure-dark [data-testid$="-controls"] {
          align-items: center !important;
          gap: 8px !important;
        }
        .lesson-figure-dark [data-testid$="-controls"] > div:first-child {
          text-align: center !important;
        }
        .lesson-figure-dark [data-testid$="-frame-counter"] {
          color: ${FAINT} !important;
          text-align: center !important;
        }
        .lesson-figure-dark [data-testid$="-controls"] > div:first-child > div:first-child {
          color: ${FAINT} !important;
          text-align: center !important;
        }
        .lesson-figure-dark [data-testid$="-controls"] input[type="range"] {
          accent-color: ${ACCENT} !important;
          max-width: 360px !important;
          margin-top: 6px !important;
        }
        /* center the button row */
        .lesson-figure-dark [data-testid$="-controls"] > div:nth-child(2) {
          justify-content: center !important;
          gap: 8px !important;
        }
        /* the playback buttons — solid yellow, brutalist, uppercase (the lectie figure bar). */
        .lesson-figure-dark [data-testid$="-controls"] button {
          background: ${ACCENT} !important;
          color: ${ACCENT_INK} !important;
          border: 2px solid ${ACCENT} !important;
          border-radius: 3px !important;
          font-family: ${MONO} !important;
          font-weight: 700 !important;
          letter-spacing: 0.04em !important;
          text-transform: uppercase !important;
          padding: 8px 14px !important;
          cursor: pointer !important;
        }
        .lesson-figure-dark [data-testid$="-controls"] button:disabled {
          background: transparent !important;
          color: ${FAINT} !important;
          border-color: ${GHOST_LINE} !important;
          opacity: 1 !important;
          cursor: default !important;
        }
        /* Hide the share + voice controls — not part of the lectie figure bar (kept in DOM for a11y
           parity is unnecessary here; the scrubber + step buttons + reset + play carry the figure). */
        .lesson-figure-dark [data-testid$="-share"],
        .lesson-figure-dark [data-testid$="-voice"] {
          display: none !important;
        }
        /* the aria-live status node is positioned off-screen by the shell; keep its (hidden) text dark. */
        .lesson-figure-dark [data-testid$="-live"] {
          color: ${DARK_BG} !important;
        }
      `}</style>
      {renderDarkFamily(props)}
    </div>
  );
}
