import { useCallback, useRef } from "react";
import { PlotlyEmbed, PlotlyFrame } from "../PlotlyEmbed";

export interface CompareFramesProps { data: number[]; }

function buildFrames(data: number[]): PlotlyFrame[] {
  const sorted = [...data].sort((a, b) => a - b);
  const mean = data.reduce((a, b) => a + b, 0) / data.length;
  const median =
    sorted.length % 2 === 0
      ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2
      : sorted[Math.floor(sorted.length / 2)];

  const scatter = (est: number, label: string) => [
    {
      type: "scatter",
      mode: "markers",
      x: data.map((_, i) => i + 1),
      y: data,
      name: "data",
      marker: { color: "#64748b", size: 8 },
    },
    {
      type: "scatter",
      mode: "lines",
      x: [1, data.length],
      y: [est, est],
      name: label,
      line: { color: "#3b82f6", width: 2, dash: "dash" },
    },
  ];

  return [
    { name: "baseline", data: scatter(mean, "baseline mean") },
    { name: "mean", data: scatter(mean, `mean (${mean.toFixed(2)})`) },
    { name: "median", data: scatter(median, `median (${median.toFixed(2)})`) },
  ];
}

export function CompareFrames({ data }: CompareFramesProps) {
  const frames = buildFrames(data);
  const mean = data.reduce((a, b) => a + b, 0) / data.length;
  const containerRef = useRef<HTMLDivElement>(null);

  const handlePlay = useCallback(async () => {
    const mod = await import("plotly.js-dist-min");
    const Plotly = (mod as any).default ?? mod;
    if (typeof Plotly.animate !== "function") return;
    // In production, prefer the Plotly graph div (has _fullLayout); fall back
    // to the container so tests (which use a mock without .js-plotly-plot) can
    // still trigger the spy and validate arguments.
    const el =
      (containerRef.current?.querySelector(".js-plotly-plot") as Element | null) ??
      containerRef.current;
    if (!el) return;
    const prefersReduced =
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const dur = prefersReduced ? 0 : 600;
    await Plotly.animate(el, frames, {
      transition: { duration: dur, easing: "cubic-bezier(.4,0,.2,1)" },
      frame: { duration: dur },
    });
  }, [frames]);

  return (
    <div
      ref={containerRef}
      data-testid="compare-frames-plotly"
      style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}
    >
      <PlotlyEmbed
        indexLabel="FIG"
        figure={{
          data: [
            {
              type: "scatter",
              mode: "markers",
              x: data.map((_, i) => i + 1),
              y: data,
              name: "data",
              marker: { color: "#64748b", size: 8 },
            },
            {
              type: "scatter",
              mode: "lines",
              x: [1, data.length],
              y: [mean, mean],
              name: "mean",
              line: { color: "#3b82f6", width: 2, dash: "dash" },
            },
          ],
          layout: {
            title: { text: "Mean vs Median — frame morph" },
            showlegend: true,
            margin: { t: 20, b: 30, l: 40, r: 10 },
          },
        }}
      />
      <button
        data-testid="compare-frames-play"
        onClick={handlePlay}
        style={{
          alignSelf: "flex-start",
          padding: "0.35em 0.8em",
          border: "1.5px solid var(--color-accent, #3b82f6)",
          borderRadius: "4px",
          background: "transparent",
          color: "var(--color-accent, #3b82f6)",
          fontFamily: "var(--font-mono, monospace)",
          fontSize: "0.75rem",
          fontWeight: 700,
          cursor: "pointer",
        }}
      >
        ▶ ANIMATE FRAMES
      </button>
    </div>
  );
}
