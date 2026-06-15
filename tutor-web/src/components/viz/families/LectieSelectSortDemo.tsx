import { useState, type CSSProperties } from "react";
import { SequenceArrayFamily } from "./SequenceArrayFamily";

/**
 * Plan-V family-2 BEAUTY demo — a standalone lesson-surface that mounts the REAL seq-array family
 * figure (selection sort, `variant="dark"`) inside a lectie-styled dark shell: masthead trust badge +
 * progress pips + a gated beat (PREDICT → PRIVEȘTE → NUMEȘTE) + an înapoi/continuă footer. The figure
 * is the SAME family component used on the demo gallery — same typed data_json, same data-cell-index
 * stamps, same correctness invariants — only the render skin + the surrounding chrome change. This
 * route exists to PROVE a family-system figure can read as premium as the hand-coded `lectie.html`.
 *
 * Route: /tutor/lectie-selectsort (wired in main.tsx). NOT a production lesson — a quality vehicle.
 */

// The viz-pa-selectsort-001 instance data_json — byte-identical to the gallery mount + the shipped YAML.
const SELECTSORT_DATA_JSON =
  '{"values":[5,3,8,1,6],"steps":[{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":0,"min":0,"phase":"scan","callout":"Runda i=0: presupunem că minimul este a[0]=5."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":1,"min":1,"phase":"scan","callout":"Scanăm j=1: a[1]=3 este mai mic — noul minim este la indicele 1."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":2,"min":1,"phase":"scan","callout":"Scanăm j=2: a[2]=8 ≥ minimul curent a[1]=3 — minimul rămâne la 1."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":3,"min":3,"phase":"scan","callout":"Scanăm j=3: a[3]=1 este mai mic — noul minim este la indicele 3."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":4,"min":3,"phase":"scan","callout":"Scanăm j=4: a[4]=6 ≥ minimul curent a[3]=1 — minimul rămâne la 3."},{"array":[1,3,8,5,6],"sortedCount":1,"i":0,"j":4,"min":3,"phase":"swap","callout":"Schimbăm a[0] cu a[3]: minimul 1 ajunge la poziția 0. Stânga lui 0 este sortată."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":1,"min":1,"phase":"scan","callout":"Runda i=1: presupunem că minimul este a[1]=3."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":2,"min":1,"phase":"scan","callout":"Scanăm j=2: a[2]=8 ≥ minimul curent a[1]=3 — minimul rămâne la 1."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":3,"min":1,"phase":"scan","callout":"Scanăm j=3: a[3]=5 ≥ minimul curent a[1]=3 — minimul rămâne la 1."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":4,"min":1,"phase":"scan","callout":"Scanăm j=4: a[4]=6 ≥ minimul curent a[1]=3 — minimul rămâne la 1."},{"array":[1,3,8,5,6],"sortedCount":2,"i":1,"j":4,"min":1,"phase":"swap","callout":"Minimul a[1]=3 este deja la poziția 1 — poziția 1 este fixată."},{"array":[1,3,8,5,6],"sortedCount":2,"i":2,"j":2,"min":2,"phase":"scan","callout":"Runda i=2: presupunem că minimul este a[2]=8."},{"array":[1,3,8,5,6],"sortedCount":2,"i":2,"j":3,"min":3,"phase":"scan","callout":"Scanăm j=3: a[3]=5 este mai mic — noul minim este la indicele 3."},{"array":[1,3,8,5,6],"sortedCount":2,"i":2,"j":4,"min":3,"phase":"scan","callout":"Scanăm j=4: a[4]=6 ≥ minimul curent a[3]=5 — minimul rămâne la 3."},{"array":[1,3,5,8,6],"sortedCount":3,"i":2,"j":4,"min":3,"phase":"swap","callout":"Schimbăm a[2] cu a[3]: minimul 5 ajunge la poziția 2. Stânga lui 2 este sortată."},{"array":[1,3,5,8,6],"sortedCount":3,"i":3,"j":3,"min":3,"phase":"scan","callout":"Runda i=3: presupunem că minimul este a[3]=8."},{"array":[1,3,5,8,6],"sortedCount":3,"i":3,"j":4,"min":4,"phase":"scan","callout":"Scanăm j=4: a[4]=6 este mai mic — noul minim este la indicele 4."},{"array":[1,3,5,6,8],"sortedCount":4,"i":3,"j":4,"min":4,"phase":"swap","callout":"Schimbăm a[3] cu a[4]: minimul 6 ajunge la poziția 3. Stânga lui 3 este sortată."}]}';

// Lectie BRUTALIST palette (the reference's default :root) = DESIGN.md DARK surface.
const C = {
  bg: "#0e0e0e",
  ink: "#fff",
  muted: "#9a9a9a",
  dim: "#cfcfcf",
  faint: "#888",
  acc: "#fde047",
  acc2: "#fde047",
  card: "#161616",
  line: "#333",
  btnline: "#555",
  cellInk: "#000",
  gate: "#c9a227",
} as const;

const MONO = '"JetBrains Mono", ui-monospace, Consolas, monospace';

// The three teaching beats of this micro-lesson. The figure lives in beat ② (PRIVEȘTE).
type Beat = { step: string; render: "predict" | "figure" | "name" };
const BEATS: Beat[] = [
  { step: "① INTUIȚIE — întâi prezici", render: "predict" },
  { step: "② PRIVEȘTE — ce face de fapt algoritmul", render: "figure" },
  { step: "③ ACUM ARE UN NUME", render: "name" },
];

const accSpan = (t: string) => <span style={{ color: C.acc }}>{t}</span>;

export function LectieSelectSortDemo() {
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
    <div data-testid="lectie-selectsort" style={wrap}>
      {/* The shell's playback buttons are restyled to the lectie brutalist-yellow look. They carry
          inline styles, so we override with !important, scoped to the seq-array controls subtree. */}
      <style>{`
        /* Drop the shell's white/black box chrome — the figure floats on the dark stage like lectie. */
        [data-testid="seq-array-root"] {
          border: none !important; background: transparent !important; padding: 0 !important;
          grid-template-columns: 1fr !important; gap: 12px !important; min-height: 0 !important;
          /* figure + playback bar are BOTH content-sized rows packed as ONE tight unit (no 1fr row, so
             no dead gap opens between the cells and the bar). The unit's overall height is bounded by
             the SVG max-height below; the figWrap then vertically centers the whole figure+helper block
             in the header→footer band. */
          grid-template-rows: auto auto !important;
        }
        /* The dark figure uses a short 2:1 viewBox (packed, no void); cap its rendered height so the
           whole figure column (svg + playback bar + helper) fits the header→footer band even at the
           shortest target (1536×648). The SVG keeps width:100% so it scales down on narrow screens. */
        [data-testid="seq-array-root"] .algo-stepper-shell-svg {
          border: none !important; background: ${C.bg} !important;
          max-height: min(46vh, 300px) !important; min-height: 0 !important;
          align-self: center !important;
        }
        /* Controls = a clean centered lectie playback bar: step ◀ ▶ ↻ + counter only — packed tight. */
        [data-testid="seq-array-controls"] { align-items: center !important; gap: 6px !important; }
        [data-testid="seq-array-controls"] > div:first-child { display: flex; flex-direction: column; align-items: center; }
        [data-testid="seq-array-controls"] > div:first-child > div:first-child { margin-bottom: 0 !important; }
        [data-testid="seq-array-controls"] input[type="range"] { margin-top: 4px !important; }
        [data-testid="seq-array-controls"] > div:first-child { text-align: center; }
        [data-testid="seq-array-controls"] [data-testid="seq-array-frame-counter"] { color: ${C.faint} !important; text-align: center; }
        [data-testid="seq-array-controls"] > div:first-child > div:first-child { color: ${C.faint} !important; text-align: center; }
        [data-testid="seq-array-controls"] input[type="range"] { accent-color: ${C.acc} !important; max-width: 360px; }
        [data-testid="seq-array-controls"] > div:nth-child(2) { justify-content: center !important; gap: 8px !important; }
        [data-testid="seq-array-controls"] button {
          background: ${C.acc} !important; color: ${C.cellInk} !important;
          border: 2px solid ${C.acc} !important; border-radius: 3px !important; font-family: ${MONO} !important;
          font-weight: 700 !important; letter-spacing: 0.04em !important;
          text-transform: uppercase !important; padding: 8px 16px !important; cursor: pointer !important;
        }
        [data-testid="seq-array-controls"] button:disabled {
          background: transparent !important; color: ${C.faint} !important;
          border-color: ${C.btnline} !important; opacity: 1 !important;
        }
        /* Hide share + voice — not part of the lectie figure bar. */
        [data-testid="seq-array-share"], [data-testid="seq-array-voice"] { display: none !important; }
        [data-testid="seq-array-controls"] [data-testid="seq-array-live"] { color: ${C.bg} !important; }
      `}</style>

      {/* ── MASTHEAD — wordmark + trust badge (matches lectie .mast) ── */}
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
          <span
            style={{
              width: 16,
              height: 16,
              borderRadius: "50%",
              background: `linear-gradient(135deg, ${C.acc2}, ${C.acc})`,
            }}
          />
          TUTOR
        </div>
        <div data-testid="trust-badge" style={{ fontSize: 10, letterSpacing: "0.14em", color: C.muted }}>
          PA · Selection Sort · corespunde cursului
        </div>
      </div>

      {/* ── PIPS — progress across the 3 beats (matches lectie .pips) ── */}
      <div style={{ display: "flex", gap: 7, justifyContent: "center", padding: 12, flex: "0 0 auto" }}>
        {BEATS.map((_, i) => (
          <div
            key={i}
            style={{
              width: 34,
              height: 5,
              background: i === cur ? C.acc : i < cur ? C.acc2 : C.line,
              opacity: i < cur ? 0.5 : 1,
            }}
          />
        ))}
      </div>

      {/* ── STAGE ── */}
      <div style={{ flex: "1 1 auto", position: "relative", overflow: "hidden", display: "flex", flexDirection: "column", alignItems: "center", padding: "6px 26px 0" }}>
        <div style={{ fontSize: 11, letterSpacing: "0.2em", color: C.faint, marginBottom: 8, textAlign: "center" }}>{beat.step}</div>

        {beat.render === "predict" && (
          <PredictBeat predict={predict} setPredict={setPredict} />
        )}

        {beat.render === "figure" && (
          <div style={{ width: "100%", maxWidth: 720, flex: "1 1 auto", display: "flex", flexDirection: "column", justifyContent: "center", minHeight: 0 }}>
            <SequenceArrayFamily
              instanceId="viz-pa-selectsort-001"
              dataJson={SELECTSORT_DATA_JSON}
              language="ro"
              variant="dark"
              layout={{ canvasBg: C.bg, controls: "bottom", maxWidth: 720 }}
              labels={{ frame: "PAS", reset: "↻", play: "▶ redă", pause: "⏸ pauză" }}
            />
            <div style={{ fontSize: 12, color: C.faint, textAlign: "center", marginTop: 6 }}>
              ▶ pășește prin animație cu butoanele de mai jos
            </div>
          </div>
        )}

        {beat.render === "name" && <NameBeat predict={predict} />}
      </div>

      {/* ── FOOTER — înapoi / gatemsg / continuă (matches lectie .ctl) ── */}
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
        <button
          data-testid="lectie-back"
          onClick={() => setCur((c) => Math.max(0, c - 1))}
          disabled={cur === 0}
          style={navBtn(cur === 0, true)}
        >
          ‹ înapoi
        </button>
        <span style={{ fontSize: 11, color: C.gate, letterSpacing: "0.04em" }}>
          {gateOK ? "" : "răspunde ca să continui"}
        </span>
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
  const arr = [5, 3, 8, 1, 6];
  return (
    <>
      <h2 style={{ fontSize: "clamp(19px,2.5vw,25px)", lineHeight: 1.18, margin: "0 0 6px", maxWidth: 800, textAlign: "center", fontWeight: 700 }}>
        Vrei să sortezi {accSpan("[5, 3, 8, 1, 6]")} crescător. Strategia: la fiecare pas, găsește {accSpan("cel mai mic")} element rămas și mută-l la stânga.
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
      <p style={{ fontSize: 15, lineHeight: 1.6, color: C.dim, maxWidth: 640, margin: "6px 0", textAlign: "center" }}>
        Care element ajunge {accSpan("primul")} la poziția 0 (cel mai din stânga)?
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
      {/* NEUTRAL predict ack — the SAME message for every choice: NO right/wrong, NO "bun", NO "cel
          mai mic", NO mention of which is the minimum. The reveal belongs to the figure beat
          (PRIVEȘTE) + the NameBeat check, NOT here — spoiling the answer at PREDICT kills the
          predict→watch→reveal pedagogy (lectie tells you nothing until you watch). */}
      <div style={{ fontSize: 12, color: C.faint, marginTop: 4, minHeight: 18, textAlign: "center" }}>
        {predict == null
          ? "alege o variantă ca să continui — nu contează dacă greșești, asta e ideea"
          : `ai ales ${predict} — hai să vezi cum lucrează algoritmul`}
      </div>
    </>
  );
}

function NameBeat({ predict }: { predict: number | null }) {
  return (
    <>
      <h2 style={{ fontSize: "clamp(19px,2.5vw,25px)", lineHeight: 1.18, margin: "0 0 6px", maxWidth: 800, textAlign: "center", fontWeight: 700 }}>
        Asta e {accSpan("Sortarea prin Selecție")}: pentru fiecare poziție i, scanează restul vectorului → găsește minimul → schimbă-l la poziția i.
      </h2>
      <p style={{ fontSize: 15, lineHeight: 1.6, color: C.dim, maxWidth: 680, margin: "6px 0", textAlign: "center" }}>
        <b>Invariant:</b> după runda i, primele i+1 poziții sunt {accSpan("definitiv sortate")} — bara galbenă crește mereu spre dreapta.
      </p>
      <p style={{ fontSize: 15, lineHeight: 1.6, color: C.dim, maxWidth: 680, margin: "6px 0", textAlign: "center" }}>
        Pentru n elemente faci {accSpan("n−1 runde")}, iar runda i compară ~n−i elemente — în total ~n²/2 comparații.
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
        (n−1) + (n−2) + … + 1 ⇒ O(n²)
      </p>
      <p style={{ color: C.muted, fontSize: 13, textAlign: "center" }}>
        {predict === 1
          ? "(tu ai prezis 1 — exact! minimul ajunge primul la stânga)"
          : predict != null
            ? "(tu ai prezis " + predict + " — minimul real era 1, primul plasat la stânga)"
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
