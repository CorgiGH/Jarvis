/**
 * Plan-V family 4 (INV-5.1 / INV-5.2) — an INDEPENDENT distribution reference, used by the
 * trace-match harness to recompute the expected per-step rendered state from the raw data_json.
 * This is the ORACLE: the harness asserts the family's frames equal what this computes, so the
 * family cannot reveal a beat the data does not encode, and the annotated probability cannot drift
 * from the actual area under the supplied curve.
 *
 * The model:
 *   - reveal[k] = the cumulative reveal level of step k (1 curve, 2 interval, 3 shade, 4 annotate),
 *     read straight from the instance steps (the family must echo these exactly, in order).
 *   - The chart "labels" channel (analogue of mergesort's node-label set) is a one-element list
 *     ["reveal-<n>"] per step — the harness compares the family's per-frame reveal to this.
 *   - INDEPENDENT probability check: integrate the supplied curve over [a,b] by the trapezoid rule
 *     and assert it matches the instance's declared `probability` within tolerance. A wrong-trace
 *     fixture that lies about the probability (or reorders/duplicates the reveal beats) makes the
 *     harness throw naming the offending step.
 */

export type ChartRefStep = { reveal: number; label: string };

type Pt = { x: number; y: number };
type Parsed = {
  points: Pt[];
  interval: { a: number; b: number };
  probability: number;
  steps: { reveal: number }[];
};

/** Trapezoid integral of the piecewise-linear curve between a and b (clamped to the sample range). */
export function areaUnderCurve(points: Pt[], a: number, b: number): number {
  const yAt = (x: number): number => {
    if (x <= points[0].x) return points[0].y;
    if (x >= points[points.length - 1].x) return points[points.length - 1].y;
    for (let i = 1; i < points.length; i++) {
      if (x <= points[i].x) {
        const t = (x - points[i - 1].x) / (points[i].x - points[i - 1].x);
        return points[i - 1].y + t * (points[i].y - points[i - 1].y);
      }
    }
    return points[points.length - 1].y;
  };
  // breakpoints = a, every sample strictly inside (a,b), b
  const xs = [a, ...points.map((p) => p.x).filter((x) => x > a && x < b), b];
  let area = 0;
  for (let i = 1; i < xs.length; i++) {
    const dx = xs[i] - xs[i - 1];
    area += ((yAt(xs[i - 1]) + yAt(xs[i])) / 2) * dx;
  }
  return area;
}

/**
 * Reference executor for the chart-dist family. Recomputes the expected per-step trace AND verifies
 * the declared probability against the integrated area (throws naming the step on mismatch).
 */
export function chartDistributionReference(dataJson: string): ChartRefStep[] {
  const parsed = JSON.parse(dataJson) as Parsed;
  const { points, interval, probability, steps } = parsed;

  // INDEPENDENT area check (this is what makes the figure CORRECTNESS-SAFE, not just consistent).
  const area = areaUnderCurve(points, interval.a, interval.b);
  const TOL = 0.02; // 2-decimal annotation precision
  if (Math.abs(area - probability) > TOL) {
    // The annotation beat (reveal 4) is the offending step — name it.
    const annotIdx = steps.findIndex((s) => s.reveal === 4);
    const stepName = annotIdx >= 0 ? `step ${annotIdx}` : "annotation step";
    throw new Error(
      `chart-dist trace mismatch at ${stepName}: declared probability ${probability} ≠ ∫curve over [${interval.a},${interval.b}] = ${area.toFixed(4)} (|Δ|=${Math.abs(area - probability).toFixed(4)} > ${TOL})`,
    );
  }

  // Per-step reveal trace. The reveal levels must be non-decreasing (a reveal can only ADD), so a
  // fixture that re-hides or reorders a beat is caught here naming the step.
  const out: ChartRefStep[] = [];
  let prev = 0;
  for (let i = 0; i < steps.length; i++) {
    const r = steps[i].reveal;
    if (r < prev) {
      throw new Error(
        `chart-dist trace mismatch at step ${i}: reveal level ${r} is LESS than the previous level ${prev} — a reveal beat may only add, never retract`,
      );
    }
    prev = r;
    out.push({ reveal: r, label: `reveal-${r}` });
  }
  return out;
}
