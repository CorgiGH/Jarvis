import "katex/dist/katex.min.css";
import "./index.css";
import type { CSSProperties, ReactNode } from "react";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

/**
 * Plan-4a Task 4 (§0.9D, spec §9.2 g6 / INV-9.5) — the two THEME REFERENCE PAGES.
 *
 * DESIGN.md "Two surfaces, never a third" (DESIGN.md:106-115): the brutalist DARK surface
 * and the brutalist LIGHT surface. These are the spec's "2 theme reference pages" baselined
 * alongside the fixed shell (3 baselines total). They are NOT routed in main.tsx (a Plan-3
 * file Lane B must not edit), so Lane B owns this standalone Vite harness entry. Everything
 * is driven by the real index.css tokens — a token change reflows the baseline, which is the
 * drift signal we want. Deterministic on purpose: SVG figures only, NO emoji, self-hosted
 * fonts (Task 1), no async/RAF — so a 0-tolerance full-page screenshot is reproducible.
 *
 * ?surface=dark  -> the DARK brutalist reference surface (data-testid="door-brutalist")
 * ?surface=light -> the brutalist LIGHT specimen on page-bg/page-fg/accent tokens
 *
 * NOTE (review fix): DoorBrutalist, concept, and figures are inlined here rather than
 * imported from ./door/* because those files are not in Task 4's Files block. The harness
 * is self-contained; no ./door/* imports.
 */

// ── concept data (inline — mergesort only, deterministic) ─────────────────────

interface MergeSpec {
  kind: "merge";
  left: number[];
  right: number[];
  leftConsumed: number;
  rightConsumed: number;
  output: number[];
}

interface DoorConcept {
  subject: string;
  track: string;
  familiarity: string;
  progress: { current: number; total: number };
  titleTop: string;
  titleAccent: string;
  equation: string;
  gistLead: string;
  gistVerb: string;
  figureLabel: string;
  figure: MergeSpec;
  caption: string;
  footer: string[];
}

const MERGESORT: DoorConcept = {
  subject: "PA",
  track: "Divide & Conquer",
  familiarity: "First time",
  progress: { current: 5, total: 9 },
  titleTop: "Merge",
  titleAccent: "Sort",
  equation: "take min(headₗ, headᵣ)",
  gistLead:
    "Two already-sorted runs become one. Keep a finger on each head and always take the smaller — that is merging.",
  gistVerb: "merging",
  figureLabel: "two runs, one output",
  figure: {
    kind: "merge",
    left: [2, 5, 8],
    right: [1, 4, 9],
    leftConsumed: 1,
    rightConsumed: 1,
    output: [1, 2],
  },
  caption:
    "Compare the two heads, take the smaller, advance that finger. Every element is touched once — Θ(n) per level.",
  footer: ["Stable", "Θ(n log n)", "extra O(n)"],
};

// ── inline MergeFig (the only figure shape used by MERGESORT) ─────────────────

const ACCENT_VAR = "var(--fig-accent)";
const ACCENT_INK_VAR = "var(--fig-accent-ink)";
const INK_VAR = "var(--fig-ink)";
const MUTED_VAR = "var(--fig-muted)";
const NODE_FILL_VAR = "var(--fig-node-fill)";
const NODE_INK_VAR = "var(--fig-node-ink)";
const FONT_VAR = "var(--fig-font)";

const figStyle: CSSProperties = { width: "100%", height: "auto", display: "block" };

function label(x: number, y: number, text: string): ReactNode {
  return (
    <text x={x} y={y} style={{ fontFamily: FONT_VAR, fontSize: 10.5, fill: MUTED_VAR, letterSpacing: "0.08em" }}>
      {text}
    </text>
  );
}

function MergeFig({ left, right, leftConsumed, rightConsumed, output }: MergeSpec): ReactNode {
  const W = 480, H = 300;
  const cw = 46, ch = 40, gap = 6;
  const runW = (arr: number[]) => arr.length * cw + (arr.length - 1) * gap;
  const leftX0 = 40, rightX0 = W - 40 - runW(right);
  const runY = 74;
  const outW = (left.length + right.length) * cw + (left.length + right.length - 1) * gap;
  const outX0 = (W - outW) / 2, outY = 212;
  const cellX = (x0: number, i: number) => x0 + i * (cw + gap);
  const headLeftX = cellX(leftX0, leftConsumed) + cw / 2;
  const headRightX = cellX(rightX0, rightConsumed) + cw / 2;
  const nextSlotX = cellX(outX0, output.length) + cw / 2;

  const cell = (x: number, y: number, val: string, state: "head" | "done" | "idle" | "slot"): ReactNode => (
    <g>
      <rect
        x={x} y={y} width={cw} height={ch}
        fill={state === "head" ? ACCENT_VAR : state === "done" ? NODE_FILL_VAR : "none"}
        stroke={state === "slot" ? ACCENT_VAR : state === "idle" || state === "done" ? INK_VAR : ACCENT_VAR}
        strokeWidth={state === "head" || state === "slot" ? 2 : 1.4}
        strokeDasharray={state === "slot" ? "3 3" : undefined}
        opacity={state === "done" ? 0.45 : 1}
      />
      {val !== "" && (
        <text x={x + cw / 2} y={y + ch / 2} textAnchor="middle" dominantBaseline="central"
          style={{ fontFamily: FONT_VAR, fontWeight: 700, fontSize: 16, fill: state === "head" ? ACCENT_INK_VAR : state === "done" ? MUTED_VAR : INK_VAR }}>
          {val}
        </text>
      )}
    </g>
  );

  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" data-testid="door-figure" style={figStyle}>
      <line x1={headLeftX} y1={runY + ch} x2={nextSlotX} y2={outY} stroke={ACCENT_VAR} strokeWidth={1.4} strokeDasharray="3 4" />
      <line x1={headRightX} y1={runY + ch} x2={nextSlotX} y2={outY} stroke={ACCENT_VAR} strokeWidth={1.4} strokeDasharray="3 4" />
      {label(leftX0, runY - 12, "LEFT RUN")}
      {label(rightX0, runY - 12, "RIGHT RUN")}
      {label(outX0, outY - 12, "MERGED")}
      {left.map((v, i) => (
        <g key={`l${i}`}>{cell(cellX(leftX0, i), runY, String(v), i < leftConsumed ? "done" : i === leftConsumed ? "head" : "idle")}</g>
      ))}
      {right.map((v, i) => (
        <g key={`r${i}`}>{cell(cellX(rightX0, i), runY, String(v), i < rightConsumed ? "done" : i === rightConsumed ? "head" : "idle")}</g>
      ))}
      {Array.from({ length: left.length + right.length }, (_, i) => (
        <g key={`o${i}`}>{cell(cellX(outX0, i), outY, i < output.length ? String(output[i]) : "", i < output.length ? "idle" : i === output.length ? "slot" : "idle")}</g>
      ))}
    </svg>
  );
}

// ── inline DoorBrutalist (DARK surface) ────────────────────────────────────────

const INK = "var(--color-overlay-fg)";
const ACCENT = "var(--color-accent)";

const FIG_VARS: CSSProperties = {
  ["--fig-accent" as string]: "var(--color-accent)",
  ["--fig-accent-ink" as string]: "var(--color-page-fg)",
  ["--fig-ink" as string]: "var(--color-overlay-fg)",
  ["--fig-line" as string]: "rgba(255,255,255,0.55)",
  ["--fig-rail" as string]: "rgba(255,255,255,0.32)",
  ["--fig-node-fill" as string]: "var(--color-panel-dark-bg)",
  ["--fig-node-ink" as string]: "var(--color-overlay-fg)",
  ["--fig-muted" as string]: "rgba(255,255,255,0.6)",
  ["--fig-font" as string]: "ui-monospace, 'JetBrains Mono', Menlo, Consolas, monospace",
} as CSSProperties;

function Gist({ lead, verb }: { lead: string; verb: string }): ReactNode {
  const at = lead.indexOf(verb);
  if (at < 0) return <>{lead}</>;
  return (
    <>
      {lead.slice(0, at)}
      <span style={{ color: ACCENT, fontWeight: 700 }}>{verb}</span>
      {lead.slice(at + verb.length)}
    </>
  );
}

function DoorBrutalist({ concept, brandMark }: { concept: DoorConcept; brandMark?: ReactNode }): ReactNode {
  const pips = Array.from({ length: concept.progress.total }, (_, i) => i);
  return (
    <div
      data-testid="door-brutalist"
      className="min-h-screen w-full bg-panel-dark-bg font-mono text-overlay-fg"
      style={{ display: "flex", flexDirection: "column", ...FIG_VARS }}
    >
      <main
        style={{
          flex: "1 0 auto",
          width: "100%",
          maxWidth: 1600,
          margin: "0 auto",
          padding: "0 clamp(28px, 6vw, 120px)",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <header
          className="border-b-2 border-overlay-fg"
          style={{
            display: "flex",
            alignItems: "baseline",
            justifyContent: "space-between",
            gap: 24,
            padding: "clamp(22px,4vh,44px) 0 16px",
          }}
        >
          <span style={{ display: "inline-flex", alignItems: "center", gap: 11 }}>
            {brandMark}
            <span style={{ letterSpacing: "0.3em", fontWeight: 700, fontSize: 13 }}>TUTOR</span>
          </span>
          <span style={{ display: "flex", alignItems: "center", gap: 14, fontSize: 12, letterSpacing: "0.15em", opacity: 0.85 }}>
            <span style={{ display: "flex", gap: 5 }} aria-hidden>
              {pips.map((i) => (
                <span
                  key={i}
                  style={{
                    width: 8,
                    height: 8,
                    background:
                      i === concept.progress.current - 1
                        ? ACCENT
                        : i < concept.progress.current - 1
                          ? INK
                          : "transparent",
                    border: `1px solid ${INK}`,
                    opacity: i < concept.progress.current - 1 ? 0.55 : 1,
                  }}
                />
              ))}
            </span>
            CONCEPT {concept.progress.current} / {concept.progress.total}
          </span>
        </header>

        <div
          style={{
            flex: "1 0 auto",
            display: "grid",
            gridTemplateColumns: "minmax(0, 1.05fr) minmax(0, 0.95fr)",
            gap: "clamp(32px, 5vw, 88px)",
            alignItems: "center",
            padding: "clamp(36px, 7vh, 96px) 0",
          }}
          className="door-spread"
        >
          <section style={{ minWidth: 0 }}>
            <p
              style={{
                display: "flex",
                alignItems: "center",
                gap: 12,
                marginBottom: "clamp(22px,4vh,40px)",
                fontSize: 12,
                letterSpacing: "0.26em",
                textTransform: "uppercase",
              }}
            >
              <span style={{ color: ACCENT, fontWeight: 700 }}>{concept.subject}</span>
              <span style={{ opacity: 0.4 }}>/</span>
              <span style={{ opacity: 0.85 }}>{concept.track}</span>
              <span style={{ opacity: 0.4 }}>/</span>
              <span style={{ opacity: 0.55 }}>{concept.familiarity}</span>
            </p>

            <h1
              style={{
                margin: "0 0 clamp(26px,4vh,44px)",
                lineHeight: 1.0,
                letterSpacing: "-0.02em",
                textTransform: "uppercase",
                fontWeight: 700,
                fontSize: "clamp(46px, 7.4vw, 116px)",
              }}
            >
              <span style={{ display: "block" }}>{concept.titleTop}</span>
              <span style={{ display: "block", color: ACCENT }}>{concept.titleAccent}</span>
            </h1>

            <div
              className="border-2 border-overlay-fg"
              style={{
                display: "inline-block",
                padding: "12px 18px",
                marginBottom: "clamp(20px,3vh,30px)",
                fontSize: "clamp(15px,1.5vw,20px)",
                letterSpacing: "0.02em",
              }}
            >
              {concept.equation}
            </div>

            <p
              style={{
                maxWidth: "42ch",
                marginBottom: "clamp(30px,5vh,52px)",
                fontSize: "clamp(14px,1.2vw,17px)",
                lineHeight: 1.6,
                opacity: 0.8,
              }}
            >
              <Gist lead={concept.gistLead} verb={concept.gistVerb} />
            </p>

            <div style={{ display: "flex", alignItems: "center", gap: "clamp(18px,2vw,30px)", flexWrap: "wrap" }}>
              <button
                type="button"
                data-testid="door-begin"
                className="bg-accent text-page-fg"
                style={{
                  appearance: "none",
                  border: "none",
                  cursor: "pointer",
                  fontFamily: "inherit",
                  fontWeight: 700,
                  letterSpacing: "0.22em",
                  textTransform: "uppercase",
                  fontSize: "clamp(14px,1.1vw,16px)",
                  padding: "18px 40px",
                  boxShadow: `8px 8px 0 0 ${INK}`,
                  transition: "transform var(--duration-fast,200ms) var(--ease-standard,ease), box-shadow var(--duration-fast,200ms) var(--ease-standard,ease)",
                }}
              >
                Begin
              </button>
              <span style={{ fontSize: 12.5, letterSpacing: "0.04em", opacity: 0.6 }}>
                or press{" "}
                <kbd className="border border-overlay-fg" style={{ padding: "3px 8px", fontSize: 11, letterSpacing: "0.08em" }}>CTRL</kbd>{" "}
                <kbd className="border border-overlay-fg" style={{ padding: "3px 8px", fontSize: 11, letterSpacing: "0.08em" }}>ENTER</kbd>
              </span>
            </div>
          </section>

          <aside
            className="border-l border-overlay-fg"
            style={{
              minWidth: 0,
              alignSelf: "stretch",
              display: "flex",
              flexDirection: "column",
              justifyContent: "center",
              paddingLeft: "clamp(28px,3.5vw,72px)",
            }}
          >
            <div
              style={{
                fontSize: 11,
                letterSpacing: "0.24em",
                textTransform: "uppercase",
                opacity: 0.6,
                marginBottom: "clamp(22px,4vh,44px)",
              }}
            >
              <span style={{ color: ACCENT }}>FIG. 1</span> · {concept.figureLabel}
            </div>
            <MergeFig {...concept.figure} />
            <p
              className="border-t border-overlay-fg"
              style={{
                marginTop: "clamp(24px,4vh,44px)",
                paddingTop: "clamp(16px,2vh,22px)",
                maxWidth: "40ch",
                fontSize: 13,
                lineHeight: 1.55,
                opacity: 0.6,
              }}
            >
              {concept.caption}
            </p>
          </aside>
        </div>

        <footer
          className="border-t border-overlay-fg"
          style={{
            display: "flex",
            justifyContent: "flex-end",
            gap: 0,
            padding: "16px 0 clamp(24px,4vh,42px)",
            fontSize: 11,
            letterSpacing: "0.2em",
            textTransform: "uppercase",
            opacity: 0.65,
          }}
        >
          {concept.footer.map((f, i) => (
            <span key={f}>
              {i > 0 && <span style={{ color: ACCENT, padding: "0 10px" }}>·</span>}
              {f}
            </span>
          ))}
        </footer>
      </main>

      <style>{`
        @media (max-width: 900px) {
          .door-spread {
            grid-template-columns: 1fr !important;
            gap: clamp(32px,6vh,56px) !important;
          }
          .door-spread > aside {
            border-left: none !important;
            border-top: 1px solid var(--color-overlay-fg);
            padding-left: 0 !important;
            padding-top: clamp(28px,5vh,44px);
          }
        }
      `}</style>
    </div>
  );
}

// ── LIGHT brutalist reference ──────────────────────────────────────────────────

const concept = MERGESORT;

function ThemeRefLight(): ReactNode {
  return (
    <div
      data-testid="theme-ref-light"
      className="min-h-screen w-full bg-page-bg font-mono text-page-fg"
      style={{ display: "flex", flexDirection: "column" }}
    >
      <main style={{ flex: "1 0 auto", width: "100%", maxWidth: 1280, margin: "0 auto", padding: "0 clamp(28px,6vw,96px)" }}>
        <header
          className="border-b-2 border-border-strong"
          style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 24, padding: "clamp(22px,4vh,44px) 0 16px" }}
        >
          <span style={{ letterSpacing: "0.3em", fontWeight: 700, fontSize: 13 }}>JARVIS · TUTOR</span>
          <span style={{ fontSize: 12, letterSpacing: "0.15em", opacity: 0.7 }}>LIGHT SURFACE — REFERENCE</span>
        </header>

        <section style={{ padding: "clamp(36px,7vh,88px) 0", display: "grid", gap: "clamp(28px,5vh,52px)" }}>
          <p style={{ fontSize: 12, letterSpacing: "0.26em", textTransform: "uppercase", opacity: 0.7 }}>
            <span className="text-accent" style={{ fontWeight: 700, background: "var(--color-accent)", color: "var(--color-page-fg)", padding: "2px 8px" }}>
              {concept.subject}
            </span>{" "}
            / {concept.track} / {concept.familiarity}
          </p>
          <h1 style={{ margin: 0, lineHeight: 1.0, letterSpacing: "-0.02em", textTransform: "uppercase", fontWeight: 700, fontSize: "clamp(44px,7vw,104px)" }}>
            <span style={{ display: "block" }}>{concept.titleTop}</span>
            <span style={{ display: "block", background: "var(--color-accent)", padding: "0 12px", width: "fit-content" }}>{concept.titleAccent}</span>
          </h1>
          <div className="border-2 border-border-strong" style={{ display: "inline-block", width: "fit-content", padding: "12px 18px", fontSize: "clamp(15px,1.5vw,20px)", boxShadow: "var(--shadow-hard)" }}>
            {concept.equation}
          </div>
          <p style={{ maxWidth: "48ch", fontSize: "clamp(14px,1.2vw,17px)", lineHeight: 1.6, opacity: 0.85 }}>{concept.gistLead}</p>
          <div style={{ display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
            <button
              type="button"
              data-testid="theme-ref-light-begin"
              className="bg-accent text-page-fg"
              style={{ border: "none", cursor: "pointer", fontFamily: "inherit", fontWeight: 700, letterSpacing: "0.22em", textTransform: "uppercase", fontSize: "clamp(14px,1.1vw,16px)", padding: "16px 36px", boxShadow: "var(--shadow-hard)" }}
            >
              Begin
            </button>
            <span style={{ fontSize: 12.5, letterSpacing: "0.04em", opacity: 0.6 }}>
              uppercase <span className="border border-border-strong" style={{ padding: "3px 8px", fontSize: 11 }}>tracking-widest</span> labels
            </span>
          </div>
        </section>
      </main>
    </div>
  );
}

// ── entry ──────────────────────────────────────────────────────────────────────

const surface = new URLSearchParams(window.location.search).get("surface") === "light" ? "light" : "dark";

createRoot(document.getElementById("theme-ref-root")!).render(
  <StrictMode>
    {surface === "light" ? (
      <ThemeRefLight />
    ) : (
      <div data-testid="theme-ref-dark">
        <DoorBrutalist concept={concept} brandMark={<span style={{ letterSpacing: "0.3em", fontWeight: 700, fontSize: 13 }}>JARVIS</span>} />
      </div>
    )}
  </StrictMode>,
);
