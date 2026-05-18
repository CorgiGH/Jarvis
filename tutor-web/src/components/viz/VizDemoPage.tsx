import type { CSSProperties } from "react";
import { useState } from "react";
import { AlgoStepperShellSmoke } from "./AlgoStepperShellSmoke";
import { MatrixTransform } from "./MatrixTransform";
import { NumLineDirect } from "./NumLineDirect";
import { SlopeCounter } from "./SlopeCounter";
import { SumPlotTracker } from "./SumPlotTracker";
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
    </div>
  );
}
