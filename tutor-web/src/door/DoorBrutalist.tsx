import type { CSSProperties, ReactNode } from "react";
import type { DoorConcept } from "./concept";
import { Figure } from "./figures";

// CONCEPT DOOR — brutalist skin.
// DESIGN.md DARK surface: black field, mono everywhere, ONE yellow accent
// (the title's focal line + BEGIN), white hairline rules as structure, radius 0.
// M1 composition (masthead → monumental title → gist → action) fused with M8's
// OPEN diagram treatment (the figure is placed in its own column behind a
// hairline rule + caption — never a boxed card). Page-aware: the figure is
// whatever the concept declares; colors come from the shared --fig-* vars set
// on the root below, so any palette/skin flows through.

const INK = "var(--color-overlay-fg)"; // white — structure + body on dark
const ACCENT = "var(--color-accent)"; // the one focal color

// The figure's color contract for the dark brutalist surface.
const FIG_VARS: CSSProperties = {
  ["--fig-accent" as string]: "var(--color-accent)",
  ["--fig-accent-ink" as string]: "var(--color-page-fg)",
  ["--fig-ink" as string]: "var(--color-overlay-fg)",
  ["--fig-line" as string]: "rgba(255,255,255,0.55)",
  ["--fig-rail" as string]: "rgba(255,255,255,0.32)",
  ["--fig-node-fill" as string]: "var(--color-panel-dark-bg)",
  ["--fig-node-ink" as string]: "var(--color-overlay-fg)",
  ["--fig-muted" as string]: "rgba(255,255,255,0.6)",
  ["--fig-font" as string]:
    "ui-monospace, 'JetBrains Mono', Menlo, Consolas, monospace",
} as CSSProperties;

// Render the gist with the one verb highlighted in accent.
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

export function DoorBrutalist({
  concept,
  theme,
  brandMark,
}: {
  concept: DoorConcept;
  theme?: CSSProperties;
  brandMark?: ReactNode;
}): ReactNode {
  const pips = Array.from({ length: concept.progress.total }, (_, i) => i);
  return (
    <div
      data-testid="door-brutalist"
      className="min-h-screen w-full bg-panel-dark-bg font-mono text-overlay-fg"
      style={{
        display: "flex",
        flexDirection: "column",
        ...FIG_VARS,
        ...theme,
      }}
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
        {/* ── masthead: brand left, folio + progress right, hard white rule under ── */}
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
          <span
            style={{ display: "inline-flex", alignItems: "center", gap: 11 }}
          >
            {brandMark}
            <span style={{ letterSpacing: "0.3em", fontWeight: 700, fontSize: 13 }}>
              TUTOR
            </span>
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

        {/* ── the spread: lede left, open figure right ── */}
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
              <span style={{ color: ACCENT, fontWeight: 700 }}>
                {concept.subject}
              </span>
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
              <span style={{ display: "block", color: ACCENT }}>
                {concept.titleAccent}
              </span>
            </h1>

            {/* equation in a hard-bordered mono plate */}
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

            {/* primary action — the one accent fill */}
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "clamp(18px,2vw,30px)",
                flexWrap: "wrap",
              }}
            >
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
                  transition:
                    "transform var(--duration-fast,200ms) var(--ease-standard,ease), box-shadow var(--duration-fast,200ms) var(--ease-standard,ease)",
                }}
              >
                Begin →
              </button>
              <span
                style={{
                  fontSize: 12.5,
                  letterSpacing: "0.04em",
                  opacity: 0.6,
                }}
              >
                or press{" "}
                <kbd
                  className="border border-overlay-fg"
                  style={{ padding: "3px 8px", fontSize: 11, letterSpacing: "0.08em" }}
                >
                  CTRL
                </kbd>{" "}
                <kbd
                  className="border border-overlay-fg"
                  style={{ padding: "3px 8px", fontSize: 11, letterSpacing: "0.08em" }}
                >
                  ENTER
                </kbd>
              </span>
            </div>
          </section>

          {/* PLATE — open figure behind a hairline rule */}
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
            <Figure spec={concept.figure} />
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

        {/* ── colophon ── */}
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
