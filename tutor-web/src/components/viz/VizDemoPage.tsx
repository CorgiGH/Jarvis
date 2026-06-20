import type { CSSProperties } from "react";
import { useState } from "react";
import { AlgoStepperShellSmoke } from "./AlgoStepperShellSmoke";
import { CompareFrames } from "./CompareFrames";
import { DPWastedWork } from "./DPWastedWork";
import { BayesTree } from "./BayesTree";
import { NPGadget } from "./NPGadget";
import { TcpCwnd } from "./TcpCwnd";
import { PageTableWalk } from "./PageTableWalk";
import { Tls0RttReplay } from "./Tls0RttReplay";
import { CppVTable } from "./CppVTable";
import { MatrixTransform } from "./MatrixTransform";
import { GraphTreeFamily } from "./families/GraphTreeFamily";
import { ChartDistributionFamily } from "./families/ChartDistributionFamily";
import { SequenceArrayFamily } from "./families/SequenceArrayFamily";
import { MatrixGridFamily } from "./families/MatrixGridFamily";
import { ClassDiagramFamily } from "./families/ClassDiagramFamily";
import { LessonFigureShell } from "../lesson/LessonFigureShell";
import { NumLineDirect } from "./NumLineDirect";
import { OsiEncap } from "./OsiEncap";
import { ProcessFSM } from "./ProcessFSM";
import { RaceMutex } from "./RaceMutex";
import { RecursionTree } from "./RecursionTree";
import { SchedulerGantt } from "./SchedulerGantt";
import { SigmaStackedBar } from "./SigmaStackedBar";
import { SlopeCounter } from "./SlopeCounter";
import { SumPlotTracker } from "./SumPlotTracker";
import { TcpHandshake } from "./TcpHandshake";
import { FONT_FAMILY, INK, PAPER } from "./theme";

const tileStyle: CSSProperties = {
  marginBottom: 48,
};

const headingStyle: CSSProperties = {
  margin: "0 0 8px",
  fontSize: 14,
  fontWeight: 900,
  letterSpacing: "0.06em",
  textTransform: "uppercase",
};

const subheadingStyle: CSSProperties = {
  margin: "0 0 16px",
  fontSize: 11,
  opacity: 0.7,
  letterSpacing: "0.04em",
};

// The viz-pa-mergesort-001 instance data_json (verbatim from content/PA/viz/viz-pa-mergesort-001.yaml).
// Keep byte-identical to the shipped YAML — the family-no-clip e2e drives THIS mount.
const MERGESORT_DATA_JSON =
  '{"nodes":[{"id":"n0","label":"5 2 8 1 9 3"},{"id":"n1","label":"5 2 8","parent":"n0"},{"id":"n2","label":"5","parent":"n1"},{"id":"n3","label":"2 8","parent":"n1"},{"id":"n4","label":"2","parent":"n3"},{"id":"n5","label":"8","parent":"n3"},{"id":"n6","label":"1 9 3","parent":"n0"},{"id":"n7","label":"1","parent":"n6"},{"id":"n8","label":"9 3","parent":"n6"},{"id":"n9","label":"9","parent":"n8"},{"id":"n10","label":"3","parent":"n8"}],"steps":[{"highlight":["n0"],"deltas":[],"callout":"vectorul întreg — încă nesortat"},{"highlight":["n1","n6"],"deltas":[],"callout":"↓ ÎMPARTE — fiecare rând nou taie în jumătate"},{"highlight":["n2","n3","n7","n8"],"deltas":[],"callout":"↓ ÎMPARTE — fiecare rând nou taie în jumătate"},{"highlight":["n2","n4","n5","n7","n9","n10"],"deltas":[],"callout":"↓ ÎMPARTE — fiecare rând nou taie în jumătate"},{"highlight":["n2","n4","n5","n7","n9","n10"],"deltas":[],"callout":"✋ numără nivelurile: 3 = log₂(6) — de-aici vine „log n\\""},{"highlight":["n2","n3","n7","n8"],"deltas":[{"node":"n3","label":"2 8"},{"node":"n8","label":"3 9"}],"callout":"↑ INTERCLASEAZĂ — atinge toate cele 6 elemente pe nivel → O(n)"},{"highlight":["n1","n6"],"deltas":[{"node":"n1","label":"2 5 8"},{"node":"n6","label":"1 3 9"}],"callout":"↑ INTERCLASEAZĂ — atinge toate cele 6 elemente pe nivel → O(n)"},{"highlight":["n0"],"deltas":[{"node":"n0","label":"1 2 3 5 8 9"}],"callout":"3 niveluri × O(n) = O(n log n) ✓ → [1 2 3 5 8 9]"}]}';

// DEEP-TREE FIXTURE (gate-only, NOT lesson content) — a 7-level decomposition tree (deepest node at
// DEPTH 6, ≥5) authored to EXERCISE the GraphTreeFamily SVG_H height-ceiling path. With LEVEL_GAP=64 the
// deepest node sits at y ≈ 28 + 6·64 = 412, well past the old SVG_H=360 cap; before the height fix the
// figure-noclip-gate catches the deepest rows clipping the bottom viewBox edge (the gate doing its job).
// A right-leaning spine n0→n1→…→n6 with a sibling leaf at each level so it reads as a real tree, not a list.
const DEEP_TREE_DATA_JSON =
  '{"nodes":[' +
  '{"id":"n0","label":"L0"},' +
  '{"id":"n1","label":"L1","parent":"n0"},{"id":"s1","label":"a","parent":"n0"},' +
  '{"id":"n2","label":"L2","parent":"n1"},{"id":"s2","label":"b","parent":"n1"},' +
  '{"id":"n3","label":"L3","parent":"n2"},{"id":"s3","label":"c","parent":"n2"},' +
  '{"id":"n4","label":"L4","parent":"n3"},{"id":"s4","label":"d","parent":"n3"},' +
  '{"id":"n5","label":"L5","parent":"n4"},{"id":"s5","label":"e","parent":"n4"},' +
  '{"id":"n6","label":"L6","parent":"n5"},{"id":"s6","label":"f","parent":"n5"}' +
  '],"steps":[' +
  '{"highlight":["n0"],"deltas":[],"callout":"rădăcina — nivel 0"},' +
  '{"highlight":["n1","s1"],"deltas":[],"callout":"nivel 1"},' +
  '{"highlight":["n3","s3"],"deltas":[],"callout":"nivel 3 — coboară pe spină"},' +
  '{"highlight":["n6","s6"],"deltas":[],"callout":"nivel 6 — cel mai adânc nod (depth 6 ≥ 5)"}' +
  ']}';

// The viz-ps-normal-area-001 instance data_json (verbatim from content/PS/viz/viz-ps-normal-area-001.yaml).
// Keep byte-identical to the shipped YAML — the family-no-clip e2e drives THIS mount.
const NORMAL_AREA_DATA_JSON =
  '{"points":[{"x":-4,"y":0.0001},{"x":-3.8841,"y":0.0002},{"x":-3.7681,"y":0.0003},{"x":-3.6522,"y":0.0005},{"x":-3.5362,"y":0.0008},{"x":-3.4203,"y":0.0011},{"x":-3.3043,"y":0.0017},{"x":-3.1884,"y":0.0025},{"x":-3.0725,"y":0.0036},{"x":-2.9565,"y":0.005},{"x":-2.8406,"y":0.0071},{"x":-2.7246,"y":0.0097},{"x":-2.6087,"y":0.0133},{"x":-2.4928,"y":0.0178},{"x":-2.3768,"y":0.0237},{"x":-2.2609,"y":0.031},{"x":-2.1449,"y":0.04},{"x":-2.029,"y":0.0509},{"x":-1.913,"y":0.064},{"x":-1.7971,"y":0.0794},{"x":-1.6812,"y":0.0971},{"x":-1.5652,"y":0.1172},{"x":-1.4493,"y":0.1396},{"x":-1.3333,"y":0.164},{"x":-1.2174,"y":0.1901},{"x":-1.1014,"y":0.2175},{"x":-0.9855,"y":0.2455},{"x":-0.8696,"y":0.2733},{"x":-0.7536,"y":0.3003},{"x":-0.6377,"y":0.3255},{"x":-0.5217,"y":0.3482},{"x":-0.4058,"y":0.3674},{"x":-0.2899,"y":0.3825},{"x":-0.1739,"y":0.393},{"x":-0.058,"y":0.3983},{"x":0.058,"y":0.3983},{"x":0.1739,"y":0.393},{"x":0.2899,"y":0.3825},{"x":0.4058,"y":0.3674},{"x":0.5217,"y":0.3482},{"x":0.6377,"y":0.3255},{"x":0.7536,"y":0.3003},{"x":0.8696,"y":0.2733},{"x":0.9855,"y":0.2455},{"x":1.1014,"y":0.2175},{"x":1.2174,"y":0.1901},{"x":1.3333,"y":0.164},{"x":1.4493,"y":0.1396},{"x":1.5652,"y":0.1172},{"x":1.6812,"y":0.0971},{"x":1.7971,"y":0.0794},{"x":1.913,"y":0.064},{"x":2.029,"y":0.0509},{"x":2.1449,"y":0.04},{"x":2.2609,"y":0.031},{"x":2.3768,"y":0.0237},{"x":2.4928,"y":0.0178},{"x":2.6087,"y":0.0133},{"x":2.7246,"y":0.0097},{"x":2.8406,"y":0.0071},{"x":2.9565,"y":0.005},{"x":3.0725,"y":0.0036},{"x":3.1884,"y":0.0025},{"x":3.3043,"y":0.0017},{"x":3.4203,"y":0.0011},{"x":3.5362,"y":0.0008},{"x":3.6522,"y":0.0005},{"x":3.7681,"y":0.0003},{"x":3.8841,"y":0.0002},{"x":4,"y":0.0001}],"interval":{"a":-1,"b":1},"probability":0.68,"xLabel":"X","steps":[{"reveal":1,"callout":"densitatea de probabilitate — aria totală de sub curbă = 1"},{"reveal":2,"callout":"marcăm intervalul [a, b] = [-1, 1] pe axa X"},{"reveal":3,"callout":"aria de sub curbă între a și b = P(a ≤ X ≤ b)"},{"reveal":4,"callout":"P(-1 ≤ X ≤ 1) = 0.68 — aproximativ 68% din masă"}]}';

// The viz-pa-selectsort-001 instance data_json (verbatim from content/PA/viz/viz-pa-selectsort-001.yaml).
// Keep byte-identical to the shipped YAML — the family-no-clip e2e drives THIS mount.
const SELECTSORT_DATA_JSON =
  '{"values":[5,3,8,1,6],"steps":[{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":0,"min":0,"phase":"scan","callout":"Runda i=0: presupunem că minimul este a[0]=5."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":1,"min":1,"phase":"scan","callout":"Scanăm j=1: a[1]=3 este mai mic — noul minim este la indicele 1."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":2,"min":1,"phase":"scan","callout":"Scanăm j=2: a[2]=8 ≥ minimul curent a[1]=3 — minimul rămâne la 1."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":3,"min":3,"phase":"scan","callout":"Scanăm j=3: a[3]=1 este mai mic — noul minim este la indicele 3."},{"array":[5,3,8,1,6],"sortedCount":0,"i":0,"j":4,"min":3,"phase":"scan","callout":"Scanăm j=4: a[4]=6 ≥ minimul curent a[3]=1 — minimul rămâne la 3."},{"array":[1,3,8,5,6],"sortedCount":1,"i":0,"j":4,"min":3,"phase":"swap","callout":"Schimbăm a[0] cu a[3]: minimul 1 ajunge la poziția 0. Stânga lui 0 este sortată."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":1,"min":1,"phase":"scan","callout":"Runda i=1: presupunem că minimul este a[1]=3."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":2,"min":1,"phase":"scan","callout":"Scanăm j=2: a[2]=8 ≥ minimul curent a[1]=3 — minimul rămâne la 1."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":3,"min":1,"phase":"scan","callout":"Scanăm j=3: a[3]=5 ≥ minimul curent a[1]=3 — minimul rămâne la 1."},{"array":[1,3,8,5,6],"sortedCount":1,"i":1,"j":4,"min":1,"phase":"scan","callout":"Scanăm j=4: a[4]=6 ≥ minimul curent a[1]=3 — minimul rămâne la 1."},{"array":[1,3,8,5,6],"sortedCount":2,"i":1,"j":4,"min":1,"phase":"swap","callout":"Minimul a[1]=3 este deja la poziția 1 — poziția 1 este fixată."},{"array":[1,3,8,5,6],"sortedCount":2,"i":2,"j":2,"min":2,"phase":"scan","callout":"Runda i=2: presupunem că minimul este a[2]=8."},{"array":[1,3,8,5,6],"sortedCount":2,"i":2,"j":3,"min":3,"phase":"scan","callout":"Scanăm j=3: a[3]=5 este mai mic — noul minim este la indicele 3."},{"array":[1,3,8,5,6],"sortedCount":2,"i":2,"j":4,"min":3,"phase":"scan","callout":"Scanăm j=4: a[4]=6 ≥ minimul curent a[3]=5 — minimul rămâne la 3."},{"array":[1,3,5,8,6],"sortedCount":3,"i":2,"j":4,"min":3,"phase":"swap","callout":"Schimbăm a[2] cu a[3]: minimul 5 ajunge la poziția 2. Stânga lui 2 este sortată."},{"array":[1,3,5,8,6],"sortedCount":3,"i":3,"j":3,"min":3,"phase":"scan","callout":"Runda i=3: presupunem că minimul este a[3]=8."},{"array":[1,3,5,8,6],"sortedCount":3,"i":3,"j":4,"min":4,"phase":"scan","callout":"Scanăm j=4: a[4]=6 este mai mic — noul minim este la indicele 4."},{"array":[1,3,5,6,8],"sortedCount":4,"i":3,"j":4,"min":4,"phase":"swap","callout":"Schimbăm a[3] cu a[4]: minimul 6 ajunge la poziția 3. Stânga lui 3 este sortată."}]}';

// The viz-pa-dpfib-001 instance data_json (verbatim from content/PA/viz/viz-pa-dpfib-001.yaml).
// Keep byte-identical to the shipped YAML — the matrix-grid no-clip e2e drives THIS mount.
const DPFIB_DATA_JSON =
  '{"rows":1,"cols":6,"kind":"dp-fill","seed":{"n":5},"colHeaders":["dp[0]","dp[1]","dp[2]","dp[3]","dp[4]","dp[5]"],"steps":[{"writes":[{"row":0,"col":0,"value":"0"}],"fills":[{"row":0,"col":0}],"pivot":{"row":0,"col":0},"callout":"dp[0] = 0 (caz de bază)"},{"writes":[{"row":0,"col":1,"value":"1"}],"fills":[{"row":0,"col":1}],"pivot":{"row":0,"col":1},"callout":"dp[1] = 1 (caz de bază)"},{"writes":[{"row":0,"col":2,"value":"1"}],"fills":[{"row":0,"col":2}],"pivot":{"row":0,"col":2},"rowOp":"dp[2]=dp[1]+dp[0]","callout":"dp[2] = dp[1]+dp[0] = 1"},{"writes":[{"row":0,"col":3,"value":"2"}],"fills":[{"row":0,"col":3}],"pivot":{"row":0,"col":3},"rowOp":"dp[3]=dp[2]+dp[1]","callout":"dp[3] = dp[2]+dp[1] = 2"},{"writes":[{"row":0,"col":4,"value":"3"}],"fills":[{"row":0,"col":4}],"pivot":{"row":0,"col":4},"rowOp":"dp[4]=dp[3]+dp[2]","callout":"dp[4] = dp[3]+dp[2] = 3"},{"writes":[{"row":0,"col":5,"value":"5"}],"fills":[{"row":0,"col":5}],"pivot":{"row":0,"col":5},"rowOp":"dp[5]=dp[4]+dp[3]","callout":"dp[5] = dp[4]+dp[3] = 5 ✓ — fiecare celulă calculată o singură dată (O(n))"}]}';

// The viz-alo-gauss-001 instance data_json (verbatim from content/ALO/viz/viz-alo-gauss-001.yaml).
// Keep byte-identical to the shipped YAML — the matrix-grid (gauss) no-clip e2e drives THIS mount.
// The flagship matrix instance: 3×4 augmented matrix, partial-pivoting row-swap, rowOp rail + long callouts.
const GAUSS_DATA_JSON =
  '{"rows":3,"cols":4,"kind":"gauss-elim","seed":{"matrix":[[1,-1,3,2],[3,-3,1,-1],[1,1,0,3]]},"colHeaders":["x1","x2","x3","| b"],"rowHeaders":["E1","E2","E3"],"steps":[{"writes":[{"row":0,"col":0,"value":"1"},{"row":0,"col":1,"value":"-1"},{"row":0,"col":2,"value":"3"},{"row":0,"col":3,"value":"2"},{"row":1,"col":0,"value":"3"},{"row":1,"col":1,"value":"-3"},{"row":1,"col":2,"value":"1"},{"row":1,"col":3,"value":"-1"},{"row":2,"col":0,"value":"1"},{"row":2,"col":1,"value":"1"},{"row":2,"col":2,"value":"0"},{"row":2,"col":3,"value":"3"}],"fills":[{"row":0,"col":0},{"row":0,"col":1},{"row":0,"col":2},{"row":0,"col":3},{"row":1,"col":0},{"row":1,"col":1},{"row":1,"col":2},{"row":1,"col":3},{"row":2,"col":0},{"row":2,"col":1},{"row":2,"col":2},{"row":2,"col":3}],"pivot":null,"callout":"Matricea extinsă a sistemului. Scopul: o aducem la formă superior triunghiulară."},{"writes":[],"fills":[],"pivot":{"row":0,"col":0},"rowOp":"pivot a11 = 1","callout":"Pasul 1: pivotul este a11 = 1. Eliminăm x1 din liniile de sub el."},{"writes":[{"row":1,"col":0,"value":"0"},{"row":1,"col":1,"value":"0"},{"row":1,"col":2,"value":"-8"},{"row":1,"col":3,"value":"-7"}],"fills":[],"pivot":{"row":0,"col":0},"rowOp":"E2 ← E2 + (-3)·E1","callout":"f21 = -3/1 = -3. E2 ← E2 + (-3)·E1 ⇒ (0  0  -8 | -7)."},{"writes":[{"row":2,"col":0,"value":"0"},{"row":2,"col":1,"value":"2"},{"row":2,"col":2,"value":"-3"},{"row":2,"col":3,"value":"1"}],"fills":[],"pivot":{"row":0,"col":0},"rowOp":"E3 ← E3 + (-1)·E1","callout":"f31 = -1/1 = -1. E3 ← E3 + (-1)·E1 ⇒ (0  2  -3 | 1)."},{"writes":[{"row":1,"col":1,"value":"2"},{"row":1,"col":2,"value":"-3"},{"row":1,"col":3,"value":"1"},{"row":2,"col":1,"value":"0"},{"row":2,"col":2,"value":"-8"},{"row":2,"col":3,"value":"-7"}],"fills":[],"pivot":{"row":1,"col":1},"rowOp":"schimbă E2 ↔ E3","callout":"Pasul 2: pivotul a22 = 0! Pivotare parțială: interschimbăm E2 cu E3."},{"writes":[],"fills":[],"pivot":{"row":1,"col":1},"rowOp":"pivot a22 = 2","callout":"După schimb, noul pivot este a22 = 2 (nenul). Eliminăm x2 din liniile de sub el."},{"writes":[],"fills":[],"pivot":{"row":1,"col":1},"rowOp":"E3 ← E3 + 0·E2","callout":"f32 = -0/2 = 0: E3 e deja zero pe coloana x2. Sistem superior triunghiular ✓."}]}';

// The viz-alo-gauss-002 instance data_json (verbatim from content/ALO/viz/viz-alo-gauss-002.yaml).
// Fresh integer-clean 3×4 system [[1,2,2,3],[2,5,7,9],[1,3,6,7]] → upper-triangular, NO pivot swap
// (the happy-path counterpart to gauss-001's partial-pivoting case). Authored correct-by-construction
// from the gaussElimReference oracle; trace-match harness GREEN, seeded-defect drill RED.
const GAUSS2_DATA_JSON =
  '{"rows":3,"cols":4,"kind":"gauss-elim","seed":{"matrix":[[1,2,2,3],[2,5,7,9],[1,3,6,7]]},"colHeaders":["x1","x2","x3","| b"],"rowHeaders":["E1","E2","E3"],"steps":[{"writes":[{"row":0,"col":0,"value":"1"},{"row":0,"col":1,"value":"2"},{"row":0,"col":2,"value":"2"},{"row":0,"col":3,"value":"3"},{"row":1,"col":0,"value":"2"},{"row":1,"col":1,"value":"5"},{"row":1,"col":2,"value":"7"},{"row":1,"col":3,"value":"9"},{"row":2,"col":0,"value":"1"},{"row":2,"col":1,"value":"3"},{"row":2,"col":2,"value":"6"},{"row":2,"col":3,"value":"7"}],"fills":[{"row":0,"col":0},{"row":0,"col":1},{"row":0,"col":2},{"row":0,"col":3},{"row":1,"col":0},{"row":1,"col":1},{"row":1,"col":2},{"row":1,"col":3},{"row":2,"col":0},{"row":2,"col":1},{"row":2,"col":2},{"row":2,"col":3}],"pivot":null,"callout":"Matricea extinsă a sistemului. Scopul: o aducem la formă superior triunghiulară prin eliminare gaussiană."},{"writes":[],"fills":[],"pivot":{"row":0,"col":0},"rowOp":"pivot a11 = 1","callout":"Pasul 1: pivotul este a11 = 1. Eliminăm x1 din liniile de sub el."},{"writes":[{"row":1,"col":0,"value":"0"},{"row":1,"col":1,"value":"1"},{"row":1,"col":2,"value":"3"},{"row":1,"col":3,"value":"3"}],"fills":[],"pivot":{"row":0,"col":0},"rowOp":"E2 ← E2 + (-2)·E1","callout":"f21 = -2/1 = -2. E2 ← E2 + (-2)·E1 ⇒ (0  1  3 | 3)."},{"writes":[{"row":2,"col":0,"value":"0"},{"row":2,"col":1,"value":"1"},{"row":2,"col":2,"value":"4"},{"row":2,"col":3,"value":"4"}],"fills":[],"pivot":{"row":0,"col":0},"rowOp":"E3 ← E3 + (-1)·E1","callout":"f31 = -1/1 = -1. E3 ← E3 + (-1)·E1 ⇒ (0  1  4 | 4)."},{"writes":[],"fills":[],"pivot":{"row":1,"col":1},"rowOp":"pivot a22 = 1","callout":"Pasul 2: pivotul a22 = 1 (nenul, fără pivotare). Eliminăm x2 din linia de sub el."},{"writes":[{"row":2,"col":1,"value":"0"},{"row":2,"col":2,"value":"1"},{"row":2,"col":3,"value":"1"}],"fills":[],"pivot":{"row":1,"col":1},"rowOp":"E3 ← E3 + (-1)·E2","callout":"f32 = -1/1 = -1. E3 ← E3 + (-1)·E2 ⇒ (0  0  1 | 1). Sistem superior triunghiular ✓."}]}';

// The viz-poo-animals-001 instance data_json (verbatim from
// tutor-web/src/components/viz/families/__tests__/fixtures/class-diagram-animals.yaml).
// Family 7 (class-diagram, AMENDMENT 2026-06-17). NOT trace-based — its floor is the structure-
// isomorphism gate, not the trace-match harness. Keep byte-identical to the fixture YAML.
const CLASS_DIAGRAM_DATA_JSON =
  '{"classes":[{"id":"animal","name":"Animal","stereotype":"abstract","fields":[{"name":"name","type":"String","vis":"#"}],"methods":[{"name":"makeSound","ret":"void","vis":"+"}]},{"id":"dog","name":"Dog","fields":[],"methods":[{"name":"makeSound","ret":"void","vis":"+"},{"name":"fetch","ret":"void","vis":"+"}]},{"id":"cat","name":"Cat","fields":[],"methods":[{"name":"makeSound","ret":"void","vis":"+"}]},{"id":"owner","name":"Owner","fields":[{"name":"name","type":"String","vis":"+"}],"methods":[]}],"edges":[{"from":"dog","to":"animal","kind":"inheritance"},{"from":"cat","to":"animal","kind":"inheritance"},{"from":"owner","to":"animal","kind":"association","toMult":"1..*"}]}';

export function VizDemoPage() {
  const [numLineMu, setNumLineMu] = useState(5);
  return (
    <div
      data-testid="viz-demo-page"
      style={{
        background: PAPER,
        minHeight: "100vh",
        padding: 32,
        fontFamily: FONT_FAMILY,
        color: INK,
      }}
    >
      <header style={{ marginBottom: 32 }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 900 }}>
          ⭐ VIZ DEMO GALLERY
        </h1>
        <p style={{ margin: "4px 0 0", fontSize: 12, opacity: 0.7 }}>
          Brutalist mono · viz-foundation-demo branch · NOT a production drill flow
        </p>
      </header>

      {/* ── DARK LESSON-SURFACE VERIFICATION (Plan: premium-dark figures) ──
          Mounts the lesson families with variant="dark" + the float layout inside the lectie dark
          stage, exactly as a lesson reveal beat does (via LessonFigureShell). This is the eyeball
          surface for "figure floats DARK, no white box" — graph-tree (mergesort), matrix-grid (dp +
          gauss), chart-dist (normal area), seq-array (selection sort). NOT lesson content. */}
      <section
        data-testid="viz-demo-dark-figures"
        style={{ marginBottom: 56, background: "#0e0e0e", border: "3px solid #fde047", padding: 24 }}
      >
        <h2 style={{ ...headingStyle, color: "#fde047" }}>★ DARK LESSON-SURFACE FIGURES (variant="dark", floating)</h2>
        <p style={{ ...subheadingStyle, color: "#9a9a9a" }}>
          The four lesson families rendered DARK + FLOATING on the lectie stage (no white box). Same geometry,
          same data-* stamps, same trace — only paint + chrome change. This is what a reveal beat shows.
        </p>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(420px, 1fr))", gap: 28 }}>
          <div data-testid="dark-fig-graph-tree" style={{ background: "#0e0e0e" }}>
            <div style={{ color: "#fde047", fontSize: 11, letterSpacing: "0.1em", marginBottom: 8, fontWeight: 700 }}>
              graph-tree · mergesort
            </div>
            <LessonFigureShell familyId="graph-tree" instanceId="viz-pa-mergesort-001" dataJson={MERGESORT_DATA_JSON} language="ro" />
          </div>
          <div data-testid="dark-fig-seq-array" style={{ background: "#0e0e0e" }}>
            <div style={{ color: "#fde047", fontSize: 11, letterSpacing: "0.1em", marginBottom: 8, fontWeight: 700 }}>
              seq-array · selection sort
            </div>
            <LessonFigureShell familyId="seq-array" instanceId="viz-pa-selectsort-001" dataJson={SELECTSORT_DATA_JSON} language="ro" />
          </div>
          <div data-testid="dark-fig-matrix-grid" style={{ background: "#0e0e0e" }}>
            <div style={{ color: "#fde047", fontSize: 11, letterSpacing: "0.1em", marginBottom: 8, fontWeight: 700 }}>
              matrix-grid · dp fib (mg-root)
            </div>
            <LessonFigureShell familyId="matrix-grid" instanceId="viz-pa-dpfib-001" dataJson={DPFIB_DATA_JSON} language="ro" />
          </div>
          <div data-testid="dark-fig-matrix-grid-gauss" className="lesson-figure-dark" style={{ background: "#0e0e0e", width: "100%" }}>
            <div style={{ color: "#fde047", fontSize: 11, letterSpacing: "0.1em", marginBottom: 8, fontWeight: 700 }}>
              matrix-grid · gauss (mgg-root)
            </div>
            <MatrixGridFamily
              instanceId="viz-alo-gauss-001"
              dataJson={GAUSS_DATA_JSON}
              language="ro"
              testIdPrefix="mgg"
              variant="dark"
              layout={{ canvasBg: "#0e0e0e", controls: "bottom", maxWidth: 760 }}
            />
          </div>
          <div data-testid="dark-fig-chart-dist" style={{ background: "#0e0e0e" }}>
            <div style={{ color: "#fde047", fontSize: 11, letterSpacing: "0.1em", marginBottom: 8, fontWeight: 700 }}>
              chart-dist · normal area
            </div>
            <LessonFigureShell familyId="chart-dist" instanceId="viz-ps-normal-area-001" dataJson={NORMAL_AREA_DATA_JSON} language="ro" />
          </div>
          <div data-testid="dark-fig-class-diagram" style={{ background: "#0e0e0e" }}>
            <div style={{ color: "#fde047", fontSize: 11, letterSpacing: "0.1em", marginBottom: 8, fontWeight: 700 }}>
              class-diagram · POO inheritance (family 7)
            </div>
            <LessonFigureShell familyId="class-diagram" instanceId="viz-poo-animals-001" dataJson={CLASS_DIAGRAM_DATA_JSON} language="ro" />
          </div>
        </div>
      </section>

      <section data-testid="viz-demo-graph-tree" style={tileStyle}>
        <h2 style={headingStyle}>PA · MergeSort graph-tree family (viz-pa-mergesort-001)</h2>
        <p style={subheadingStyle}>
          Family verification vehicle (NOT lesson content, §0.6 #1). d3-hierarchy layout · 8 steps · no-clip by construction.
        </p>
        <GraphTreeFamily instanceId="viz-pa-mergesort-001" dataJson={MERGESORT_DATA_JSON} language="ro" />
      </section>

      <section data-testid="viz-demo-graph-tree-deep" style={tileStyle}>
        <h2 style={headingStyle}>PA · DEEP graph-tree fixture (depth 6) ⭐ gate fixture</h2>
        <p style={subheadingStyle}>
          Gate-only fixture (NOT lesson content) — a 7-level tree whose deepest node is at DEPTH 6 (≥5), authored
          to exercise the GraphTreeFamily height-ceiling path. The figure-noclip-gate steps every frame and asserts
          the deepest rows stay inside the hugged viewBox. Paints data-testid="graph-tree-root".
        </p>
        <GraphTreeFamily instanceId="viz-pa-deeptree-fixture-001" dataJson={DEEP_TREE_DATA_JSON} language="ro" />
      </section>

      <section data-testid="viz-demo-chart-dist" style={tileStyle}>
        <h2 style={headingStyle}>PS · Normal-area chart-dist family (viz-ps-normal-area-001)</h2>
        <p style={subheadingStyle}>
          Family verification vehicle (NOT lesson content). Measured-label no-clip layout · 4-beat reveal (curbă → interval → arie → P) · paints data-testid="chart-dist-root".
        </p>
        <ChartDistributionFamily instanceId="viz-ps-normal-area-001" dataJson={NORMAL_AREA_DATA_JSON} language="ro" />
      </section>

      <section data-testid="viz-demo-seq-array" style={tileStyle}>
        <h2 style={headingStyle}>PA · Selection-sort seq-array family (viz-pa-selectsort-001)</h2>
        <p style={subheadingStyle}>
          Family verification vehicle (NOT lesson content). Selection sort on [5,3,8,1,6] · pointers i/j/min · sorted-prefix recolor · 18 steps · element-anchored callouts · paints data-testid="seq-array-root".
        </p>
        <SequenceArrayFamily instanceId="viz-pa-selectsort-001" dataJson={SELECTSORT_DATA_JSON} language="ro" />
      </section>

      <section data-testid="viz-demo-matrix-grid" style={tileStyle}>
        <h2 style={headingStyle}>PA · DP fill-table matrix-grid family (viz-pa-dpfib-001)</h2>
        <p style={subheadingStyle}>
          Family verification vehicle (NOT lesson content). DP fib(5) table · left→right fill · pivot-anchored callouts · per-cell trace-match · paints data-testid="mg-root".
        </p>
        <MatrixGridFamily instanceId="viz-pa-dpfib-001" dataJson={DPFIB_DATA_JSON} language="ro" />
      </section>

      <section data-testid="viz-demo-matrix-grid-gauss" style={tileStyle}>
        <h2 style={headingStyle}>ALO · Gaussian-elimination matrix-grid family (viz-alo-gauss-001)</h2>
        <p style={subheadingStyle}>
          Family verification vehicle (NOT lesson content). 3×4 augmented matrix · forward elimination + partial-pivoting row-swap (a22=0 ⇒ E2↔E3) · rowOp rail · pivot-anchored callouts · per-cell trace-match vs the Gauss oracle · paints data-testid="mgg-root".
        </p>
        <MatrixGridFamily instanceId="viz-alo-gauss-001" dataJson={GAUSS_DATA_JSON} language="ro" testIdPrefix="mgg" />
      </section>

      <section id="gauss2" data-testid="viz-demo-matrix-grid-gauss2" style={{ ...tileStyle, scrollMarginTop: 16 }}>
        <h2 style={headingStyle}>ALO · Gaussian-elimination matrix-grid family (viz-alo-gauss-002) ⭐ NEW</h2>
        <p style={subheadingStyle}>
          Fresh 3×4 system [[1,2,2,3],[2,5,7,9],[1,3,6,7]] · integer-clean forward elimination · NO pivot swap (happy path) ·
          authored correct-by-construction from the Gauss oracle · trace-match harness GREEN, seeded-defect drill RED · paints data-testid="mgg2-root".
        </p>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(420px, 1fr))", gap: 28 }}>
          <div className="lesson-figure-dark" style={{ background: "#0e0e0e", padding: 16, width: "100%" }}>
            <div style={{ color: "#fde047", fontSize: 11, letterSpacing: "0.1em", marginBottom: 8, fontWeight: 700 }}>
              dark lesson-surface (what a reveal beat shows)
            </div>
            <MatrixGridFamily
              instanceId="viz-alo-gauss-002"
              dataJson={GAUSS2_DATA_JSON}
              language="ro"
              testIdPrefix="mgg2"
              variant="dark"
              layout={{ canvasBg: "#0e0e0e", controls: "bottom", maxWidth: 760 }}
            />
          </div>
          <div>
            <div style={{ fontSize: 11, letterSpacing: "0.1em", marginBottom: 8, fontWeight: 700, opacity: 0.7 }}>
              light gallery skin (e2e / trace baseline)
            </div>
            <MatrixGridFamily instanceId="viz-alo-gauss-002" dataJson={GAUSS2_DATA_JSON} language="ro" testIdPrefix="mgg2light" />
          </div>
        </div>
      </section>

      <section data-testid="viz-demo-class-diagram" style={tileStyle}>
        <h2 style={headingStyle}>POO · Class-diagram family (viz-poo-animals-001) ⭐ family 7</h2>
        <p style={subheadingStyle}>
          Static-structure UML (NOT a trace, AMENDMENT 2026-06-17). Abstract Animal ← Dog, Cat (inheritance) · Owner → Animal (association 1..*) · no-clip by construction (measured labels) · structure-isomorphism gate, not trace-match · paints data-testid="cd-root".
        </p>
        <ClassDiagramFamily instanceId="viz-poo-animals-001" dataJson={CLASS_DIAGRAM_DATA_JSON} language="ro" />
      </section>

      <section data-testid="viz-demo-matrix" style={tileStyle}>
        <h2 style={headingStyle}>ALO-3 · Eigenvector demo (MatrixTransform)</h2>
        <p style={subheadingStyle}>
          Standalone primitive (not yet on Shell). Scrubber + prediction gate + voice cache.
        </p>
        <MatrixTransform
          target={[
            [2, 1],
            [1, 2],
          ]}
          eigenvectors={[
            { v: [1 / Math.sqrt(2), 1 / Math.sqrt(2)], lambda: 3 },
            { v: [1 / Math.sqrt(2), -1 / Math.sqrt(2)], lambda: 1 },
          ]}
          w={[0.8, 0.3]}
          testRayCount={8}
          predictionGates={new Map([[0, {
            question:
              "Which colored ray stays in the same direction after applying M?",
            answers: [
              { label: "gray test ray", isCorrect: false },
              { label: "yellow eigenvector", isCorrect: true },
              { label: "black w (non-eig)", isCorrect: false },
            ],
          }]])}
        />
      </section>

      <section data-testid="viz-demo-algo-stepper-smoke" style={tileStyle}>
        <h2 style={headingStyle}>AlgoStepperShell smoke — counter</h2>
        <p style={subheadingStyle}>
          Subclass demo · 8 frames · scrubber + keyboard + Space + R + share + voice (no audio map)
        </p>
        <AlgoStepperShellSmoke />
      </section>

      <section data-testid="viz-demo-numline" style={tileStyle}>
        <h2 style={headingStyle}>NumLineDirect (PS basis)</h2>
        <p style={subheadingStyle}>
          Drag μ marker · 7 sample ticks · retrofit theme · drag-RAF flow
        </p>
        <NumLineDirect
          data={[1, 3, 4, 5, 6, 8, 11]}
          mu={numLineMu}
          onMu={setNumLineMu}
        />
      </section>

      <section data-testid="viz-demo-sumplot" style={tileStyle}>
        <h2 style={headingStyle}>SumPlotTracker (PS basis)</h2>
        <p style={subheadingStyle}>
          Σ|xᵢ − μ| curve · marker tracks μ · brutalist mono
        </p>
        <SumPlotTracker
          data={[1, 3, 4, 5, 6, 8, 11]}
          mu={numLineMu}
        />
      </section>

      <section data-testid="viz-demo-slope" style={tileStyle}>
        <h2 style={headingStyle}>SlopeCounter (PS basis)</h2>
        <p style={subheadingStyle}>
          Left/right count chips · diff readout · brutalist hard edges
        </p>
        <SlopeCounter data={[1, 3, 4, 5, 6, 8, 11]} mu={numLineMu} />
      </section>

      <section data-testid="viz-demo-sigma" style={tileStyle}>
        <h2 style={headingStyle}>SigmaStackedBar (PS basis)</h2>
        <p style={subheadingStyle}>
          Stacked deviations · SVG · ink segments + hairline seams · ACCENT on focus
        </p>
        <SigmaStackedBar data={[1, 3, 4, 5, 6, 8, 11]} mu={numLineMu} />
      </section>

      <section data-testid="viz-demo-compare-frames" style={tileStyle}>
        <h2 style={headingStyle}>CompareFrames (PS basis)</h2>
        <p style={subheadingStyle}>
          Mean vs median estimator · animate-frames button cycles modes · visx scatter + line
        </p>
        <CompareFrames data={[1, 3, 4, 5, 6, 8, 11]} />
      </section>

      <section data-testid="viz-demo-pa1-recursion" style={tileStyle}>
        <h2 style={headingStyle}>PA-1 · Recursion tree (fib 5)</h2>
        <p style={subheadingStyle}>
          Call stack + recursion tree side-by-side. Step through fib(5) execution. Yellow = current call.
        </p>
        <RecursionTree />
      </section>

      <section data-testid="viz-demo-pa3-dp-wasted" style={tileStyle}>
        <h2 style={headingStyle}>PA-3 · DP wasted-work ⭐ world-first</h2>
        <p style={subheadingStyle}>
          Naïve recursion vs DP, side-by-side. Watch duplicate subproblems shade darker as wasted work accumulates.
        </p>
        <DPWastedWork />
      </section>

      <section data-testid="viz-demo-so4-pagetable" style={tileStyle}>
        <h2 style={headingStyle}>SO-4 · Page table walk + TLB + page fault + COW ⭐</h2>
        <p style={subheadingStyle}>
          Virtual address → TLB → page table → physical memory. Includes hit, miss, page fault, fork+COW.
        </p>
        <PageTableWalk />
      </section>

      <section data-testid="viz-demo-ps2-bayes" style={tileStyle}>
        <h2 style={headingStyle}>PS-2 · Bayes tree ⭐</h2>
        <p style={subheadingStyle}>
          Disease/test scenario. Watch P(D|+) emerge from prior × likelihood / evidence.
        </p>
        <BayesTree />
      </section>

      <section data-testid="viz-demo-pa7-np-gadget" style={tileStyle}>
        <h2 style={headingStyle}>PA-7 · NP gadget (3-SAT → CLIQUE) ⭐ world-first</h2>
        <p style={subheadingStyle}>
          Bidirectional iff reduction. Watch φ ⟺ k-clique correspondence both ways.
        </p>
        <NPGadget />
      </section>

      <section data-testid="viz-demo-rc3-tcp-cwnd" style={tileStyle}>
        <h2 style={headingStyle}>RC-3 · TCP congestion window (Reno) ⭐ world-first</h2>
        <p style={subheadingStyle}>
          cwnd trajectory over 30 RTTs. Slow-start exponential → congestion avoidance linear → packet loss → fast recovery.
        </p>
        <TcpCwnd />
      </section>

      <section data-testid="viz-demo-rc6-tls-0rtt" style={tileStyle}>
        <h2 style={headingStyle}>RC-6 · TLS 1.3 0-RTT replay attack ⭐ world-first</h2>
        <p style={subheadingStyle}>
          Resumption + EarlyData → attacker replays the captured ClientHello → server processes twice. Mitigations: idempotent-only, single-use tickets, anti-replay window.
        </p>
        <Tls0RttReplay />
      </section>

      <section data-testid="viz-demo-poo1-poo4-vtable" style={tileStyle}>
        <h2 style={headingStyle}>POO-1/POO-4 · C++ vtable + shared_ptr cycle ⭐ world-first</h2>
        <p style={subheadingStyle}>
          Virtual dispatch through vptr/vtable. shared_ptr cycles cause memory leaks; weak_ptr breaks the cycle.
        </p>
        <CppVTable />
      </section>

      <section data-testid="viz-demo-so1-process-fsm" style={tileStyle}>
        <h2 style={headingStyle}>SO-1 · Process lifecycle FSM (with zombie)</h2>
        <p style={subheadingStyle}>
          fork/wait scenario. NEW → READY → RUNNING → WAITING → ZOMBIE → TERMINATED. PCB lingers until parent wait() reaps.
        </p>
        <ProcessFSM />
      </section>

      <section data-testid="viz-demo-so2-sched-gantt" style={tileStyle}>
        <h2 style={headingStyle}>SO-2 · Scheduler comparison (FCFS / SJF / SRTF / RR / MLFQ)</h2>
        <p style={subheadingStyle}>
          Same 4-job batch under 5 algorithms. Gantt timeline + avg turnaround per algo + MLFQ promotion/demotion.
        </p>
        <SchedulerGantt />
      </section>

      <section data-testid="viz-demo-so3-race-mutex" style={tileStyle}>
        <h2 style={headingStyle}>SO-3 · Race condition + mutex</h2>
        <p style={subheadingStyle}>
          Two threads racing to increment shared counter. Lost update without mutex (counter=1); mutex serializes critical section (counter=2).
        </p>
        <RaceMutex />
      </section>

      <section data-testid="viz-demo-rc1-osi-encap" style={tileStyle}>
        <h2 style={headingStyle}>RC-1 · OSI encapsulation + MTU fragmentation</h2>
        <p style={subheadingStyle}>
          1400B HTTP descends OSI stack, IP fragments at L3 against 800B MTU, reassembles at receiver. Headers attached + stripped layer by layer.
        </p>
        <OsiEncap />
      </section>

      <section data-testid="viz-demo-rc2-tcp-handshake" style={tileStyle}>
        <h2 style={headingStyle}>RC-2 · TCP 3-way handshake + SYN flood + SYN cookies</h2>
        <p style={subheadingStyle}>
          Normal SYN/SYN-ACK/ACK with ISN randomization. Attacker exhausts backlog via spoofed SYNs. SYN cookies defend without backlog state.
        </p>
        <TcpHandshake />
      </section>
    </div>
  );
}
