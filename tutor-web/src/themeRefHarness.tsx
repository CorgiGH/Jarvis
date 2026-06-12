import "./index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

/**
 * Plan-4a Task 4 (§0.9D, spec §9.2 g6 / INV-9.5) — the two THEME REFERENCE PAGES.
 *
 * SELF-CONTAINED: ZERO imports from src/door/* (those are deliberately-untracked
 * working-tree demos per the standing door rule; importing them makes the committed
 * tree unresolvable on clean checkout — PM RULING 2026-06-12).
 *
 * DESIGN.md "Two surfaces, never a third" (DESIGN.md:106-115): the brutalist DARK
 * surface and the brutalist LIGHT surface. These are the spec's "2 theme reference
 * pages" baselined alongside the fixed shell (3 baselines total). Everything is driven
 * by the real index.css tokens — a token change reflows the baseline, which is the
 * drift signal we want. Deterministic: SVG figures only, NO emoji, self-hosted fonts
 * (Task 1), no async/RAF — so a 0-tolerance full-page screenshot is reproducible.
 *
 * ?surface=dark  -> DARK brutalist specimen (data-testid="door-brutalist" preserved
 *                   for the dark spec's anchor assertion)
 * ?surface=light -> LIGHT brutalist specimen on page-bg/page-fg/accent tokens
 */
const surface = new URLSearchParams(window.location.search).get("surface") === "light" ? "light" : "dark";

// ── Inline concept data (mergesort) — replaces getConcept("mergesort") import ──
const CONCEPT = {
  subject: "PA",
  track: "Divide & Conquer",
  familiarity: "First time",
  progress: { current: 5, total: 9 },
  titleTop: "Merge",
  titleAccent: "Sort",
  equation: "take min(headₗ, headᵣ)",
  gistLead: "Two already-sorted runs become one. Keep a finger on each head and always take the smaller — that is merging.",
  figureLabel: "two runs, one output",
  caption: "Compare the two heads, take the smaller, advance that finger. Every element is touched once — Θ(n) per level.",
  footer: ["Stable", "Θ(n log n)", "extra O(n)"],
  // Merge figure inline: left=[2,5,8] right=[1,4,9], leftConsumed=1, rightConsumed=1, output=[1,2]
  left: [2, 5, 8] as number[],
  right: [1, 4, 9] as number[],
  leftConsumed: 1,
  rightConsumed: 1,
  output: [1, 2] as number[],
};

// ── Inline SVG merge figure — deterministic, no emoji, no RAF ──
function MergeFigure() {
  const { left, right, leftConsumed, rightConsumed, output } = CONCEPT;
  const cellW = 44, cellH = 32, gutter = 8, rowGap = 28;
  const totalCols = Math.max(left.length, right.length, output.length);
  const svgW = totalCols * (cellW + gutter);
  const svgH = cellH * 3 + rowGap * 2 + 24;

  function Row({
    values,
    consumed,
    y,
    label,
    labelColor,
  }: {
    values: number[];
    consumed: number;
    y: number;
    label: string;
    labelColor: string;
  }) {
    return (
      <>
        <text x={-6} y={y + cellH / 2 + 5} textAnchor="end" fontSize={11} fill="var(--color-overlay-fg)" opacity={0.6} fontFamily="monospace">{label}</text>
        {values.map((v, i) => {
          const faded = i < consumed;
          const active = i === consumed;
          return (
            <g key={i} transform={`translate(${i * (cellW + gutter)}, ${y})`}>
              <rect
                width={cellW}
                height={cellH}
                fill={active ? "var(--color-accent)" : "none"}
                stroke={faded ? "var(--color-overlay-fg)" : "var(--color-overlay-fg)"}
                strokeWidth={active ? 2 : 1}
                opacity={faded ? 0.28 : 1}
              />
              <text
                x={cellW / 2}
                y={cellH / 2 + 5}
                textAnchor="middle"
                fontSize={13}
                fontWeight={active ? 700 : 400}
                fill={active ? "var(--color-page-fg)" : "var(--color-overlay-fg)"}
                opacity={faded ? 0.28 : 1}
                fontFamily="monospace"
              >
                {v}
              </text>
            </g>
          );
        })}
        {/* pointer arrow on consumed index */}
        {consumed < values.length && (
          <text
            x={consumed * (cellW + gutter) + cellW / 2}
            y={y - 6}
            textAnchor="middle"
            fontSize={12}
            fill={labelColor}
            fontFamily="monospace"
          >
            ▾
          </text>
        )}
      </>
    );
  }

  function OutputRow({ values, y }: { values: number[]; y: number }) {
    return (
      <>
        <text x={-6} y={y + cellH / 2 + 5} textAnchor="end" fontSize={11} fill="var(--color-overlay-fg)" opacity={0.6} fontFamily="monospace">out</text>
        {values.map((v, i) => (
          <g key={i} transform={`translate(${i * (cellW + gutter)}, ${y})`}>
            <rect width={cellW} height={cellH} fill="none" stroke="var(--color-overlay-fg)" strokeWidth={1} opacity={0.5} strokeDasharray="3 2" />
            <text x={cellW / 2} y={cellH / 2 + 5} textAnchor="middle" fontSize={13} fill="var(--color-overlay-fg)" fontFamily="monospace" opacity={0.85}>{v}</text>
          </g>
        ))}
      </>
    );
  }

  return (
    <svg
      viewBox={`-40 -18 ${svgW + 60} ${svgH + 18}`}
      width="100%"
      style={{ maxWidth: 360, display: "block" }}
      aria-label="Merge figure: two sorted runs being merged"
    >
      <Row values={left} consumed={leftConsumed} y={0} label="L" labelColor="var(--color-overlay-fg)" />
      <Row values={right} consumed={rightConsumed} y={cellH + rowGap} label="R" labelColor="var(--color-overlay-fg)" />
      <OutputRow values={output} y={(cellH + rowGap) * 2} />
    </svg>
  );
}

// ── DARK brutalist specimen ──────────────────────────────────────────────────
// Mirrors the essential layout of DoorBrutalist. Preserves data-testid="door-brutalist"
// (required by theme-dark.visual.spec.ts line 14). Zero src/door imports.
function ThemeRefDark() {
  const pips = Array.from({ length: CONCEPT.progress.total }, (_, i) => i);
  return (
    <div
      data-testid="theme-ref-dark"
      style={{ width: "100%", minHeight: "100vh" }}
    >
      <div
        data-testid="door-brutalist"
        className="min-h-screen w-full bg-panel-dark-bg font-mono text-overlay-fg"
        style={{
          display: "flex",
          flexDirection: "column",
          // Fig color contract for dark surface
          ["--fig-accent" as string]: "var(--color-accent)",
          ["--fig-accent-ink" as string]: "var(--color-page-fg)",
          ["--fig-ink" as string]: "var(--color-overlay-fg)",
          ["--fig-line" as string]: "rgba(255,255,255,0.55)",
          ["--fig-rail" as string]: "rgba(255,255,255,0.32)",
          ["--fig-node-fill" as string]: "var(--color-panel-dark-bg)",
          ["--fig-node-ink" as string]: "var(--color-overlay-fg)",
          ["--fig-muted" as string]: "rgba(255,255,255,0.6)",
        }}
      >
        <main
          style={{
            flex: "1 0 auto",
            width: "100%",
            maxWidth: 1600,
            margin: "0 auto",
            padding: "0 clamp(28px,6vw,120px)",
            display: "flex",
            flexDirection: "column",
          }}
        >
          {/* masthead */}
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
              <span style={{ letterSpacing: "0.3em", fontWeight: 700, fontSize: 13 }}>JARVIS · TUTOR</span>
            </span>
            <span
              style={{
                display: "flex",
                alignItems: "center",
                gap: 14,
                fontSize: 12,
                letterSpacing: "0.15em",
                opacity: 0.85,
              }}
            >
              <span style={{ display: "flex", gap: 5 }}>
                {pips.map((i) => (
                  <span
                    key={i}
                    style={{
                      width: 8,
                      height: 8,
                      background:
                        i === CONCEPT.progress.current - 1
                          ? "var(--color-accent)"
                          : i < CONCEPT.progress.current - 1
                            ? "var(--color-overlay-fg)"
                            : "transparent",
                      border: "1px solid var(--color-overlay-fg)",
                      opacity: i < CONCEPT.progress.current - 1 ? 0.55 : 1,
                    }}
                  />
                ))}
              </span>
              CONCEPT {CONCEPT.progress.current} / {CONCEPT.progress.total}
            </span>
          </header>

          {/* spread: lede left, figure right */}
          <div
            style={{
              flex: "1 0 auto",
              display: "grid",
              gridTemplateColumns: "minmax(0,1.05fr) minmax(0,0.95fr)",
              gap: "clamp(32px,5vw,88px)",
              alignItems: "center",
              padding: "clamp(36px,7vh,96px) 0",
            }}
          >
            {/* LEDE */}
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
                <span style={{ color: "var(--color-accent)", fontWeight: 700 }}>{CONCEPT.subject}</span>
                <span style={{ opacity: 0.4 }}>/</span>
                <span style={{ opacity: 0.85 }}>{CONCEPT.track}</span>
                <span style={{ opacity: 0.4 }}>/</span>
                <span style={{ opacity: 0.55 }}>{CONCEPT.familiarity}</span>
              </p>

              <h1
                style={{
                  margin: "0 0 clamp(26px,4vh,44px)",
                  lineHeight: 1.0,
                  letterSpacing: "-0.02em",
                  textTransform: "uppercase",
                  fontWeight: 700,
                  fontSize: "clamp(46px,7.4vw,116px)",
                }}
              >
                <span style={{ display: "block" }}>{CONCEPT.titleTop}</span>
                <span style={{ display: "block", color: "var(--color-accent)" }}>{CONCEPT.titleAccent}</span>
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
                {CONCEPT.equation}
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
                {CONCEPT.gistLead}
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
                    boxShadow: "8px 8px 0 0 var(--color-overlay-fg)",
                  }}
                >
                  Begin →
                </button>
                <span style={{ fontSize: 12.5, letterSpacing: "0.04em", opacity: 0.6 }}>
                  or press{" "}
                  <kbd className="border border-overlay-fg" style={{ padding: "3px 8px", fontSize: 11, letterSpacing: "0.08em" }}>CTRL</kbd>{" "}
                  <kbd className="border border-overlay-fg" style={{ padding: "3px 8px", fontSize: 11, letterSpacing: "0.08em" }}>ENTER</kbd>
                </span>
              </div>
            </section>

            {/* PLATE — open figure */}
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
                <span style={{ color: "var(--color-accent)" }}>FIG. 1</span> · {CONCEPT.figureLabel}
              </div>
              <MergeFigure />
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
                {CONCEPT.caption}
              </p>
            </aside>
          </div>

          {/* colophon */}
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
            {CONCEPT.footer.map((f, i) => (
              <span key={f}>
                {i > 0 && <span style={{ color: "var(--color-accent)", padding: "0 10px" }}>·</span>}
                {f}
              </span>
            ))}
          </footer>
        </main>
      </div>
    </div>
  );
}

// ── LIGHT brutalist specimen ─────────────────────────────────────────────────
function ThemeRefLight() {
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
            <span
              style={{ fontWeight: 700, background: "var(--color-accent)", color: "var(--color-page-fg)", padding: "2px 8px" }}
            >
              {CONCEPT.subject}
            </span>{" "}
            / {CONCEPT.track} / {CONCEPT.familiarity}
          </p>
          <h1 style={{ margin: 0, lineHeight: 1.0, letterSpacing: "-0.02em", textTransform: "uppercase", fontWeight: 700, fontSize: "clamp(44px,7vw,104px)" }}>
            <span style={{ display: "block" }}>{CONCEPT.titleTop}</span>
            <span style={{ display: "block", background: "var(--color-accent)", padding: "0 12px", width: "fit-content" }}>{CONCEPT.titleAccent}</span>
          </h1>
          <div className="border-2 border-border-strong" style={{ display: "inline-block", width: "fit-content", padding: "12px 18px", fontSize: "clamp(15px,1.5vw,20px)", boxShadow: "var(--shadow-hard)" }}>
            {CONCEPT.equation}
          </div>
          <p style={{ maxWidth: "48ch", fontSize: "clamp(14px,1.2vw,17px)", lineHeight: 1.6, opacity: 0.85 }}>{CONCEPT.gistLead}</p>
          <div style={{ display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
            <button
              type="button"
              data-testid="theme-ref-light-begin"
              className="bg-accent text-page-fg"
              style={{ border: "none", cursor: "pointer", fontFamily: "inherit", fontWeight: 700, letterSpacing: "0.22em", textTransform: "uppercase", fontSize: "clamp(14px,1.1vw,16px)", padding: "16px 36px", boxShadow: "var(--shadow-hard)" }}
            >
              Begin →
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

createRoot(document.getElementById("theme-ref-root")!).render(
  <StrictMode>
    {surface === "light" ? <ThemeRefLight /> : <ThemeRefDark />}
  </StrictMode>,
);
