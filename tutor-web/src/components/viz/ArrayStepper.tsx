import { useMemo, type ReactNode } from "react";
import { AlgoStepperShell, type Frame, type ShellLayout } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "./theme";
import { motion } from "./motion-helpers";

/**
 * ArrayStepper — the verdict's "build next" (viz-coverage-verdict.md §5).
 * A parametric compare/swap bar stepper on the (now unboxed) AlgoStepperShell.
 * One surface covers sorting + two-pointer/window + stack-queue by feeding a
 * different `buildTrace`. This file demos the SORTING trace (bubble sort).
 * Proof that a NEW archetype builds on the discrete parametric pattern with
 * ZERO new libs — just motion + the existing shell.
 */
type BarState = "idle" | "compare" | "swap" | "sorted";
interface Bar { value: number; state: BarState }
interface SortState { bars: Bar[]; j: number; k: number; note: string }

function buildBubbleTrace(input: number[]): Frame<SortState>[] {
  const a = [...input];
  const n = a.length;
  const frames: Frame<SortState>[] = [];
  const snap = (j: number, k: number, swapping: boolean, sortedFrom: number, note: string) => {
    frames.push({
      state: {
        bars: a.map((value, idx) => ({
          value,
          state: idx >= sortedFrom ? "sorted" : idx === j || idx === k ? (swapping ? "swap" : "compare") : "idle",
        })),
        j, k, note,
      },
      aria: note,
    });
  };
  let sortedFrom = n;
  for (let i = 0; i < n - 1; i++) {
    for (let j = 0; j < n - 1 - i; j++) {
      snap(j, j + 1, false, sortedFrom, `Compare a[${j}]=${a[j]} vs a[${j + 1}]=${a[j + 1]}`);
      if (a[j] > a[j + 1]) {
        [a[j], a[j + 1]] = [a[j + 1], a[j]];
        snap(j, j + 1, true, sortedFrom, `Swap → ${a[j]}, ${a[j + 1]}`);
      }
    }
    sortedFrom = n - 1 - i; // this index is now finalized
  }
  // all sorted
  frames.push({
    state: { bars: a.map(value => ({ value, state: "sorted" })), j: -1, k: -1, note: "Sorted." },
    aria: "Array fully sorted.",
  });
  return frames;
}

// layout
const VB_W = 480, VB_H = 360;
const PAD_X = 30, BASE_Y = 300, MAX_H = 210;

function fillFor(s: BarState): string {
  if (s === "compare") return ACCENT;
  if (s === "swap") return ACCENT;
  if (s === "sorted") return INK;
  return "#fff";
}

function renderFrame(frame: Frame<SortState>): ReactNode {
  const { bars, note } = frame.state;
  const n = bars.length;
  const maxVal = Math.max(...bars.map(b => b.value), 1);
  const slot = (VB_W - 2 * PAD_X) / n;
  const bw = slot * 0.7;
  return (
    <>
      <text x={PAD_X} y={28} fontFamily={FONT_FAMILY} fontSize={12} fontWeight={700} fill={INK}>
        BUBBLE SORT
      </text>
      <text x={PAD_X} y={46} fontFamily={FONT_FAMILY} fontSize={11} fill={INK} opacity={0.7}>
        {note}
      </text>
      {bars.map((b, i) => {
        const h = (b.value / maxVal) * MAX_H;
        const x = PAD_X + i * slot + (slot - bw) / 2;
        const y = BASE_Y - h;
        return (
          <g key={i}>
            <motion.rect
              x={x}
              width={bw}
              initial={false}
              animate={{ y, height: h, fill: fillFor(b.state) }}
              transition={{ duration: 0.35, ease: "easeInOut" }}
              stroke={INK}
              strokeWidth={b.state === "compare" || b.state === "swap" ? 2.5 : 1}
            />
            <text
              x={x + bw / 2}
              y={BASE_Y + 16}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={11}
              fontWeight={700}
              fill={b.state === "sorted" ? INK : INK}
            >
              {b.value}
            </text>
            {(b.state === "compare" || b.state === "swap") && (
              <text x={x + bw / 2} y={BASE_Y + 32} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={13} fill={INK}>
                ▲
              </text>
            )}
          </g>
        );
      })}
    </>
  );
}

const DEFAULT = [5, 2, 8, 1, 9, 3, 6];

export function ArrayStepper({ values = DEFAULT, layout }: { values?: number[]; layout?: ShellLayout } = {}): ReactNode {
  const frames = useMemo(() => buildBubbleTrace(values), [values]);
  return (
    <AlgoStepperShell<SortState>
      title="Bubble sort — compare & swap"
      desc="Step through bubble sort. Yellow bars = being compared. Black bars = finalized in their sorted position."
      frames={frames}
      renderFrame={renderFrame}
      testIdPrefix="array-stepper"
      layout={layout}
    />
  );
}
