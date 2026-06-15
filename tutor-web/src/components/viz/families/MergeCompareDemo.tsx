import type { CSSProperties, ReactNode } from "react";
import { SortMergeFamily } from "./SortMergeFamily";

/**
 * PROTOTYPE compare page — the merge-sort figure rendered TWO ways, SIDE BY SIDE, on one dark page at
 * the SAME representative merge frame, so the developer can eyeball the current flat-BOX encoding vs the
 * new height∝value BAR encoding at his real viewport. Throwaway quality vehicle (NOT a production
 * lesson). Reuses the lectie dark-shell chrome + the EXACT MERGESORT_DATA_JSON from LectieMergeSortDemo.
 *
 * Route: /tutor/merge-compare (wired in main.tsx).
 *
 * Representative frame = step index 23 (0-based): "Compară fronturile: 5 și 3 → 3 e mai mic." — a
 * `compare` phase in the final 6-element merge with runs.length===2 (two FULL sorted runs converging)
 * AND outFilled===2 (the output already building), plus a live front comparison. Both columns open on
 * this frame via the shell's initialStep so the two encodings are compared at the identical moment.
 */

const FRAME_INDEX = 23;

// The viz-pa-mergesort-runs-001 instance data_json — byte-identical to LectieMergeSortDemo + the YAML + harness.
const MERGESORT_DATA_JSON =
  '{"values":[5,2,8,1,9,3],"steps":[{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":6}],"frontOf":[0],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"divide","callout":"Vectorul întreg — încă nesortat."},{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":3},{"lo":3,"hi":6}],"frontOf":[0,3],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"divide","callout":"ÎMPARTE — taie vectorul în jumătăți, recursiv."},{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":1},{"lo":1,"hi":3},{"lo":3,"hi":4},{"lo":4,"hi":6}],"frontOf":[0,1,3,4],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"divide","callout":"ÎMPARTE — taie vectorul în jumătăți, recursiv."},{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":1},{"lo":1,"hi":2},{"lo":2,"hi":3},{"lo":3,"hi":4},{"lo":4,"hi":5},{"lo":5,"hi":6}],"frontOf":[0,1,2,3,4,5],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"divide","callout":"ÎMPARTE — am tăiat până la elemente singure."},{"array":[5,2,8,1,9,3],"runs":[{"lo":1,"hi":2},{"lo":2,"hi":3}],"frontOf":[1,2],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 2 și 8 → 2 e mai mic."},{"array":[5,2,8,1,9,3],"runs":[{"lo":2,"hi":3}],"frontOf":[2],"outFilled":1,"tookFrom":1,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 2 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[5,2,8,1,9,3],"runs":[],"frontOf":[],"outFilled":2,"tookFrom":2,"sortedSpan":{"lo":1,"hi":3},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 8."},{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":1},{"lo":1,"hi":3}],"frontOf":[0,1],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 5 și 2 → 2 e mai mic."},{"array":[2,5,8,1,9,3],"runs":[{"lo":1,"hi":2},{"lo":2,"hi":3}],"frontOf":[1,2],"outFilled":1,"tookFrom":0,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 2 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[2,5,8,1,9,3],"runs":[{"lo":1,"hi":2},{"lo":2,"hi":3}],"frontOf":[1,2],"outFilled":1,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 5 și 8 → 5 e mai mic."},{"array":[2,5,8,1,9,3],"runs":[{"lo":2,"hi":3}],"frontOf":[2],"outFilled":2,"tookFrom":1,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 5 în rezultat — se mută jos pe poziția 1 a listei interclasate."},{"array":[2,5,8,1,9,3],"runs":[],"frontOf":[],"outFilled":3,"tookFrom":2,"sortedSpan":{"lo":0,"hi":3},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 8."},{"array":[2,5,8,1,9,3],"runs":[{"lo":4,"hi":5},{"lo":5,"hi":6}],"frontOf":[4,5],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 9 și 3 → 3 e mai mic."},{"array":[2,5,8,1,3,9],"runs":[{"lo":5,"hi":6}],"frontOf":[5],"outFilled":1,"tookFrom":4,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 3 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[2,5,8,1,3,9],"runs":[],"frontOf":[],"outFilled":2,"tookFrom":5,"sortedSpan":{"lo":4,"hi":6},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 9."},{"array":[2,5,8,1,3,9],"runs":[{"lo":3,"hi":4},{"lo":4,"hi":6}],"frontOf":[3,4],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 1 și 3 → 1 e mai mic."},{"array":[2,5,8,1,3,9],"runs":[{"lo":4,"hi":6}],"frontOf":[4],"outFilled":1,"tookFrom":3,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 1 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[2,5,8,1,3,9],"runs":[{"lo":5,"hi":6}],"frontOf":[5],"outFilled":2,"tookFrom":4,"sortedSpan":{"lo":0,"hi":0},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 3."},{"array":[2,5,8,1,3,9],"runs":[],"frontOf":[],"outFilled":3,"tookFrom":5,"sortedSpan":{"lo":3,"hi":6},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 9."},{"array":[2,5,8,1,3,9],"runs":[{"lo":0,"hi":3},{"lo":3,"hi":6}],"frontOf":[0,3],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 2 și 1 → 1 e mai mic."},{"array":[1,2,5,8,3,9],"runs":[{"lo":1,"hi":4},{"lo":4,"hi":6}],"frontOf":[1,4],"outFilled":1,"tookFrom":0,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 1 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[1,2,5,8,3,9],"runs":[{"lo":1,"hi":4},{"lo":4,"hi":6}],"frontOf":[1,4],"outFilled":1,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 2 și 3 → 2 e mai mic."},{"array":[1,2,5,8,3,9],"runs":[{"lo":2,"hi":4},{"lo":4,"hi":6}],"frontOf":[2,4],"outFilled":2,"tookFrom":1,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 2 în rezultat — se mută jos pe poziția 1 a listei interclasate."},{"array":[1,2,5,8,3,9],"runs":[{"lo":2,"hi":4},{"lo":4,"hi":6}],"frontOf":[2,4],"outFilled":2,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 5 și 3 → 3 e mai mic."},{"array":[1,2,3,5,8,9],"runs":[{"lo":3,"hi":5},{"lo":5,"hi":6}],"frontOf":[3,5],"outFilled":3,"tookFrom":2,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 3 în rezultat — se mută jos pe poziția 2 a listei interclasate."},{"array":[1,2,3,5,8,9],"runs":[{"lo":3,"hi":5},{"lo":5,"hi":6}],"frontOf":[3,5],"outFilled":3,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 5 și 9 → 5 e mai mic."},{"array":[1,2,3,5,8,9],"runs":[{"lo":4,"hi":5},{"lo":5,"hi":6}],"frontOf":[4,5],"outFilled":4,"tookFrom":3,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 5 în rezultat — se mută jos pe poziția 3 a listei interclasate."},{"array":[1,2,3,5,8,9],"runs":[{"lo":4,"hi":5},{"lo":5,"hi":6}],"frontOf":[4,5],"outFilled":4,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 8 și 9 → 8 e mai mic."},{"array":[1,2,3,5,8,9],"runs":[{"lo":5,"hi":6}],"frontOf":[5],"outFilled":5,"tookFrom":4,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 8 în rezultat — se mută jos pe poziția 4 a listei interclasate."},{"array":[1,2,3,5,8,9],"runs":[],"frontOf":[],"outFilled":6,"tookFrom":5,"sortedSpan":{"lo":0,"hi":6},"phase":"final","callout":"[1 2 3 5 8 9] — sortat ✓"}]}';

// Lectie BRUTALIST palette (matches LectieMergeSortDemo + the sort-merge family dark skin).
const C = {
  bg: "#0e0e0e",
  ink: "#fff",
  muted: "#9a9a9a",
  dim: "#cfcfcf",
  faint: "#888",
  acc: "#fde047",
  card: "#161616",
  line: "#333",
  btnline: "#555",
  cellInk: "#000",
  gate: "#c9a227",
} as const;

const MONO = '"JetBrains Mono", ui-monospace, Consolas, monospace';

export function MergeCompareDemo() {
  const wrap: CSSProperties = {
    margin: 0,
    minHeight: "100vh",
    background: C.bg,
    color: C.ink,
    fontFamily: MONO,
    display: "flex",
    flexDirection: "column",
  };

  return (
    <div data-testid="merge-compare" style={wrap}>
      {/* Restyle the shell's playback chrome to the lectie brutalist-yellow look (scoped to BOTH
          sort-merge subtrees). Same treatment as LectieMergeSortDemo, with a tighter canvas cap so
          two columns fit side by side at the developer's 1536-wide viewport. */}
      <style>{`
        [data-testid="sort-merge-root"] {
          border: none !important; background: transparent !important; padding: 0 !important;
          grid-template-columns: 1fr !important; gap: 10px !important; min-height: 0 !important;
          grid-template-rows: auto auto !important;
        }
        [data-testid="sort-merge-root"] .algo-stepper-shell-svg {
          border: none !important; background: ${C.bg} !important;
          max-height: min(52vh, 430px) !important; min-height: 0 !important;
          align-self: center !important;
        }
        [data-testid="sort-merge-controls"] { align-items: center !important; gap: 6px !important; }
        [data-testid="sort-merge-controls"] > div:first-child { display: flex; flex-direction: column; align-items: center; text-align: center; }
        [data-testid="sort-merge-controls"] > div:first-child > div:first-child { margin-bottom: 0 !important; color: ${C.faint} !important; }
        [data-testid="sort-merge-controls"] input[type="range"] { margin-top: 4px !important; accent-color: ${C.acc} !important; max-width: 320px; }
        [data-testid="sort-merge-controls"] [data-testid="sort-merge-frame-counter"] { color: ${C.faint} !important; text-align: center; }
        [data-testid="sort-merge-controls"] > div:nth-child(2) { justify-content: center !important; gap: 8px !important; }
        [data-testid="sort-merge-controls"] button {
          background: ${C.acc} !important; color: ${C.cellInk} !important;
          border: 2px solid ${C.acc} !important; border-radius: 3px !important; font-family: ${MONO} !important;
          font-weight: 700 !important; letter-spacing: 0.04em !important;
          text-transform: uppercase !important; padding: 7px 13px !important; cursor: pointer !important;
        }
        [data-testid="sort-merge-controls"] button:disabled {
          background: transparent !important; color: ${C.faint} !important;
          border-color: ${C.btnline} !important; opacity: 1 !important;
        }
        /* The PLAY/PAUSE (autoplay) button STAYS visible in both columns — it is the one-touch
           "watch the two lists contopi themselves" control (share + voice stay hidden — prototype).
           It inherits the yellow brutalist button look above; we widen it a touch and put it FIRST so
           the eye lands on "▶ redă" before the step arrows. The label flips to "⏸ pauză" while playing. */
        [data-testid="sort-merge-share"], [data-testid="sort-merge-voice"] { display: none !important; }
        [data-testid="sort-merge-play"] { order: -1 !important; padding: 7px 16px !important; }
        [data-testid="sort-merge-controls"] [data-testid="sort-merge-live"] { color: ${C.bg} !important; }
      `}</style>

      {/* ── MASTHEAD ── */}
      <div
        style={{
          height: 46,
          flex: "0 0 auto",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "0 22px",
          borderBottom: `3px solid ${C.acc}`,
          color: C.acc,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 9, fontWeight: 700, letterSpacing: "0.12em", fontSize: 13 }}>
          <span style={{ width: 16, height: 16, borderRadius: "50%", background: `linear-gradient(135deg, ${C.acc}, ${C.acc})` }} />
          TUTOR
        </div>
        <div data-testid="trust-badge" style={{ fontSize: 10, letterSpacing: "0.14em", color: C.muted }}>
          PROTOTIP · Merge Sort · cutii vs bare (același cadru)
        </div>
      </div>

      {/* ── HEADLINE ── */}
      <div style={{ textAlign: "center", padding: "14px 26px 4px" }}>
        <div style={{ fontSize: 13, letterSpacing: "0.16em", color: C.acc, fontWeight: 700 }}>
          ACELAȘI MOMENT, DOUĂ CODIFICĂRI
        </div>
        <div style={{ fontSize: 12, color: C.faint, marginTop: 6, maxWidth: 760, marginInline: "auto", lineHeight: 1.5 }}>
          cadru {FRAME_INDEX + 1} — „Compară fronturile: 5 și 3 → 3 e mai mic.&rdquo; · stânga = înălțime fixă (cutii),
          dreapta = înălțime ∝ valoare (bare)
        </div>
      </div>

      {/* ── TWO COLUMNS ── */}
      <div
        style={{
          flex: "1 1 auto",
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: 18,
          padding: "12px 26px 26px",
          alignItems: "start",
          minHeight: 0,
        }}
      >
        <Column label="CUTII / BOXES" sub="codificarea actuală — toate celulele la fel de înalte">
          <SortMergeFamily
            instanceId="viz-pa-mergesort-runs-001"
            dataJson={MERGESORT_DATA_JSON}
            language="ro"
            variant="boxes"
            initialStep={FRAME_INDEX}
            layout={{ canvasBg: C.bg, controls: "bottom", maxWidth: 620 }}
            labels={{ frame: "PAS", reset: "↻", play: "▶ redă", pause: "⏸ pauză" }}
          />
        </Column>

        <Column label="BARE / BARS" sub="codificare nouă — înălțimea barei = valoarea (cel mai mic = cea mai scurtă)">
          <SortMergeFamily
            instanceId="viz-pa-mergesort-runs-001"
            dataJson={MERGESORT_DATA_JSON}
            language="ro"
            variant="bars"
            initialStep={FRAME_INDEX}
            layout={{ canvasBg: C.bg, controls: "bottom", maxWidth: 620 }}
            labels={{ frame: "PAS", reset: "↻", play: "▶ redă", pause: "⏸ pauză" }}
          />
        </Column>
      </div>
    </div>
  );
}

function Column({ label, sub, children }: { label: string; sub: string; children: ReactNode }) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        border: `2px solid ${C.line}`,
        borderRadius: 4,
        background: "#0a0a0a",
        padding: "12px 14px 14px",
        minHeight: 0,
      }}
    >
      <div style={{ fontSize: 13, fontWeight: 700, letterSpacing: "0.12em", color: C.acc, textAlign: "center" }}>{label}</div>
      <div style={{ fontSize: 10.5, color: C.faint, textAlign: "center", margin: "5px 0 12px", lineHeight: 1.45, minHeight: 30 }}>{sub}</div>
      <div style={{ width: "100%", display: "flex", flexDirection: "column", justifyContent: "center", minHeight: 0 }}>{children}</div>
    </div>
  );
}
