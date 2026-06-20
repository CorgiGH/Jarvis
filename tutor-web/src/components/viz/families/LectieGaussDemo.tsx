import { type CSSProperties } from "react";
import { MatrixGridFamily } from "./MatrixGridFamily";

/**
 * Standalone lesson-surface for the ALO Gaussian-elimination figure (viz-alo-gauss-002). Composed as a
 * two-panel lesson card: LEFT = the system of equations (the thing the matrix abstracts), RIGHT = the
 * animated augmented-matrix elimination. The figure's per-cell trace is GREEN against the
 * gaussElimReference oracle (trace-match harness); a seeded wrong cell makes it RED — the figure cannot
 * lie about the math.
 *
 * Route: /tutor/lectie-gauss (wired in main.tsx).
 */

// viz-alo-gauss-002 data_json — byte-identical to content/ALO/viz/viz-alo-gauss-002.yaml.
const GAUSS2_DATA_JSON =
  '{"rows":3,"cols":4,"kind":"gauss-elim","seed":{"matrix":[[1,2,2,3],[2,5,7,9],[1,3,6,7]]},"colHeaders":["x1","x2","x3","| b"],"rowHeaders":["E1","E2","E3"],"steps":[{"writes":[{"row":0,"col":0,"value":"1"},{"row":0,"col":1,"value":"2"},{"row":0,"col":2,"value":"2"},{"row":0,"col":3,"value":"3"},{"row":1,"col":0,"value":"2"},{"row":1,"col":1,"value":"5"},{"row":1,"col":2,"value":"7"},{"row":1,"col":3,"value":"9"},{"row":2,"col":0,"value":"1"},{"row":2,"col":1,"value":"3"},{"row":2,"col":2,"value":"6"},{"row":2,"col":3,"value":"7"}],"fills":[{"row":0,"col":0},{"row":0,"col":1},{"row":0,"col":2},{"row":0,"col":3},{"row":1,"col":0},{"row":1,"col":1},{"row":1,"col":2},{"row":1,"col":3},{"row":2,"col":0},{"row":2,"col":1},{"row":2,"col":2},{"row":2,"col":3}],"pivot":null,"callout":"Matricea extinsă a sistemului. Scopul: o aducem la formă superior triunghiulară prin eliminare gaussiană."},{"writes":[],"fills":[],"pivot":{"row":0,"col":0},"rowOp":"pivot a11 = 1","callout":"Pasul 1: pivotul este a11 = 1. Eliminăm x1 din liniile de sub el."},{"writes":[{"row":1,"col":0,"value":"0"},{"row":1,"col":1,"value":"1"},{"row":1,"col":2,"value":"3"},{"row":1,"col":3,"value":"3"}],"fills":[],"pivot":{"row":0,"col":0},"rowOp":"E2 ← E2 + (-2)·E1","callout":"f21 = -2/1 = -2. E2 ← E2 + (-2)·E1 ⇒ (0  1  3 | 3)."},{"writes":[{"row":2,"col":0,"value":"0"},{"row":2,"col":1,"value":"1"},{"row":2,"col":2,"value":"4"},{"row":2,"col":3,"value":"4"}],"fills":[],"pivot":{"row":0,"col":0},"rowOp":"E3 ← E3 + (-1)·E1","callout":"f31 = -1/1 = -1. E3 ← E3 + (-1)·E1 ⇒ (0  1  4 | 4)."},{"writes":[],"fills":[],"pivot":{"row":1,"col":1},"rowOp":"pivot a22 = 1","callout":"Pasul 2: pivotul a22 = 1 (nenul, fără pivotare). Eliminăm x2 din linia de sub el."},{"writes":[{"row":2,"col":1,"value":"0"},{"row":2,"col":2,"value":"1"},{"row":2,"col":3,"value":"1"}],"fills":[],"pivot":{"row":1,"col":1},"rowOp":"E3 ← E3 + (-1)·E2","callout":"f32 = -1/1 = -1. E3 ← E3 + (-1)·E2 ⇒ (0  0  1 | 1). Sistem superior triunghiular ✓."}]}';

const C = {
  bg: "#0e0e0e",
  panel: "#141414",
  ink: "#f4f4f4",
  muted: "#9a9a9a",
  dim: "#cfcfcf",
  faint: "#7a7a7a",
  acc: "#fde047",
  line: "#2a2a2a",
} as const;

const MONO = '"JetBrains Mono", ui-monospace, Consolas, monospace';

// The seed system, as equations — the thing the augmented matrix abstracts.
const SYS: { coeffs: [number, number, number]; b: number }[] = [
  { coeffs: [1, 2, 2], b: 3 },
  { coeffs: [2, 5, 7], b: 9 },
  { coeffs: [1, 3, 6], b: 7 },
];

function Equation({ coeffs, b }: { coeffs: [number, number, number]; b: number }) {
  const term = (c: number, v: string, first: boolean) => (
    <>
      {!first && <span style={{ color: C.faint, margin: "0 6px" }}>+</span>}
      <span style={{ color: C.ink }}>{c}</span>
      <span style={{ color: C.acc }}>{v}</span>
    </>
  );
  return (
    <div style={{ fontSize: 21, fontWeight: 700, letterSpacing: "0.01em", lineHeight: 1.9, whiteSpace: "nowrap" }}>
      {term(coeffs[0], "x₁", true)}
      {term(coeffs[1], "x₂", false)}
      {term(coeffs[2], "x₃", false)}
      <span style={{ color: C.faint, margin: "0 8px" }}>=</span>
      <span style={{ color: C.ink }}>{b}</span>
    </div>
  );
}

export function LectieGaussDemo() {
  const wrap: CSSProperties = {
    margin: 0,
    height: "100vh",
    background: `radial-gradient(1200px 600px at 30% 0%, #161616 0%, ${C.bg} 60%)`,
    color: C.ink,
    fontFamily: MONO,
    overflow: "hidden",
    display: "flex",
    flexDirection: "column",
  };

  return (
    <div data-testid="lectie-gauss" style={wrap}>
      <style>{`
        [data-testid="lgauss-root"] { border: none !important; background: transparent !important; padding: 0 !important; }
        [data-testid="lgauss-root"] .algo-stepper-shell-svg {
          border: none !important; background: transparent !important;
          max-height: 300px !important; min-height: 0 !important; align-self: center !important;
        }
        [data-testid="lgauss-share"], [data-testid="lgauss-voice"] { display: none !important; }
      `}</style>

      {/* ── MASTHEAD ── */}
      <div style={{ height: 46, flex: "0 0 auto", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 26px", borderBottom: `2px solid ${C.acc}`, color: C.acc }}>
        <div style={{ display: "flex", alignItems: "center", gap: 9, fontWeight: 700, letterSpacing: "0.12em", fontSize: 13 }}>
          <span style={{ width: 15, height: 15, borderRadius: "50%", background: C.acc }} />
          TUTOR
        </div>
        <div data-testid="trust-badge" style={{ fontSize: 10, letterSpacing: "0.14em", color: C.muted }}>
          ALO · Eliminare Gauss · corespunde cursului
        </div>
      </div>

      {/* ── STAGE ── */}
      <div style={{ flex: "1 1 auto", overflow: "hidden", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "20px 32px" }}>
        <h1 style={{ fontSize: "clamp(20px,2.5vw,28px)", lineHeight: 1.15, margin: "0 0 4px", textAlign: "center", fontWeight: 800 }}>
          Eliminare <span style={{ color: C.acc }}>Gaussiană</span>
        </h1>
        <p style={{ fontSize: 13, color: C.muted, margin: "0 0 22px", textAlign: "center", letterSpacing: "0.02em" }}>
          rezolvăm sistemul aducând matricea la formă superior triunghiulară
        </p>

        {/* ── two-panel lesson card ── */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "minmax(340px, 460px) minmax(420px, 620px)",
            gap: 0,
            width: "100%",
            maxWidth: 1080,
            border: `1px solid ${C.line}`,
            borderRadius: 14,
            background: "rgba(20,20,20,0.6)",
            boxShadow: "0 24px 60px rgba(0,0,0,0.5)",
            overflow: "hidden",
          }}
        >
          {/* LEFT — the system as equations */}
          <div style={{ padding: "30px 34px", borderRight: `1px solid ${C.line}`, display: "flex", flexDirection: "column", justifyContent: "center" }}>
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.22em", color: C.faint, marginBottom: 18 }}>SISTEMUL</div>
            {SYS.map((e, i) => (
              <Equation key={i} coeffs={e.coeffs} b={e.b} />
            ))}
            <div style={{ marginTop: 22, paddingTop: 18, borderTop: `1px solid ${C.line}`, fontSize: 13, color: C.dim, lineHeight: 1.6 }}>
              Scriem doar <span style={{ color: C.acc }}>coeficienții</span> → matricea extinsă din dreapta. Sub fiecare
              <span style={{ color: C.acc }}> pivot</span> facem zerouri, rând cu rând.
            </div>
            <div style={{ marginTop: 16, fontSize: 14, color: C.muted }}>
              soluție: <span style={{ color: C.acc, fontWeight: 700 }}>x = (1, 0, 1)</span>
            </div>
          </div>

          {/* RIGHT — the animated augmented matrix */}
          <div style={{ padding: "20px 24px 16px", background: "#0c0c0c", display: "flex", flexDirection: "column", justifyContent: "center", minWidth: 0 }}>
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.22em", color: C.faint, marginBottom: 8 }}>MATRICEA EXTINSĂ</div>
            <MatrixGridFamily
              instanceId="viz-alo-gauss-002"
              dataJson={GAUSS2_DATA_JSON}
              language="ro"
              testIdPrefix="lgauss"
              variant="dark"
              layout={{ canvasBg: "transparent", controls: "bottom", maxWidth: 600 }}
              labels={{ frame: "PAS", reset: "↻", play: "▶ redă", pause: "⏸ pauză" }}
            />
          </div>
        </div>
      </div>

      {/* ── FOOTER ── */}
      <div style={{ flex: "0 0 auto", height: 44, display: "flex", alignItems: "center", justifyContent: "center", padding: "0 26px", borderTop: `2px solid ${C.acc}`, color: C.faint, fontSize: 11, letterSpacing: "0.06em" }}>
        figură verificată celulă-cu-celulă față de oracolul de eliminare · niciun pas inventat
      </div>
    </div>
  );
}
