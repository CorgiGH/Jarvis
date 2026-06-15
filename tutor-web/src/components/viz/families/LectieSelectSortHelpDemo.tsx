import { useState, type CSSProperties } from "react";
import { SequenceArrayFamily } from "./SequenceArrayFamily";

/**
 * LIVE-HELP-LAYER fold-in demo — takes the REAL lectie-selectsort lesson surface (same dark brutalist
 * shell, same SequenceArrayFamily figure) and folds in the two proposed help affordances so Alex can
 * SEE where + how they sit:
 *   ① ASK-IN-LESSON  — a pinned "întreabă" chip → a KC/beat-anchored grounded ask panel (the real
 *                      sidekick/ask flow: citation-backed answer, drill-self-paste guard, RO framing).
 *   ② PREREQ-PEEK    — a dotted-underlined prerequisite term → an on-demand compressed infographic
 *                      popover (the §7.3 compressed-reveal, triggered by the learner, no lesson derail).
 *
 * Route: /tutor/lectie-selectsort-help. THROWAWAY quality vehicle — NOT a production lesson.
 */

const SELECTSORT_DATA_JSON =
  '{"values":[5,3,8,1,6],"steps":[{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":0,"min":0,"phase":"scan","callout":"Runda i=0: presupunem că minimul este a[0]=5."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":1,"min":1,"phase":"scan","callout":"Scanăm j=1: a[1]=3 este mai mic — noul minim este la indicele 1."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":3,"min":3,"phase":"scan","callout":"Scanăm j=3: a[3]=1 este mai mic — noul minim este la indicele 3."},{"array":[1,3,8,5,6],"sortedCount":1,"i":0,"j":4,"min":3,"phase":"swap","callout":"Schimbăm a[0] cu a[3]: minimul 1 ajunge la poziția 0. Stânga lui 0 este sortată."},{"array":[1,3,5,6,8],"sortedCount":4,"i":3,"j":4,"min":4,"phase":"swap","callout":"Vectorul este sortat."}]}';

const C = {
  bg: "#0e0e0e",
  ink: "#fff",
  muted: "#9a9a9a",
  dim: "#cfcfcf",
  faint: "#888",
  acc: "#fde047",
  card: "#161616",
  card2: "#1d1d1d",
  line: "#333",
  btnline: "#555",
  cellInk: "#000",
  gate: "#c9a227",
} as const;

const MONO = '"JetBrains Mono", ui-monospace, Consolas, monospace';
const accSpan = (t: string) => <span style={{ color: C.acc }}>{t}</span>;

export function LectieSelectSortHelpDemo() {
  const [ask, setAsk] = useState(false);
  const [peek, setPeek] = useState(false);

  const wrap: CSSProperties = {
    margin: 0,
    height: "100vh",
    background: C.bg,
    color: C.ink,
    fontFamily: MONO,
    overflow: "hidden",
    display: "flex",
    flexDirection: "column",
    position: "relative",
  };

  return (
    <div data-testid="lectie-selectsort-help" style={wrap}>
      <style>{`
        [data-testid="seq-array-root"] {
          border: none !important; background: transparent !important; padding: 0 !important;
          grid-template-columns: 1fr !important; gap: 10px !important; min-height: 0 !important;
          grid-template-rows: auto auto !important;
        }
        [data-testid="seq-array-root"] .algo-stepper-shell-svg {
          border: none !important; background: ${C.bg} !important;
          max-height: min(40vh, 260px) !important; min-height: 0 !important; align-self: center !important;
        }
        [data-testid="seq-array-controls"] { align-items: center !important; gap: 6px !important; }
        [data-testid="seq-array-controls"] > div:nth-child(2) { justify-content: center !important; gap: 8px !important; }
        [data-testid="seq-array-controls"] button {
          background: ${C.acc} !important; color: ${C.cellInk} !important;
          border: 2px solid ${C.acc} !important; border-radius: 3px !important; font-family: ${MONO} !important;
          font-weight: 700 !important; text-transform: uppercase !important; padding: 7px 14px !important; cursor: pointer !important;
        }
        [data-testid="seq-array-controls"] button:disabled { background: transparent !important; color: ${C.faint} !important; border-color: ${C.btnline} !important; }
        [data-testid="seq-array-share"], [data-testid="seq-array-voice"] { display: none !important; }
        [data-testid="seq-array-controls"] input[type="range"] { accent-color: ${C.acc} !important; max-width: 320px; }
      `}</style>

      {/* ── MASTHEAD ── */}
      <div style={{ height: 46, flex: "0 0 auto", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 22px", borderBottom: `3px solid ${C.acc}`, color: C.acc }}>
        <div style={{ display: "flex", alignItems: "center", gap: 9, fontWeight: 700, letterSpacing: "0.12em", fontSize: 13 }}>
          <span style={{ width: 16, height: 16, borderRadius: "50%", background: C.acc }} />
          TUTOR
        </div>
        <div style={{ fontSize: 10, letterSpacing: "0.14em", color: C.muted }}>PA · Selection Sort · corespunde cursului</div>
      </div>

      {/* ── PIPS ── */}
      <div style={{ display: "flex", gap: 7, justifyContent: "center", padding: 11, flex: "0 0 auto" }}>
        {[0, 1, 2].map((i) => (
          <div key={i} style={{ width: 34, height: 5, background: i === 1 ? C.acc : i < 1 ? C.acc : C.line, opacity: i < 1 ? 0.5 : 1 }} />
        ))}
      </div>

      {/* ── STAGE ── */}
      <div style={{ flex: "1 1 auto", position: "relative", overflow: "hidden", display: "flex", flexDirection: "column", alignItems: "center", padding: "4px 26px 0" }}>
        <div style={{ fontSize: 11, letterSpacing: "0.2em", color: C.faint, marginBottom: 6, textAlign: "center" }}>② PRIVEȘTE — urmărește cel mai mic</div>

        <div style={{ width: "100%", maxWidth: 720, flex: "1 1 auto", display: "flex", flexDirection: "column", justifyContent: "center", minHeight: 0 }}>
          <SequenceArrayFamily
            instanceId="viz-pa-selectsort-001"
            dataJson={SELECTSORT_DATA_JSON}
            language="ro"
            variant="dark"
            layout={{ canvasBg: C.bg, controls: "bottom", maxWidth: 720 }}
            labels={{ frame: "PAS", reset: "↻", play: "▶ redă", pause: "⏸ pauză" }}
          />
          {/* PREREQ-PEEK trigger — a slim recap pill, no redundant phrasing */}
          <div style={{ display: "flex", justifyContent: "center", marginTop: 12 }}>
            <button
              data-testid="prereq-term"
              onClick={() => { setPeek((p) => !p); setAsk(false); }}
              style={{
                display: "inline-flex", alignItems: "center", gap: 7,
                background: "transparent", border: `1px solid ${C.btnline}`, borderRadius: 20,
                padding: "5px 14px", cursor: "pointer", fontFamily: MONO, fontSize: 12, color: C.dim,
              }}
            >
              <span style={{ color: C.acc, fontSize: 13 }}>↻</span> recapitulează interschimbarea
            </button>
          </div>
        </div>

        {/* ── ② PREREQ-PEEK popover (on-demand compressed reveal, no lesson derail) ── */}
        {peek && <PrereqPeek onClose={() => setPeek(false)} />}

        {/* ── ① ASK chip — pinned bottom-right of the stage ── */}
        {!ask && (
          <button
            data-testid="ask-chip"
            onClick={() => { setAsk(true); setPeek(false); }}
            style={{
              position: "absolute", right: 18, bottom: 14, zIndex: 5,
              display: "flex", alignItems: "center", gap: 8,
              background: C.card, color: C.acc, border: `1.5px solid ${C.acc}`,
              borderRadius: 11, padding: "9px 15px", cursor: "pointer",
              fontFamily: MONO, fontWeight: 700, fontSize: 13, letterSpacing: "0.02em",
              boxShadow: "0 3px 14px rgba(0,0,0,0.45)",
            }}
          >
            <span style={{ fontSize: 14 }}>💬</span> întreabă
          </button>
        )}

        {/* ── ① ASK panel — KC/beat-anchored grounded ask ── */}
        {ask && <AskPanel onClose={() => setAsk(false)} />}
      </div>

      {/* ── FOOTER ── */}
      <div style={{ flex: "0 0 auto", height: 64, display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 26px", borderTop: `3px solid ${C.acc}` }}>
        <button style={navBtn(false, true)}>‹ înapoi</button>
        <span style={{ fontSize: 11, color: C.gate }} />
        <button style={navBtn(false, false)}>continuă ›</button>
      </div>
    </div>
  );
}

/** ② PREREQ-PEEK — compact recap card: accent strip + mini swap figure + code + why + provenance. */
function PrereqPeek({ onClose }: { onClose: () => void }) {
  const cell: CSSProperties = {
    width: 40, height: 44, border: `2px solid ${C.acc}`, background: C.acc,
    color: C.cellInk, fontWeight: 700, fontSize: 18, display: "flex", alignItems: "center",
    justifyContent: "center", fontFamily: MONO, borderRadius: 4,
  };
  return (
    <div
      data-testid="prereq-peek"
      style={{
        position: "absolute", left: "50%", top: "50%", transform: "translate(-50%,-50%)", zIndex: 8,
        width: 348, background: C.card, border: `1px solid ${C.line}`, borderTop: `3px solid ${C.acc}`,
        borderRadius: 8, boxShadow: "0 16px 50px rgba(0,0,0,0.75)", padding: "13px 18px 16px",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 3 }}>
        <span style={{ fontSize: 10, letterSpacing: "0.16em", color: C.gate, fontWeight: 700 }}>↻ RECAP RAPID</span>
        <button data-testid="peek-close" onClick={onClose} style={{ background: "none", border: "none", color: C.faint, cursor: "pointer", fontSize: 15, lineHeight: 1 }}>✕</button>
      </div>
      <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 13 }}>Cum interschimbi două valori</div>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 12, margin: "2px 0 14px" }}>
        <div style={{ textAlign: "center" }}><div style={cell}>1</div><div style={{ fontSize: 10, color: C.faint, marginTop: 4 }}>a[i]</div></div>
        <span style={{ color: C.acc, fontSize: 19 }}>⇄</span>
        <div style={{ textAlign: "center" }}><div style={cell}>5</div><div style={{ fontSize: 10, color: C.faint, marginTop: 4 }}>a[j]</div></div>
      </div>
      <pre style={{ background: C.bg, border: `1px solid ${C.line}`, borderRadius: 5, padding: "10px 12px", margin: "0 0 11px", fontSize: 12.5, color: C.dim, lineHeight: 1.6, fontFamily: MONO, overflow: "auto" }}>
{`int tmp = a[i];
a[i]  = a[j];
a[j]  = tmp;`}
      </pre>
      <div style={{ fontSize: 12, color: C.muted, lineHeight: 1.5, marginBottom: 14 }}>
        <b style={{ color: C.dim }}>tmp</b> ține prima valoare cât o suprascrii — altfel o pierzi.
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span style={{ fontSize: 10, color: C.faint, border: `1px solid ${C.line}`, borderRadius: 4, padding: "4px 8px" }}>📖 PA · Cursul 1 · p.2</span>
        <button onClick={onClose} style={{ ...navBtn(false, false), padding: "7px 15px", fontSize: 12 }}>am înțeles ✓</button>
      </div>
    </div>
  );
}

/** ① ASK panel — beat/KC-anchored grounded ask (real sidekick/ask flow, citation-backed). */
function AskPanel({ onClose }: { onClose: () => void }) {
  return (
    <div data-testid="ask-panel" style={{ position: "absolute", right: 0, top: 0, bottom: 0, zIndex: 7, width: 384, maxWidth: "92%", background: C.card, borderLeft: `3px solid ${C.acc}`, display: "flex", flexDirection: "column", boxShadow: "-8px 0 32px rgba(0,0,0,0.6)" }}>
      {/* header — anchor pill: current beat + KC */}
      <div style={{ flex: "0 0 auto", padding: "13px 16px", borderBottom: `1px solid ${C.line}` }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
          <span style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.06em", color: C.acc }}>ÎNTREABĂ TUTORUL</span>
          <button data-testid="ask-close" onClick={onClose} style={{ background: "none", border: "none", color: C.faint, cursor: "pointer", fontSize: 16, lineHeight: 1 }}>✕</button>
        </div>
        <span style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 10.5, color: C.muted, background: C.card2, border: `1px solid ${C.line}`, borderRadius: 20, padding: "4px 10px" }}>
          <span style={{ width: 6, height: 6, borderRadius: "50%", background: C.acc }} /> ② PRIVEȘTE · Sortare prin selecție
        </span>
      </div>
      {/* conversation */}
      <div style={{ flex: "1 1 auto", overflow: "auto", padding: "14px 16px", display: "flex", flexDirection: "column", gap: 11 }}>
        <div style={{ alignSelf: "flex-end", maxWidth: "85%", background: C.acc, color: C.cellInk, borderRadius: "12px 12px 3px 12px", padding: "8px 12px", fontSize: 13, fontWeight: 600 }}>
          De ce comparăm tot restul vectorului la fiecare pas?
        </div>
        <div style={{ alignSelf: "flex-start", maxWidth: "92%", background: C.card2, color: C.dim, borderRadius: "12px 12px 12px 3px", padding: "10px 13px", fontSize: 13, lineHeight: 1.55 }}>
          Ca să fii <b style={{ color: C.ink }}>sigur</b> că ai găsit cel mai mic rămas. Reține primul candidat, dar scanează mai departe — unul și mai mic poate apărea (ex. <span style={{ color: C.acc }}>1</span> la indicele 3).
          <div style={{ marginTop: 9 }}>
            <span style={{ fontSize: 10, color: C.faint, border: `1px solid ${C.line}`, borderRadius: 4, padding: "3px 8px" }}>📖 PA · Cursul 1 · „minimul curent"</span>
          </div>
        </div>
      </div>
      {/* suggestions — help a stuck student who doesn't know what to ask */}
      <div style={{ flex: "0 0 auto", padding: "10px 14px 0", borderTop: `1px solid ${C.line}` }}>
        <div style={{ fontSize: 9.5, color: C.faint, marginBottom: 7, letterSpacing: "0.12em" }}>SUGESTII</div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
          {["De ce nu ne oprim la primul mai mic?", "Câte comparații în total?"].map((s) => (
            <span key={s} style={{ fontSize: 11, color: C.dim, border: `1px solid ${C.btnline}`, borderRadius: 14, padding: "5px 10px", cursor: "pointer" }}>{s}</span>
          ))}
        </div>
      </div>
      {/* input */}
      <div style={{ flex: "0 0 auto", padding: "11px 14px", display: "flex", gap: 8 }}>
        <input placeholder="întreabă despre acest pas…" style={{ flex: 1, background: C.bg, border: `1px solid ${C.btnline}`, borderRadius: 6, padding: "9px 11px", color: C.ink, fontFamily: MONO, fontSize: 12.5 }} />
        <button style={{ ...navBtn(false, false), padding: "8px 14px", fontSize: 12 }}>↑</button>
      </div>
    </div>
  );
}

function navBtn(disabled: boolean, ghost: boolean): CSSProperties {
  return {
    fontFamily: MONO, fontSize: 13, fontWeight: 700, letterSpacing: "0.04em",
    border: `3px solid ${disabled ? C.btnline : C.acc}`,
    background: disabled ? "transparent" : ghost ? "transparent" : C.acc,
    color: disabled ? C.faint : ghost ? C.acc : C.cellInk,
    padding: "9px 22px", cursor: disabled ? "default" : "pointer", borderRadius: 3,
  };
}
