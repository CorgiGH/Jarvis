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
          predictionGate={{
            question:
              "Which colored ray stays in the same direction after applying M?",
            answers: [
              { label: "gray test ray", isCorrect: false },
              { label: "yellow eigenvector", isCorrect: true },
              { label: "black w (non-eig)", isCorrect: false },
            ],
          }}
          liveRegionId="viz-live"
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
          Stacked deviations · ink + accent + opacity alternation · hard edges
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
