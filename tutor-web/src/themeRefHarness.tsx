import "katex/dist/katex.min.css";
import "./index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { DoorBrutalist } from "./door/DoorBrutalist";
import { getConcept } from "./door/concept";

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
 * ?surface=dark  -> the real DoorBrutalist (canonical DARK surface; data-testid door-brutalist)
 * ?surface=light -> the brutalist LIGHT specimen on page-bg/page-fg/accent tokens
 */
const surface = new URLSearchParams(window.location.search).get("surface") === "light" ? "light" : "dark";

// A fixed concept so the rendered content never varies run-to-run.
const concept = getConcept("mergesort");

/** The LIGHT brutalist reference: the same design system on the white work surface. */
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
            <span className="text-accent" style={{ fontWeight: 700, background: "var(--color-accent)", color: "var(--color-page-fg)", padding: "2px 8px" }}>
              {concept.subject}
            </span>{" "}
            / {concept.track} / {concept.familiarity}
          </p>
          <h1 style={{ margin: 0, lineHeight: 1.0, letterSpacing: "-0.02em", textTransform: "uppercase", fontWeight: 700, fontSize: "clamp(44px,7vw,104px)" }}>
            <span style={{ display: "block" }}>{concept.titleTop}</span>
            <span style={{ display: "block", background: "var(--color-accent)", padding: "0 12px", width: "fit-content" }}>{concept.titleAccent}</span>
          </h1>
          {/* equation in a hard-bordered mono plate — the LIGHT elevation token */}
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
    {surface === "light" ? (
      <ThemeRefLight />
    ) : (
      <div data-testid="theme-ref-dark">
        <DoorBrutalist concept={concept} brandMark={<span style={{ letterSpacing: "0.3em", fontWeight: 700, fontSize: 13 }}>JARVIS</span>} />
      </div>
    )}
  </StrictMode>,
);
