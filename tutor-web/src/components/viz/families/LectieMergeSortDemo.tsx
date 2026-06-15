import { useState, type CSSProperties } from "react";
import { SortMergeFamily } from "./SortMergeFamily";

/**
 * Plan-V family (sort-merge) PREMIUM demo — a standalone lesson-surface that mounts the REAL
 * `sort-merge` figure (merge sort) inside a lectie-styled dark shell: masthead trust badge + progress
 * pips + a gated beat (PREDICT → PRIVEȘTE → NUMEȘTE) + an înapoi/continuă footer. The figure is the
 * SAME family component the gallery + the content YAML drive — same typed data_json, same
 * data-cell-index stamps, same correctness invariants — only the surrounding chrome changes. This
 * route exists so the animated merge figure can be EYEBALLED at full size, and to PROVE the new family
 * reads as premium as the seq-array `lectie.html` vehicle.
 *
 * Route: /tutor/lectie-mergesort (wired in main.tsx). NOT a production lesson — a quality vehicle.
 */

// The viz-pa-mergesort-runs-001 instance data_json — byte-identical to the shipped YAML + the harness.
const MERGESORT_DATA_JSON =
  '{"values":[5,2,8,1,9,3],"steps":[{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":6}],"frontOf":[0],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"divide","callout":"Vectorul întreg — încă nesortat."},{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":3},{"lo":3,"hi":6}],"frontOf":[0,3],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"divide","callout":"ÎMPARTE — taie vectorul în jumătăți, recursiv."},{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":1},{"lo":1,"hi":3},{"lo":3,"hi":4},{"lo":4,"hi":6}],"frontOf":[0,1,3,4],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"divide","callout":"ÎMPARTE — taie vectorul în jumătăți, recursiv."},{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":1},{"lo":1,"hi":2},{"lo":2,"hi":3},{"lo":3,"hi":4},{"lo":4,"hi":5},{"lo":5,"hi":6}],"frontOf":[0,1,2,3,4,5],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"divide","callout":"ÎMPARTE — am tăiat până la elemente singure."},{"array":[5,2,8,1,9,3],"runs":[{"lo":1,"hi":2},{"lo":2,"hi":3}],"frontOf":[1,2],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 2 și 8 → 2 e mai mic."},{"array":[5,2,8,1,9,3],"runs":[{"lo":2,"hi":3}],"frontOf":[2],"outFilled":1,"tookFrom":1,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 2 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[5,2,8,1,9,3],"runs":[],"frontOf":[],"outFilled":2,"tookFrom":2,"sortedSpan":{"lo":1,"hi":3},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 8."},{"array":[5,2,8,1,9,3],"runs":[{"lo":0,"hi":1},{"lo":1,"hi":3}],"frontOf":[0,1],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 5 și 2 → 2 e mai mic."},{"array":[2,5,8,1,9,3],"runs":[{"lo":1,"hi":2},{"lo":2,"hi":3}],"frontOf":[1,2],"outFilled":1,"tookFrom":0,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 2 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[2,5,8,1,9,3],"runs":[{"lo":1,"hi":2},{"lo":2,"hi":3}],"frontOf":[1,2],"outFilled":1,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 5 și 8 → 5 e mai mic."},{"array":[2,5,8,1,9,3],"runs":[{"lo":2,"hi":3}],"frontOf":[2],"outFilled":2,"tookFrom":1,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 5 în rezultat — se mută jos pe poziția 1 a listei interclasate."},{"array":[2,5,8,1,9,3],"runs":[],"frontOf":[],"outFilled":3,"tookFrom":2,"sortedSpan":{"lo":0,"hi":3},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 8."},{"array":[2,5,8,1,9,3],"runs":[{"lo":4,"hi":5},{"lo":5,"hi":6}],"frontOf":[4,5],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 9 și 3 → 3 e mai mic."},{"array":[2,5,8,1,3,9],"runs":[{"lo":5,"hi":6}],"frontOf":[5],"outFilled":1,"tookFrom":4,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 3 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[2,5,8,1,3,9],"runs":[],"frontOf":[],"outFilled":2,"tookFrom":5,"sortedSpan":{"lo":4,"hi":6},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 9."},{"array":[2,5,8,1,3,9],"runs":[{"lo":3,"hi":4},{"lo":4,"hi":6}],"frontOf":[3,4],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 1 și 3 → 1 e mai mic."},{"array":[2,5,8,1,3,9],"runs":[{"lo":4,"hi":6}],"frontOf":[4],"outFilled":1,"tookFrom":3,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 1 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[2,5,8,1,3,9],"runs":[{"lo":5,"hi":6}],"frontOf":[5],"outFilled":2,"tookFrom":4,"sortedSpan":{"lo":0,"hi":0},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 3."},{"array":[2,5,8,1,3,9],"runs":[],"frontOf":[],"outFilled":3,"tookFrom":5,"sortedSpan":{"lo":3,"hi":6},"phase":"drain","callout":"O listă s-a golit — restul se mută direct: 9."},{"array":[2,5,8,1,3,9],"runs":[{"lo":0,"hi":3},{"lo":3,"hi":6}],"frontOf":[0,3],"outFilled":0,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 2 și 1 → 1 e mai mic."},{"array":[1,2,5,8,3,9],"runs":[{"lo":1,"hi":4},{"lo":4,"hi":6}],"frontOf":[1,4],"outFilled":1,"tookFrom":0,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 1 în rezultat — se mută jos pe poziția 0 a listei interclasate."},{"array":[1,2,5,8,3,9],"runs":[{"lo":1,"hi":4},{"lo":4,"hi":6}],"frontOf":[1,4],"outFilled":1,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 2 și 3 → 2 e mai mic."},{"array":[1,2,5,8,3,9],"runs":[{"lo":2,"hi":4},{"lo":4,"hi":6}],"frontOf":[2,4],"outFilled":2,"tookFrom":1,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 2 în rezultat — se mută jos pe poziția 1 a listei interclasate."},{"array":[1,2,5,8,3,9],"runs":[{"lo":2,"hi":4},{"lo":4,"hi":6}],"frontOf":[2,4],"outFilled":2,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 5 și 3 → 3 e mai mic."},{"array":[1,2,3,5,8,9],"runs":[{"lo":3,"hi":5},{"lo":5,"hi":6}],"frontOf":[3,5],"outFilled":3,"tookFrom":2,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 3 în rezultat — se mută jos pe poziția 2 a listei interclasate."},{"array":[1,2,3,5,8,9],"runs":[{"lo":3,"hi":5},{"lo":5,"hi":6}],"frontOf":[3,5],"outFilled":3,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 5 și 9 → 5 e mai mic."},{"array":[1,2,3,5,8,9],"runs":[{"lo":4,"hi":5},{"lo":5,"hi":6}],"frontOf":[4,5],"outFilled":4,"tookFrom":3,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 5 în rezultat — se mută jos pe poziția 3 a listei interclasate."},{"array":[1,2,3,5,8,9],"runs":[{"lo":4,"hi":5},{"lo":5,"hi":6}],"frontOf":[4,5],"outFilled":4,"tookFrom":-1,"sortedSpan":{"lo":0,"hi":0},"phase":"compare","callout":"Compară fronturile: 8 și 9 → 8 e mai mic."},{"array":[1,2,3,5,8,9],"runs":[{"lo":5,"hi":6}],"frontOf":[5],"outFilled":5,"tookFrom":4,"sortedSpan":{"lo":0,"hi":0},"phase":"take","callout":"Ia 8 în rezultat — se mută jos pe poziția 4 a listei interclasate."},{"array":[1,2,3,5,8,9],"runs":[],"frontOf":[],"outFilled":6,"tookFrom":5,"sortedSpan":{"lo":0,"hi":6},"phase":"final","callout":"[1 2 3 5 8 9] — sortat ✓"}]}';

// Lectie BRUTALIST palette (matches LectieSelectSortDemo + the sort-merge family dark skin).
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

// The three teaching beats. The figure lives in beat ② (PRIVEȘTE).
type Beat = { step: string; render: "predict" | "figure" | "name" };
const BEATS: Beat[] = [
  { step: "① INTUIȚIE — întâi prezici", render: "predict" },
  { step: "② PRIVEȘTE — cum se contopesc cele două liste", render: "figure" },
  { step: "③ ACUM ARE UN NUME", render: "name" },
];

const accSpan = (t: string) => <span style={{ color: C.acc }}>{t}</span>;

export function LectieMergeSortDemo() {
  const [cur, setCur] = useState(0);
  const [predict, setPredict] = useState<number | null>(null);
  const last = BEATS.length - 1;
  const beat = BEATS[cur];
  const gateOK = beat.render !== "predict" || predict != null;

  const wrap: CSSProperties = {
    margin: 0,
    height: "100vh",
    background: C.bg,
    color: C.ink,
    fontFamily: MONO,
    overflow: "hidden",
    display: "flex",
    flexDirection: "column",
  };

  return (
    <div data-testid="lectie-mergesort" style={wrap}>
      {/* The shell's playback buttons are restyled to the lectie brutalist-yellow look (scoped to the
          sort-merge controls subtree). Same chrome treatment as the seq-array lectie demo. */}
      <style>{`
        [data-testid="sort-merge-root"] {
          border: none !important; background: transparent !important; padding: 0 !important;
          grid-template-columns: 1fr !important; gap: 12px !important; min-height: 0 !important;
          grid-template-rows: auto auto !important;
        }
        [data-testid="sort-merge-root"] .algo-stepper-shell-svg {
          border: none !important; background: ${C.bg} !important;
          max-height: min(58vh, 380px) !important; min-height: 0 !important;
          align-self: center !important;
        }
        [data-testid="sort-merge-controls"] { align-items: center !important; gap: 6px !important; }
        [data-testid="sort-merge-controls"] > div:first-child { display: flex; flex-direction: column; align-items: center; text-align: center; }
        [data-testid="sort-merge-controls"] > div:first-child > div:first-child { margin-bottom: 0 !important; color: ${C.faint} !important; }
        [data-testid="sort-merge-controls"] input[type="range"] { margin-top: 4px !important; accent-color: ${C.acc} !important; max-width: 360px; }
        [data-testid="sort-merge-controls"] [data-testid="sort-merge-frame-counter"] { color: ${C.faint} !important; text-align: center; }
        [data-testid="sort-merge-controls"] > div:nth-child(2) { justify-content: center !important; gap: 8px !important; }
        [data-testid="sort-merge-controls"] button {
          background: ${C.acc} !important; color: ${C.cellInk} !important;
          border: 2px solid ${C.acc} !important; border-radius: 3px !important; font-family: ${MONO} !important;
          font-weight: 700 !important; letter-spacing: 0.04em !important;
          text-transform: uppercase !important; padding: 8px 16px !important; cursor: pointer !important;
        }
        [data-testid="sort-merge-controls"] button:disabled {
          background: transparent !important; color: ${C.faint} !important;
          border-color: ${C.btnline} !important; opacity: 1 !important;
        }
        [data-testid="sort-merge-share"], [data-testid="sort-merge-voice"] { display: none !important; }
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
          PA · Merge Sort · corespunde cursului
        </div>
      </div>

      {/* ── PIPS ── */}
      <div style={{ display: "flex", gap: 7, justifyContent: "center", padding: 12, flex: "0 0 auto" }}>
        {BEATS.map((_, i) => (
          <div key={i} style={{ width: 34, height: 5, background: i === cur ? C.acc : i < cur ? C.acc : C.line, opacity: i < cur ? 0.5 : 1 }} />
        ))}
      </div>

      {/* ── STAGE ── */}
      <div style={{ flex: "1 1 auto", position: "relative", overflow: "hidden", display: "flex", flexDirection: "column", alignItems: "center", padding: "6px 26px 0" }}>
        <div style={{ fontSize: 11, letterSpacing: "0.2em", color: C.faint, marginBottom: 8, textAlign: "center" }}>{beat.step}</div>

        {beat.render === "predict" && <PredictBeat predict={predict} setPredict={setPredict} />}

        {beat.render === "figure" && (
          <div style={{ width: "100%", maxWidth: 760, flex: "1 1 auto", display: "flex", flexDirection: "column", justifyContent: "center", minHeight: 0 }}>
            <SortMergeFamily
              instanceId="viz-pa-mergesort-runs-001"
              dataJson={MERGESORT_DATA_JSON}
              language="ro"
              layout={{ canvasBg: C.bg, controls: "bottom", maxWidth: 760 }}
              labels={{ frame: "PAS", reset: "↻", play: "▶ redă", pause: "⏸ pauză" }}
            />
            <div style={{ fontSize: 12, color: C.faint, textAlign: "center", marginTop: 6 }}>
              ▶ pășește prin animație cu butoanele de mai jos — privește cum cele două liste se contopesc
            </div>
          </div>
        )}

        {beat.render === "name" && <NameBeat predict={predict} />}
      </div>

      {/* ── FOOTER ── */}
      <div
        style={{
          flex: "0 0 auto",
          height: 64,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "0 26px",
          borderTop: `3px solid ${C.acc}`,
        }}
      >
        <button data-testid="lectie-back" onClick={() => setCur((c) => Math.max(0, c - 1))} disabled={cur === 0} style={navBtn(cur === 0, true)}>
          ‹ înapoi
        </button>
        <span style={{ fontSize: 11, color: C.gate, letterSpacing: "0.04em" }}>{gateOK ? "" : "răspunde ca să continui"}</span>
        <button
          data-testid="lectie-next"
          onClick={() => gateOK && setCur((c) => Math.min(last, c + 1))}
          disabled={!gateOK || cur === last}
          style={navBtn(!gateOK || cur === last, false)}
        >
          {cur === last ? "gata ✓" : "continuă ›"}
        </button>
      </div>
    </div>
  );
}

function PredictBeat({ predict, setPredict }: { predict: number | null; setPredict: (v: number) => void }) {
  const arr = [5, 2, 8, 1, 9, 3];
  return (
    <>
      <h2 style={{ fontSize: "clamp(18px,2.4vw,24px)", lineHeight: 1.18, margin: "0 0 6px", maxWidth: 820, textAlign: "center", fontWeight: 700 }}>
        Vrei să sortezi {accSpan("[5, 2, 8, 1, 9, 3]")} crescător. Strategia: {accSpan("taie")} vectorul în jumătăți până la elemente singure, apoi {accSpan("contopește")} înapoi două liste deja sortate într-una singură.
      </h2>
      <div style={{ display: "flex", gap: 6, justifyContent: "center", margin: "14px 0" }}>
        {arr.map((v, i) => (
          <div
            key={i}
            style={{
              width: 46,
              height: 50,
              border: `2.5px solid ${C.acc}`,
              background: "#fff",
              color: C.cellInk,
              fontWeight: 700,
              fontSize: 19,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontFamily: MONO,
              borderRadius: 3,
            }}
          >
            {v}
          </div>
        ))}
      </div>
      <p style={{ fontSize: 15, lineHeight: 1.6, color: C.dim, maxWidth: 660, margin: "6px 0", textAlign: "center" }}>
        Când contopim două liste sortate, mereu luăm {accSpan("frontul mai mic")}. Care element ajunge {accSpan("primul")} pe poziția 0 a rezultatului final?
      </p>
      <div style={{ display: "flex", gap: 12, justifyContent: "center", margin: "16px 0", flexWrap: "wrap" }}>
        {arr.map((v) => (
          <button
            key={v}
            data-testid={`predict-${v}`}
            onClick={() => setPredict(v)}
            style={{
              border: `3px solid ${predict === v ? C.acc : C.btnline}`,
              background: predict === v ? C.acc : C.card,
              color: predict === v ? C.cellInk : C.ink,
              fontFamily: MONO,
              fontWeight: 700,
              fontSize: 17,
              padding: "12px 20px",
              cursor: "pointer",
              minWidth: 60,
              borderRadius: 3,
            }}
          >
            {v}
          </button>
        ))}
      </div>
      <div style={{ fontSize: 12, color: C.faint, marginTop: 4, minHeight: 18, textAlign: "center" }}>
        {predict == null
          ? "alege o variantă ca să continui — nu contează dacă greșești, asta e ideea"
          : `ai ales ${predict} — hai să vezi cum se contopesc listele`}
      </div>
    </>
  );
}

function NameBeat({ predict }: { predict: number | null }) {
  return (
    <>
      <h2 style={{ fontSize: "clamp(18px,2.4vw,24px)", lineHeight: 1.18, margin: "0 0 6px", maxWidth: 820, textAlign: "center", fontWeight: 700 }}>
        Asta e {accSpan("Sortarea prin Interclasare")} (merge sort): {accSpan("împarte")} recursiv până la elemente singure, apoi {accSpan("interclasează")} perechi de liste sortate, mereu luând frontul mai mic.
      </h2>
      <p style={{ fontSize: 15, lineHeight: 1.6, color: C.dim, maxWidth: 700, margin: "6px 0", textAlign: "center" }}>
        <b>Invariant:</b> după fiecare interclasare, rezultatul de jos este {accSpan("definitiv sortat")} — bara galbenă „✓ INTERCLASAT" crește mereu spre dreapta, până umple tot vectorul.
      </p>
      <p style={{ fontSize: 15, lineHeight: 1.6, color: C.dim, maxWidth: 700, margin: "6px 0", textAlign: "center" }}>
        Sunt {accSpan("log₂ n niveluri")} de tăiere (la fiecare nivel jumătățim), iar fiecare nivel atinge toate cele {accSpan("n elemente")} o dată la interclasare.
      </p>
      <p
        style={{
          fontSize: 19,
          fontWeight: 700,
          border: `3px solid ${C.acc}`,
          background: C.acc,
          color: C.cellInk,
          padding: "12px 18px",
          margin: "14px 0",
          textAlign: "center",
          borderRadius: 3,
        }}
      >
        log n niveluri × O(n) pe nivel ⇒ O(n log n)
      </p>
      <p style={{ color: C.muted, fontSize: 13, textAlign: "center" }}>
        {predict === 1
          ? "(tu ai prezis 1 — exact! 1 e cel mai mic, deci ajunge primul în rezultatul final)"
          : predict != null
            ? "(tu ai prezis " + predict + " — cel mai mic e 1, deci 1 ajunge primul pe poziția 0)"
            : ""}
      </p>
    </>
  );
}

function navBtn(disabled: boolean, ghost: boolean): CSSProperties {
  return {
    fontFamily: MONO,
    fontSize: 13,
    fontWeight: 700,
    letterSpacing: "0.04em",
    border: `3px solid ${disabled ? C.btnline : C.acc}`,
    background: disabled ? "transparent" : ghost ? "transparent" : C.acc,
    color: disabled ? C.faint : ghost ? C.acc : C.cellInk,
    padding: "9px 22px",
    cursor: disabled ? "default" : "pointer",
    opacity: disabled ? 0.55 : 1,
  };
}
