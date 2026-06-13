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

// The viz-ps-normal-area-001 instance data_json (verbatim from content/PS/viz/viz-ps-normal-area-001.yaml).
// Keep byte-identical to the shipped YAML — the family-no-clip e2e drives THIS mount.
const NORMAL_AREA_DATA_JSON =
  '{"points":[{"x":-4,"y":0.0001},{"x":-3.8841,"y":0.0002},{"x":-3.7681,"y":0.0003},{"x":-3.6522,"y":0.0005},{"x":-3.5362,"y":0.0008},{"x":-3.4203,"y":0.0011},{"x":-3.3043,"y":0.0017},{"x":-3.1884,"y":0.0025},{"x":-3.0725,"y":0.0036},{"x":-2.9565,"y":0.005},{"x":-2.8406,"y":0.0071},{"x":-2.7246,"y":0.0097},{"x":-2.6087,"y":0.0133},{"x":-2.4928,"y":0.0178},{"x":-2.3768,"y":0.0237},{"x":-2.2609,"y":0.031},{"x":-2.1449,"y":0.04},{"x":-2.029,"y":0.0509},{"x":-1.913,"y":0.064},{"x":-1.7971,"y":0.0794},{"x":-1.6812,"y":0.0971},{"x":-1.5652,"y":0.1172},{"x":-1.4493,"y":0.1396},{"x":-1.3333,"y":0.164},{"x":-1.2174,"y":0.1901},{"x":-1.1014,"y":0.2175},{"x":-0.9855,"y":0.2455},{"x":-0.8696,"y":0.2733},{"x":-0.7536,"y":0.3003},{"x":-0.6377,"y":0.3255},{"x":-0.5217,"y":0.3482},{"x":-0.4058,"y":0.3674},{"x":-0.2899,"y":0.3825},{"x":-0.1739,"y":0.393},{"x":-0.058,"y":0.3983},{"x":0.058,"y":0.3983},{"x":0.1739,"y":0.393},{"x":0.2899,"y":0.3825},{"x":0.4058,"y":0.3674},{"x":0.5217,"y":0.3482},{"x":0.6377,"y":0.3255},{"x":0.7536,"y":0.3003},{"x":0.8696,"y":0.2733},{"x":0.9855,"y":0.2455},{"x":1.1014,"y":0.2175},{"x":1.2174,"y":0.1901},{"x":1.3333,"y":0.164},{"x":1.4493,"y":0.1396},{"x":1.5652,"y":0.1172},{"x":1.6812,"y":0.0971},{"x":1.7971,"y":0.0794},{"x":1.913,"y":0.064},{"x":2.029,"y":0.0509},{"x":2.1449,"y":0.04},{"x":2.2609,"y":0.031},{"x":2.3768,"y":0.0237},{"x":2.4928,"y":0.0178},{"x":2.6087,"y":0.0133},{"x":2.7246,"y":0.0097},{"x":2.8406,"y":0.0071},{"x":2.9565,"y":0.005},{"x":3.0725,"y":0.0036},{"x":3.1884,"y":0.0025},{"x":3.3043,"y":0.0017},{"x":3.4203,"y":0.0011},{"x":3.5362,"y":0.0008},{"x":3.6522,"y":0.0005},{"x":3.7681,"y":0.0003},{"x":3.8841,"y":0.0002},{"x":4,"y":0.0001}],"interval":{"a":-1,"b":1},"probability":0.68,"xLabel":"X","steps":[{"reveal":1,"callout":"densitatea de probabilitate — aria totală de sub curbă = 1"},{"reveal":2,"callout":"marcăm intervalul [a, b] = [-1, 1] pe axa X"},{"reveal":3,"callout":"aria de sub curbă între a și b = P(a ≤ X ≤ b)"},{"reveal":4,"callout":"P(-1 ≤ X ≤ 1) = 0.68 — aproximativ 68% din masă"}]}';

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

      <section data-testid="viz-demo-graph-tree" style={tileStyle}>
        <h2 style={headingStyle}>PA · MergeSort graph-tree family (viz-pa-mergesort-001)</h2>
        <p style={subheadingStyle}>
          Family verification vehicle (NOT lesson content, §0.6 #1). d3-hierarchy layout · 8 steps · no-clip by construction.
        </p>
        <GraphTreeFamily instanceId="viz-pa-mergesort-001" dataJson={MERGESORT_DATA_JSON} language="ro" />
      </section>

      <section data-testid="viz-demo-chart-dist" style={tileStyle}>
        <h2 style={headingStyle}>PS · Normal-area chart-dist family (viz-ps-normal-area-001)</h2>
        <p style={subheadingStyle}>
          Family verification vehicle (NOT lesson content). Measured-label no-clip layout · 4-beat reveal (curbă → interval → arie → P) · paints data-testid="chart-dist-root".
        </p>
        <ChartDistributionFamily instanceId="viz-ps-normal-area-001" dataJson={NORMAL_AREA_DATA_JSON} language="ro" />
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
