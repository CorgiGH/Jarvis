import { useMemo, useState } from "react";
import { Group } from "@visx/group";
import { scaleLinear } from "@visx/scale";
import { Line } from "@visx/shape";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

export interface CompareFramesProps { data: number[]; }

const SVG_W = 480;
const SVG_H = 220;
const PAD_L = 48;
const PAD_R = 16;
const PAD_T = 16;
const PAD_B = 32;

type Mode = "baseline" | "mean" | "median";

function computeMean(data: number[]): number {
  if (data.length === 0) return 0;
  return data.reduce((a, b) => a + b, 0) / data.length;
}

function computeMedian(data: number[]): number {
  if (data.length === 0) return 0;
  const s = [...data].sort((a, b) => a - b);
  const mid = Math.floor(s.length / 2);
  return s.length % 2 === 0 ? (s[mid - 1] + s[mid]) / 2 : s[mid];
}

export function CompareFrames({ data }: CompareFramesProps) {
  const mean = useMemo(() => computeMean(data), [data]);
  const median = useMemo(() => computeMedian(data), [data]);
  const [mode, setMode] = useState<Mode>("baseline");

  const xMax = data.length + 1;
  const yMin = Math.min(...data, mean, median) - 1;
  const yMax = Math.max(...data, mean, median) + 1;

  const xScale = scaleLinear<number>({ domain: [0, xMax], range: [PAD_L, SVG_W - PAD_R] });
  const yScale = scaleLinear<number>({ domain: [yMin, yMax], range: [SVG_H - PAD_B, PAD_T] });

  const estimate = mode === "median" ? median : mean;
  const label =
    mode === "baseline"
      ? "baseline (mean)"
      : mode === "mean"
        ? `mean ${mean.toFixed(2)}`
        : `median ${median.toFixed(2)}`;

  return (
    <div
      data-testid="compare-frames-plotly"
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 8,
        fontFamily: FONT_FAMILY,
        color: INK,
      }}
    >
      <svg
        width={SVG_W}
        height={SVG_H}
        viewBox={`0 0 ${SVG_W} ${SVG_H}`}
        role="img"
        aria-label="Compare estimators — mean vs median"
        style={{ background: PAPER, border: `2px solid ${INK}` }}
      >
        <Group>
          <Line
            from={{ x: PAD_L, y: SVG_H - PAD_B }}
            to={{ x: SVG_W - PAD_R, y: SVG_H - PAD_B }}
            stroke={INK}
            strokeWidth={1}
            opacity={0.3}
          />
          <Line
            from={{ x: PAD_L, y: PAD_T }}
            to={{ x: PAD_L, y: SVG_H - PAD_B }}
            stroke={INK}
            strokeWidth={1}
            opacity={0.3}
          />
          {data.map((v, i) => (
            <circle
              key={i}
              data-testid="compare-data-point"
              cx={xScale(i + 1)}
              cy={yScale(v)}
              r={4}
              fill={INK}
              stroke={INK}
              strokeWidth={1}
            />
          ))}
          <Line
            data-testid="compare-estimate-line"
            from={{ x: PAD_L, y: yScale(estimate) }}
            to={{ x: SVG_W - PAD_R, y: yScale(estimate) }}
            stroke={ACCENT}
            strokeWidth={3}
            style={{ transition: "all 600ms cubic-bezier(0.4, 0, 0.2, 1)" }}
          />
          <text
            x={SVG_W - PAD_R - 8}
            y={yScale(estimate) - 6}
            textAnchor="end"
            fontFamily={FONT_FAMILY}
            fontSize={11}
            fontWeight={700}
            fill={INK}
          >
            {label}
          </text>
        </Group>
      </svg>
      <div style={{ display: "flex", gap: 4 }}>
        <button
          data-testid="compare-frames-play"
          onClick={() => {
            setMode((m) =>
              m === "baseline" ? "mean" : m === "mean" ? "median" : "baseline"
            );
          }}
          style={{
            background: PAPER,
            color: INK,
            border: `2px solid ${INK}`,
            padding: "0.4em 0.8em",
            fontFamily: FONT_FAMILY,
            fontSize: 11,
            fontWeight: 700,
            letterSpacing: "0.06em",
            textTransform: "uppercase",
            cursor: "pointer",
          }}
        >
          ▶ animate frames
        </button>
        <span
          data-testid="compare-mode-readout"
          style={{
            alignSelf: "center",
            fontSize: 10,
            opacity: 0.7,
            letterSpacing: "0.04em",
          }}
        >
          mode: {mode}
        </span>
      </div>
    </div>
  );
}
