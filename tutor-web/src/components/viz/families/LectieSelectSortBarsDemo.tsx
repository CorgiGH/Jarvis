import { useState, type CSSProperties } from "react";
import { SequenceArrayFamily } from "./SequenceArrayFamily";

/**
 * Plan-V family-2 BARS INTUITION demo — the same lectie lesson surface as LectieSelectSortDemo, but
 * mounting the seq-array family with `variant="bars"` (HEIGHT ∝ VALUE) + a PLAIN-LANGUAGE instance
 * (viz-pa-selectsort-bars-001) whose callouts carry ZERO index jargon. Where the trace demo narrates
 * bookkeeping ("Scanăm j=3: a[3]=1 este mai mic"), this teaches the IDEA led by a VISUAL gesture:
 * "find the SHORTEST bar of what's left → slide it to the front → repeat". The bars make "smallest"
 * something you SEE (the lowest bar), not a digit you have to compare.
 *
 * The figure is the SAME family component (same typed data_json, same data-cell-index stamps, same
 * trace correctness/oracle) — only the render skin (bars) + the plain callouts change. The 3 gated
 * beats (PREDICT → PRIVEȘTE → NUMEȘTE) mirror the trace demo, but predict + name speak plainly
 * (smallest→front→repeat), never in indices, and predict stays NEUTRAL (no answer leak).
 *
 * Route: /tutor/lectie-selectsort-bars (wired in main.tsx). A quality vehicle, not a production lesson.
 */

// viz-pa-selectsort-bars-001 data_json — byte-identical to the shipped YAML (same values + trace as
// the trace demo, so it stays correct + the oracle/invariants pass; ONLY the callouts are plain RO).
const SELECTSORT_BARS_DATA_JSON =
  '{"values":[5,3,8,1,6],"steps":[{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":0,"min":0,"phase":"scan","callout":"Căutăm cel mai mic număr din partea nesortată."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":1,"min":1,"phase":"scan","callout":"3 e mai mic decât 5 — acum el e cel mai mic."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":2,"min":1,"phase":"scan","callout":"8 e mai mare — îl sărim."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":3,"min":3,"phase":"scan","callout":"1 e și mai mic — el e cel mai mic de până acum."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":4,"min":3,"phase":"scan","callout":"6 e mai mare — îl sărim. Cel mai mic rămâne 1."},{"array":[1,3,8,5,6],"sortedCount":1,"i":0,"j":4,"min":3,"phase":"swap","callout":"L-am găsit pe cel mai mic: 1. Îl mutăm în față."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":1,"min":1,"phase":"scan","callout":"Partea din stânga e gata. Căutăm din nou cel mai mic în rest."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":2,"min":1,"phase":"scan","callout":"8 e mai mare — îl sărim."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":3,"min":1,"phase":"scan","callout":"5 e mai mare — îl sărim."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":4,"min":1,"phase":"scan","callout":"6 e mai mare — îl sărim. Cel mai mic rămâne 3."},{"array":[1,3,8,5,6],"sortedCount":2,"i":1,"j":4,"min":1,"phase":"swap","callout":"Cel mai mic, 3, e deja în față. Îl lăsăm pe loc."},{"array":[1,3,8,5,6],"sortedCount":2,"i":2,"j":2,"min":2,"phase":"scan","callout":"Repetăm pentru ce-a rămas. Cel mai mic de acum e 8."},{"array":[1,3,8,5,6],"sortedCount":2,"i":2,"j":3,"min":3,"phase":"scan","callout":"5 e mai mic decât 8 — acum el e cel mai mic."},{"array":[1,3,8,5,6],"sortedCount":2,"i":2,"j":4,"min":3,"phase":"scan","callout":"6 e mai mare — îl sărim. Cel mai mic rămâne 5."},{"array":[1,3,5,8,6],"sortedCount":3,"i":2,"j":4,"min":3,"phase":"swap","callout":"L-am găsit pe cel mai mic: 5. Îl mutăm în față."},{"array":[1,3,5,8,6],"sortedCount":3,"i":3,"j":3,"min":3,"phase":"scan","callout":"Au mai rămas două. Cel mai mic de acum e 8."},{"array":[1,3,5,8,6],"sortedCount":3,"i":3,"j":4,"min":4,"phase":"scan","callout":"6 e mai mic decât 8 — acum el e cel mai mic."},{"array":[1,3,5,6,8],"sortedCount":4,"i":3,"j":4,"min":4,"phase":"swap","callout":"Mutăm 6 în față. Ce rămâne e deja la locul lui — gata, totul e sortat."}]}';

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

// The three teaching beats of this micro-lesson. The bars figure lives in beat ② (PRIVEȘTE).
type Beat = { step: string; render: "predict" | "figure" | "name" };
const BEATS: Beat[] = [
  { step: "① INTUIȚIE — întâi prezici", render: "predict" },
  { step: "② PRIVEȘTE — urmărește cel mai mic", render: "figure" },
  { step: "③ ACUM ARE UN NUME", render: "name" },
];

const accSpan = (t: string) => <span style={{ color: C.acc }}>{t}</span>;

export function LectieSelectSortBarsDemo() {
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
    <div data-testid="lectie-selectsort-bars" style={wrap}>
      {/* The shell's playback buttons are restyled to the lectie brutalist-yellow look. They carry
          inline styles, so we override with !important, scoped to the seq-array controls subtree. */}
      <style>{`
        /* Drop the shell's white/black box chrome — the figure floats on the dark stage like lectie. */
        [data-testid="seq-array-root"] {
          border: none !important; background: transparent !important; padding: 0 !important;
          grid-template-columns: 1fr !important; gap: 12px !important; min-height: 0 !important;
          grid-template-rows: auto auto !important;
        }
        /* The bars figure uses a taller 480×300 viewBox (height∝value needs vertical room); cap its
           rendered height so the whole figure column (svg + playback bar + helper) fits the
           header→footer band even at the shortest target (1536×648). The SVG keeps width:100%. */
        [data-testid="seq-array-root"] .algo-stepper-shell-svg {
          border: none !important; background: ${C.bg} !important;
          max-height: min(52vh, 360px) !important; min-height: 0 !important;
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
          <div style={{ width: "100%", maxWidth: 760, flex: "1 1 auto", display: "flex", flexDirection: "column", justifyContent: "center", minHeight: 0 }}>
            <SequenceArrayFamily
              instanceId="viz-pa-selectsort-bars-001"
              dataJson={SELECTSORT_BARS_DATA_JSON}
              language="ro"
              variant="bars"
              layout={{ canvasBg: C.bg, controls: "bottom", maxWidth: 760 }}
              labels={{ frame: "PAS", reset: "↻", play: "▶ redă", pause: "⏸ pauză" }}
            />
            <div style={{ fontSize: 12, color: C.faint, textAlign: "center", marginTop: 6 }}>
              ▶ urmărește cum cea mai scurtă bară alunecă mereu în față
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
      <h2 style={{ fontSize: "clamp(19px,2.5vw,25px)", lineHeight: 1.18, margin: "0 0 6px", maxWidth: 820, textAlign: "center", fontWeight: 700 }}>
        Vrei să le pui în ordine, de la mic la mare. Ideea: găsește mereu {accSpan("cel mai mic")} număr rămas și mută-l în {accSpan("față")}.
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
        Care număr crezi că ajunge {accSpan("primul")} în față?
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
          mai mic", NO hint at which is the minimum. The reveal belongs to the figure beat (PRIVEȘTE)
          + the NameBeat check, NOT here — spoiling the answer at PREDICT kills predict→watch→reveal. */}
      <div style={{ fontSize: 12, color: C.faint, marginTop: 4, minHeight: 18, textAlign: "center" }}>
        {predict == null
          ? "alege o variantă ca să continui — nu contează dacă greșești, asta e ideea"
          : `ai ales ${predict} — hai să vezi cum lucrează`}
      </div>
    </>
  );
}

function NameBeat({ predict }: { predict: number | null }) {
  return (
    <>
      <h2 style={{ fontSize: "clamp(19px,2.5vw,25px)", lineHeight: 1.18, margin: "0 0 6px", maxWidth: 820, textAlign: "center", fontWeight: 700 }}>
        Asta e {accSpan("Sortarea prin Selecție")}: găsește cel mai mic din ce-a rămas → mută-l în față → repetă.
      </h2>
      <p style={{ fontSize: 15, lineHeight: 1.6, color: C.dim, maxWidth: 700, margin: "6px 0", textAlign: "center" }}>
        Partea din stânga (barele galbene) crește mereu — odată ce un număr a ajuns în față, {accSpan("rămâne acolo")}.
      </p>
      <p style={{ fontSize: 15, lineHeight: 1.6, color: C.dim, maxWidth: 700, margin: "6px 0", textAlign: "center" }}>
        De fiecare dată trebuie să te uiți prin tot ce-a rămas ca să-l găsești pe cel mai mic — de aceea, pentru liste mari, devine {accSpan("lent")}.
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
        cauți cel mai mic, iar și iar ⇒ O(n²)
      </p>
      <p style={{ color: C.muted, fontSize: 13, textAlign: "center" }}>
        {predict === 1
          ? "(tu ai prezis 1 — exact! cea mai scurtă bară ajunge prima în față)"
          : predict != null
            ? "(tu ai prezis " + predict + " — cel mai mic era 1, prima bară mutată în față)"
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
