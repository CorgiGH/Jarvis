import type { ReactNode } from "react";
import { scaleLinear } from "@visx/scale";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";
import {
  AnimatePresence,
  DrawPath,
  FadeText,
  PopIn,
  motion,
} from "./motion-helpers";

type Algo = "INTRO" | "FCFS" | "SJF" | "SRTF" | "RR" | "MLFQ" | "SUMMARY";
type GanttSegment = { job: string; tStart: number; tEnd: number };
type MlfqQueue = { level: number; jobs: string[] };
type MlfqEvent = {
  kind: "demote" | "promote";
  job: string;
  fromLevel: number;
  toLevel: number;
};
type SummaryBar = { algo: string; avgTurnaround: number };

type State = {
  step: number;
  algo: Algo;
  algoLabel: string;
  t: number;
  segments: GanttSegment[];
  mlfqQueues?: MlfqQueue[];
  mlfqEvent?: MlfqEvent;
  summaryBars?: SummaryBar[];
  showSummary: boolean;
  algoAvg?: number;
  message: string;
};

// ---- Fixed job batch -------------------------------------------------------
const JOBS = [
  { id: "J1", arrival: 0, burst: 8 },
  { id: "J2", arrival: 1, burst: 4 },
  { id: "J3", arrival: 2, burst: 9 },
  { id: "J4", arrival: 3, burst: 5 },
];

// Pre-computed schedule traces -------------------------------------------------
const FCFS_SEGMENTS: GanttSegment[] = [
  { job: "J1", tStart: 0, tEnd: 8 },
  { job: "J2", tStart: 8, tEnd: 12 },
  { job: "J3", tStart: 12, tEnd: 21 },
  { job: "J4", tStart: 21, tEnd: 26 },
];
const FCFS_AVG = (8 + 11 + 19 + 23) / 4; // 15.25

const SJF_SEGMENTS: GanttSegment[] = [
  { job: "J1", tStart: 0, tEnd: 8 },
  { job: "J2", tStart: 8, tEnd: 12 },
  { job: "J4", tStart: 12, tEnd: 17 },
  { job: "J3", tStart: 17, tEnd: 26 },
];
const SJF_AVG = (8 + 11 + 24 + 14) / 4; // 14.25

const SRTF_SEGMENTS: GanttSegment[] = [
  { job: "J1", tStart: 0, tEnd: 1 },
  { job: "J2", tStart: 1, tEnd: 5 },
  { job: "J4", tStart: 5, tEnd: 10 },
  { job: "J1", tStart: 10, tEnd: 17 },
  { job: "J3", tStart: 17, tEnd: 26 },
];
const SRTF_AVG = (17 + 4 + 24 + 7) / 4; // 13.0

const RR_SEGMENTS: GanttSegment[] = [
  { job: "J1", tStart: 0, tEnd: 2 },
  { job: "J2", tStart: 2, tEnd: 4 },
  { job: "J3", tStart: 4, tEnd: 6 },
  { job: "J1", tStart: 6, tEnd: 8 },
  { job: "J4", tStart: 8, tEnd: 10 },
  { job: "J2", tStart: 10, tEnd: 12 },
  { job: "J3", tStart: 12, tEnd: 14 },
  { job: "J1", tStart: 14, tEnd: 16 },
  { job: "J4", tStart: 16, tEnd: 18 },
  { job: "J3", tStart: 18, tEnd: 20 },
  { job: "J1", tStart: 20, tEnd: 22 },
  { job: "J4", tStart: 22, tEnd: 23 },
  { job: "J3", tStart: 23, tEnd: 26 },
];
const RR_AVG = (22 + 11 + 24 + 20) / 4; // 19.25

// MLFQ symbolic schedule — illustrative. Q0 q=1, Q1 q=2, Q2 FCFS, boost every 8.
const MLFQ_SEGMENTS: GanttSegment[] = [
  { job: "J1", tStart: 0, tEnd: 1 },
  { job: "J2", tStart: 1, tEnd: 2 },
  { job: "J3", tStart: 2, tEnd: 3 },
  { job: "J4", tStart: 3, tEnd: 4 },
  { job: "J1", tStart: 4, tEnd: 6 },
  { job: "J2", tStart: 6, tEnd: 8 },
  // priority boost at t=8 — everyone back to Q0
  { job: "J1", tStart: 8, tEnd: 9 },
  { job: "J2", tStart: 9, tEnd: 10 },
  { job: "J3", tStart: 10, tEnd: 11 },
  { job: "J4", tStart: 11, tEnd: 12 },
  { job: "J1", tStart: 12, tEnd: 14 },
  { job: "J2", tStart: 14, tEnd: 15 },
  // boost at t=16 (conceptually)
  { job: "J3", tStart: 15, tEnd: 17 },
  { job: "J4", tStart: 17, tEnd: 19 },
  { job: "J1", tStart: 19, tEnd: 21 },
  { job: "J3", tStart: 21, tEnd: 23 },
  { job: "J4", tStart: 23, tEnd: 24 },
  { job: "J3", tStart: 24, tEnd: 26 },
];
const MLFQ_AVG = 16.5; // symbolic

// ---- Frame builder ---------------------------------------------------------
function partialSegments(
  full: GanttSegment[],
  t: number
): GanttSegment[] {
  const out: GanttSegment[] = [];
  for (const s of full) {
    if (s.tStart >= t) break;
    out.push({ job: s.job, tStart: s.tStart, tEnd: Math.min(s.tEnd, t) });
  }
  return out;
}

function buildFrames(): Frame<State>[] {
  const frames: Frame<State>[] = [];

  const mk = (
    step: number,
    algo: Algo,
    algoLabel: string,
    t: number,
    segments: GanttSegment[],
    message: string,
    extra: Partial<State> = {}
  ): Frame<State> => ({
    state: {
      step,
      algo,
      algoLabel,
      t,
      segments: segments.map((s) => ({ ...s })),
      showSummary: false,
      message,
      ...extra,
    },
    aria: `${algoLabel}, time ${t}. ${message}`,
  });

  let step = 0;

  // PHASE 1 — INTRO (1 frame, F0)
  frames.push(
    mk(
      step++,
      "INTRO",
      "",
      0,
      [],
      "4 jobs to schedule. Compare FCFS, SJF, SRTF, RR, MLFQ on the same batch."
    )
  );

  // PHASE 2 — FCFS (5 frames, F1..F5)
  frames.push(
    mk(
      step++,
      "FCFS",
      "FCFS",
      0,
      partialSegments(FCFS_SEGMENTS, 0),
      "FCFS: First Come First Served. Order by arrival time. J1 starts at t=0."
    )
  );
  frames.push(
    mk(
      step++,
      "FCFS",
      "FCFS",
      4,
      partialSegments(FCFS_SEGMENTS, 4),
      "t=4: J1 still running (burst 8). No preemption — others wait."
    )
  );
  frames.push(
    mk(
      step++,
      "FCFS",
      "FCFS",
      8,
      partialSegments(FCFS_SEGMENTS, 8),
      "t=8: J1 done. J2 starts (arrived at t=1, waited 7 units)."
    )
  );
  frames.push(
    mk(
      step++,
      "FCFS",
      "FCFS",
      12,
      partialSegments(FCFS_SEGMENTS, 12),
      "t=12: J2 done. J3 starts. J3 has the longest burst (9) — convoy effect grows."
    )
  );
  frames.push(
    mk(
      step++,
      "FCFS",
      "FCFS",
      26,
      partialSegments(FCFS_SEGMENTS, 26),
      `FCFS done at t=26. Avg turnaround = ${FCFS_AVG}. Simple, but long jobs delay short ones.`,
      { algoAvg: FCFS_AVG }
    )
  );

  // PHASE 3 — SJF non-preemptive (5 frames, F6..F10)
  frames.push(
    mk(
      step++,
      "SJF",
      "SJF (non-preemptive)",
      0,
      partialSegments(SJF_SEGMENTS, 0),
      "SJF: pick the shortest job available. At t=0 only J1 is here — run it."
    )
  );
  frames.push(
    mk(
      step++,
      "SJF",
      "SJF (non-preemptive)",
      8,
      partialSegments(SJF_SEGMENTS, 8),
      "t=8: J1 done. Choose from J2(4), J3(9), J4(5). Pick J2 — shortest."
    )
  );
  frames.push(
    mk(
      step++,
      "SJF",
      "SJF (non-preemptive)",
      12,
      partialSegments(SJF_SEGMENTS, 12),
      "t=12: J2 done. Choose between J3(9), J4(5). Pick J4."
    )
  );
  frames.push(
    mk(
      step++,
      "SJF",
      "SJF (non-preemptive)",
      17,
      partialSegments(SJF_SEGMENTS, 17),
      "t=17: J4 done. Only J3 remains."
    )
  );
  frames.push(
    mk(
      step++,
      "SJF",
      "SJF (non-preemptive)",
      26,
      partialSegments(SJF_SEGMENTS, 26),
      `SJF done at t=26. Avg turnaround = ${SJF_AVG}. Optimal for avg wait — needs burst foreknowledge.`,
      { algoAvg: SJF_AVG }
    )
  );

  // PHASE 4 — SRTF preemptive (5 frames, F11..F15)
  frames.push(
    mk(
      step++,
      "SRTF",
      "SRTF (preemptive SJF)",
      1,
      partialSegments(SRTF_SEGMENTS, 1),
      "SRTF: pick shortest REMAINING. t=0 J1 runs; t=1 J2 arrives (rem 4 < J1 rem 7) — PREEMPT."
    )
  );
  frames.push(
    mk(
      step++,
      "SRTF",
      "SRTF (preemptive SJF)",
      3,
      partialSegments(SRTF_SEGMENTS, 3),
      "t=2 J3 arrives (9), t=3 J4 arrives (5). J2 still shortest (rem 2) — keep running."
    )
  );
  frames.push(
    mk(
      step++,
      "SRTF",
      "SRTF (preemptive SJF)",
      5,
      partialSegments(SRTF_SEGMENTS, 5),
      "t=5: J2 done. Remaining J1(7), J3(9), J4(5). Pick J4."
    )
  );
  frames.push(
    mk(
      step++,
      "SRTF",
      "SRTF (preemptive SJF)",
      10,
      partialSegments(SRTF_SEGMENTS, 10),
      "t=10: J4 done. Remaining J1(7), J3(9). Pick J1."
    )
  );
  frames.push(
    mk(
      step++,
      "SRTF",
      "SRTF (preemptive SJF)",
      26,
      partialSegments(SRTF_SEGMENTS, 26),
      `SRTF done. Avg turnaround = ${SRTF_AVG}. Provably optimal — but heavy on context switches.`,
      { algoAvg: SRTF_AVG }
    )
  );

  // PHASE 5 — Round Robin q=2 (6 frames, F16..F21)
  frames.push(
    mk(
      step++,
      "RR",
      "RR q=2",
      0,
      partialSegments(RR_SEGMENTS, 0),
      "Round Robin q=2: each job gets 2 time units, then go to back of queue."
    )
  );
  frames.push(
    mk(
      step++,
      "RR",
      "RR q=2",
      4,
      partialSegments(RR_SEGMENTS, 4),
      "t=0-2 J1, t=2-4 J2. Queue (FIFO): J3, J1, J4, J2 (J4 arrived at t=3)."
    )
  );
  frames.push(
    mk(
      step++,
      "RR",
      "RR q=2",
      10,
      partialSegments(RR_SEGMENTS, 10),
      "Rotation continues: J3, J1, J4 each get a quantum. Fair share."
    )
  );
  frames.push(
    mk(
      step++,
      "RR",
      "RR q=2",
      14,
      partialSegments(RR_SEGMENTS, 14),
      "t=12: J2 finishes (used 2 quanta = 4 units). t=14: J3 still has 5 units left."
    )
  );
  frames.push(
    mk(
      step++,
      "RR",
      "RR q=2",
      22,
      partialSegments(RR_SEGMENTS, 22),
      "t=22: J1 finishes. J3 and J4 still running. RR avoids starvation but adds context-switch cost."
    )
  );
  frames.push(
    mk(
      step++,
      "RR",
      "RR q=2",
      26,
      partialSegments(RR_SEGMENTS, 26),
      `RR done at t=26. Avg turnaround = ${RR_AVG}. Worst here — but fair when bursts are unknown.`,
      { algoAvg: RR_AVG }
    )
  );

  // PHASE 6 — MLFQ (5 frames, F22..F26)
  frames.push(
    mk(
      step++,
      "MLFQ",
      "MLFQ (Q0 q=1, Q1 q=2, Q2 FCFS)",
      0,
      partialSegments(MLFQ_SEGMENTS, 0),
      "MLFQ: 3 priority queues. New jobs enter Q0 (highest). Use 1q in Q0 → demote to Q1."
    )
  );
  frames.push(
    mk(
      step++,
      "MLFQ",
      "MLFQ (Q0 q=1, Q1 q=2, Q2 FCFS)",
      1,
      partialSegments(MLFQ_SEGMENTS, 1),
      "t=0-1: J1 ran 1 quantum in Q0 — demote to Q1.",
      {
        mlfqQueues: [
          { level: 0, jobs: [] },
          { level: 1, jobs: ["J1"] },
          { level: 2, jobs: [] },
        ],
        mlfqEvent: { kind: "demote", job: "J1", fromLevel: 0, toLevel: 1 },
      }
    )
  );
  frames.push(
    mk(
      step++,
      "MLFQ",
      "MLFQ (Q0 q=1, Q1 q=2, Q2 FCFS)",
      4,
      partialSegments(MLFQ_SEGMENTS, 4),
      "All four jobs ran 1q in Q0 then demoted to Q1. Q0 empty.",
      {
        mlfqQueues: [
          { level: 0, jobs: [] },
          { level: 1, jobs: ["J1", "J2", "J3", "J4"] },
          { level: 2, jobs: [] },
        ],
        mlfqEvent: { kind: "demote", job: "J4", fromLevel: 0, toLevel: 1 },
      }
    )
  );
  frames.push(
    mk(
      step++,
      "MLFQ",
      "MLFQ (Q0 q=1, Q1 q=2, Q2 FCFS)",
      8,
      partialSegments(MLFQ_SEGMENTS, 8),
      "t=8: PRIORITY BOOST — every 8 units all jobs promoted back to Q0. Prevents starvation.",
      {
        mlfqQueues: [
          { level: 0, jobs: ["J1", "J2", "J3", "J4"] },
          { level: 1, jobs: [] },
          { level: 2, jobs: [] },
        ],
        mlfqEvent: { kind: "promote", job: "J1", fromLevel: 1, toLevel: 0 },
      }
    )
  );
  frames.push(
    mk(
      step++,
      "MLFQ",
      "MLFQ (Q0 q=1, Q1 q=2, Q2 FCFS)",
      26,
      partialSegments(MLFQ_SEGMENTS, 26),
      `MLFQ done. Avg turnaround approx ${MLFQ_AVG}. Adaptive — no burst-time foreknowledge needed.`,
      {
        algoAvg: MLFQ_AVG,
        mlfqQueues: [
          { level: 0, jobs: [] },
          { level: 1, jobs: [] },
          { level: 2, jobs: [] },
        ],
      }
    )
  );

  // PHASE 7 — SUMMARY (1 frame, F27)
  frames.push(
    mk(
      step++,
      "SUMMARY",
      "COMPARISON",
      26,
      [],
      "SRTF wins on avg turnaround; FCFS is simplest; RR is fairest. MLFQ adapts without burst foreknowledge.",
      {
        showSummary: true,
        summaryBars: [
          { algo: "FCFS", avgTurnaround: FCFS_AVG },
          { algo: "SJF", avgTurnaround: SJF_AVG },
          { algo: "SRTF", avgTurnaround: SRTF_AVG },
          { algo: "RR", avgTurnaround: RR_AVG },
          { algo: "MLFQ", avgTurnaround: MLFQ_AVG },
        ],
      }
    )
  );

  return frames;
}

export const FRAMES = buildFrames();
export const FRAME_COUNT = FRAMES.length;

// ---- Layout constants ------------------------------------------------------
const GANTT_X = 10;
const GANTT_Y = 35;
const GANTT_W = 460;
const GANTT_H = 60;

const TABLE_X = 10;
const TABLE_Y = 100;
const TABLE_H = 100;

const QUEUE_X = 10;
const QUEUE_Y = 210;
const QUEUE_W = 460;
const QUEUE_H = 90;

const MSG_Y = 340;

const T_MAX = 28;

// ---- Job fill opacity tier -------------------------------------------------
function jobOpacity(job: string): number {
  if (job === "J1") return 0.85;
  if (job === "J2") return 0.6;
  if (job === "J3") return 0.4;
  return 0.25; // J4
}

function jobTextFill(job: string): string {
  // For darker fills, the text inside should be PAPER so it's readable.
  if (job === "J1" || job === "J2") return PAPER;
  return INK;
}

// ---- Render ----------------------------------------------------------------
function renderFrame(frame: Frame<State>): ReactNode {
  const {
    algo,
    algoLabel,
    t,
    segments,
    mlfqQueues,
    mlfqEvent,
    showSummary,
    summaryBars,
    algoAvg,
    message,
  } = frame.state;

  const xScale = scaleLinear<number>({
    domain: [0, T_MAX],
    range: [GANTT_X + 18, GANTT_X + GANTT_W - 8],
  });

  const tickValues = [0, 4, 8, 12, 16, 20, 24, 28];

  return (
    <>
      <defs>
        <marker
          id="gantt-arrow-accent"
          viewBox="0 0 10 10"
          refX="8"
          refY="5"
          markerWidth="6"
          markerHeight="6"
          orient="auto-start-reverse"
        >
          <path d="M0,0 L10,5 L0,10 z" fill={ACCENT} stroke={INK} strokeWidth={0.5} />
        </marker>
      </defs>

      {/* TOP — Algorithm name (cross-fades on phase change) */}
      <FadeText
        x={240}
        y={22}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={14}
        fontWeight={700}
        fill={INK}
      >
        {algoLabel || "SCHEDULER COMPARISON"}
      </FadeText>

      {/* ROW 1 — Gantt timeline */}
      <text
        x={GANTT_X}
        y={GANTT_Y - 4}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        GANTT
      </text>
      <rect
        x={GANTT_X}
        y={GANTT_Y}
        width={GANTT_W}
        height={GANTT_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />

      {/* Job row inside Gantt */}
      <g>
        <AnimatePresence>
          {segments.map((s) => {
            const x = xScale(s.tStart);
            const x2 = xScale(s.tEnd);
            const w = Math.max(2, x2 - x);
            const segKey = `${algo}-${s.job}-${s.tStart}-${s.tEnd}`;
            return (
              <PopIn key={segKey} durationMs={300}>
                <rect
                  x={x}
                  y={GANTT_Y + 8}
                  width={w}
                  height={26}
                  fill={INK}
                  opacity={jobOpacity(s.job)}
                  stroke={INK}
                  strokeWidth={1}
                />
                {w > 14 && (
                  <text
                    x={x + w / 2}
                    y={GANTT_Y + 25}
                    textAnchor="middle"
                    fontFamily={FONT_FAMILY}
                    fontSize={9}
                    fontWeight={700}
                    fill={jobTextFill(s.job)}
                  >
                    {s.job}
                  </text>
                )}
              </PopIn>
            );
          })}
        </AnimatePresence>
      </g>

      {/* Time-axis ticks */}
      {tickValues.map((v) => (
        <g key={`tick-${v}`}>
          <line
            x1={xScale(v)}
            y1={GANTT_Y + GANTT_H - 12}
            x2={xScale(v)}
            y2={GANTT_Y + GANTT_H - 8}
            stroke={INK}
            strokeWidth={1}
            opacity={0.6}
          />
          <text
            x={xScale(v)}
            y={GANTT_Y + GANTT_H - 1}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={8}
            fill={INK}
            opacity={0.7}
          >
            {v}
          </text>
        </g>
      ))}

      {/* Current-time tick — slides smoothly */}
      {algo !== "INTRO" && algo !== "SUMMARY" && (
        <motion.line
          initial={false}
          animate={{ x1: xScale(t), x2: xScale(t) }}
          transition={{ type: "tween", duration: 0.5, ease: "easeInOut" }}
          y1={GANTT_Y + 4}
          y2={GANTT_Y + GANTT_H - 14}
          stroke={ACCENT}
          strokeWidth={2}
        />
      )}

      {/* Avg-turnaround inline tag for current algo */}
      {algoAvg !== undefined && (
        <PopIn key={`avg-${algo}`}>
          <rect
            x={GANTT_X + GANTT_W - 110}
            y={GANTT_Y - 14}
            width={108}
            height={12}
            fill={ACCENT}
            stroke={INK}
            strokeWidth={1}
          />
          <text
            x={GANTT_X + GANTT_W - 56}
            y={GANTT_Y - 5}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            fill={INK}
          >
            AVG TAT {algoAvg.toFixed(2)}
          </text>
        </PopIn>
      )}

      {/* ROW 2 — Jobs table (always visible) */}
      {/* Label moved up to y=TABLE_Y - 18 (=82) so it clears the Gantt tick row at y=94 */}
      <text
        x={TABLE_X}
        y={TABLE_Y - 18}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        JOB BATCH
      </text>
      <rect
        x={TABLE_X}
        y={TABLE_Y}
        width={GANTT_W}
        height={TABLE_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      {/* Header row */}
      <line
        x1={TABLE_X}
        y1={TABLE_Y + 18}
        x2={TABLE_X + GANTT_W}
        y2={TABLE_Y + 18}
        stroke={INK}
        strokeWidth={1}
      />
      {(["PID", "ARRIVAL", "BURST", "FILL"] as const).map((label, i) => (
        <text
          key={label}
          x={TABLE_X + 30 + i * 110}
          y={TABLE_Y + 13}
          textAnchor="middle"
          fontFamily={FONT_FAMILY}
          fontSize={9}
          fontWeight={700}
          fill={INK}
          opacity={0.7}
        >
          {label}
        </text>
      ))}
      {/* Job rows */}
      {JOBS.map((j, i) => {
        const rowY = TABLE_Y + 18 + (i + 1) * 18;
        return (
          <g key={j.id}>
            <text
              x={TABLE_X + 30}
              y={rowY}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
            >
              {j.id}
            </text>
            <text
              x={TABLE_X + 140}
              y={rowY}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fill={INK}
            >
              {j.arrival}
            </text>
            <text
              x={TABLE_X + 250}
              y={rowY}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fill={INK}
            >
              {j.burst}
            </text>
            <rect
              x={TABLE_X + 310}
              y={rowY - 9}
              width={50}
              height={12}
              fill={INK}
              opacity={jobOpacity(j.id)}
              stroke={INK}
              strokeWidth={1}
            />
          </g>
        );
      })}

      {/* ROW 3 — MLFQ queues OR summary bars OR placeholder */}
      {algo === "MLFQ" && mlfqQueues && (
        <MlfqPanel queues={mlfqQueues} event={mlfqEvent} />
      )}
      {showSummary && summaryBars && <SummaryPanel bars={summaryBars} />}
      {algo !== "MLFQ" && !showSummary && (
        <g>
          <text
            x={QUEUE_X}
            y={QUEUE_Y - 4}
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            fill={INK}
            opacity={0.7}
          >
            LEGEND
          </text>
          <rect
            x={QUEUE_X}
            y={QUEUE_Y}
            width={QUEUE_W}
            height={QUEUE_H}
            fill={PAPER}
            stroke={INK}
            strokeWidth={1}
          />
          {/* Legend rows */}
          {[
            "FCFS — order by arrival; no preemption.",
            "SJF — pick shortest burst; non-preemptive.",
            "SRTF — preempt when shorter remaining arrives.",
            "RR q=2 — fixed quantum, round-robin queue.",
            "MLFQ — multi-level feedback; demote on quantum-use.",
          ].map((line, i) => (
            <g key={i}>
              <rect
                x={QUEUE_X + 8}
                y={QUEUE_Y + 8 + i * 16}
                width={10}
                height={10}
                fill={INK}
                opacity={0.4 + i * 0.1}
                stroke={INK}
                strokeWidth={0.5}
              />
              <text
                x={QUEUE_X + 24}
                y={QUEUE_Y + 17 + i * 16}
                fontFamily={FONT_FAMILY}
                fontSize={9}
                fill={INK}
              >
                {line}
              </text>
            </g>
          ))}
        </g>
      )}

      {/* Footer message — cross-fade on change */}
      <rect
        x={10}
        y={312}
        width={460}
        height={36}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <FooterMessage message={message} />
    </>
  );
}

// ---- MLFQ panel ------------------------------------------------------------
function MlfqPanel({
  queues,
  event,
}: {
  queues: MlfqQueue[];
  event?: MlfqEvent;
}): ReactNode {
  const rowHeight = 22;
  const rowGap = 4;
  const labelW = 32;
  const innerX = QUEUE_X + labelW + 8;
  const innerW = QUEUE_W - labelW - 16;
  const yOf = (level: number) =>
    QUEUE_Y + 10 + level * (rowHeight + rowGap);

  // Build arrow path for demote/promote event between queue levels
  let arrow: ReactNode = null;
  if (event) {
    const fromY = yOf(event.fromLevel) + rowHeight / 2;
    const toY = yOf(event.toLevel) + rowHeight / 2;
    const arrowX = QUEUE_X + QUEUE_W - 28;
    const d = `M ${arrowX} ${fromY} L ${arrowX} ${toY}`;
    arrow = (
      <DrawPath
        key={`mlfq-arrow-${event.kind}-${event.job}-${event.fromLevel}-${event.toLevel}`}
        d={d}
        stroke={ACCENT}
        strokeWidth={2}
        fill="none"
        markerEnd="url(#gantt-arrow-accent)"
        drawOn={false}
      />
    );
  }

  return (
    <g>
      <text
        x={QUEUE_X}
        y={QUEUE_Y - 4}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        MLFQ QUEUES
      </text>
      <rect
        x={QUEUE_X}
        y={QUEUE_Y}
        width={QUEUE_W}
        height={QUEUE_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      {queues.map((q) => {
        const y = yOf(q.level);
        return (
          <g key={`q-${q.level}`}>
            <text
              x={QUEUE_X + 8}
              y={y + rowHeight - 7}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
            >
              Q{q.level}
            </text>
            <rect
              x={innerX}
              y={y}
              width={innerW - 36}
              height={rowHeight}
              fill={PAPER}
              stroke={INK}
              strokeWidth={1}
              opacity={0.6}
            />
            {/* Job tokens inside the row */}
            <AnimatePresence>
              {q.jobs.map((job, idx) => (
                <PopIn key={`tok-${q.level}-${job}-${idx}`} durationMs={300}>
                  <rect
                    x={innerX + 6 + idx * 32}
                    y={y + 4}
                    width={26}
                    height={rowHeight - 8}
                    fill={INK}
                    opacity={jobOpacity(job)}
                    stroke={INK}
                    strokeWidth={1}
                  />
                  <text
                    x={innerX + 6 + idx * 32 + 13}
                    y={y + rowHeight - 8}
                    textAnchor="middle"
                    fontFamily={FONT_FAMILY}
                    fontSize={9}
                    fontWeight={700}
                    fill={jobTextFill(job)}
                  >
                    {job}
                  </text>
                </PopIn>
              ))}
            </AnimatePresence>
          </g>
        );
      })}
      {/* Quantum labels on the right */}
      <text
        x={QUEUE_X + QUEUE_W - 8}
        y={yOf(0) + 14}
        textAnchor="end"
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        opacity={0.6}
      >
        q=1
      </text>
      <text
        x={QUEUE_X + QUEUE_W - 8}
        y={yOf(1) + 14}
        textAnchor="end"
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        opacity={0.6}
      >
        q=2
      </text>
      <text
        x={QUEUE_X + QUEUE_W - 8}
        y={yOf(2) + 14}
        textAnchor="end"
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        opacity={0.6}
      >
        FCFS
      </text>
      {arrow}
      {event && (
        <text
          x={QUEUE_X + QUEUE_W - 28}
          y={yOf(Math.max(event.fromLevel, event.toLevel)) + 4}
          textAnchor="end"
          fontFamily={FONT_FAMILY}
          fontSize={8}
          fontWeight={700}
          fill={INK}
        >
          {event.kind === "demote" ? "DEMOTE" : "BOOST"}
        </text>
      )}
    </g>
  );
}

// ---- Summary panel ---------------------------------------------------------
function SummaryPanel({ bars }: { bars: SummaryBar[] }): ReactNode {
  const innerX = QUEUE_X + 60;
  const innerW = QUEUE_W - 120;
  const rowH = 14;
  const rowGap = 2;
  const maxVal = Math.max(...bars.map((b) => b.avgTurnaround));
  const widthScale = (v: number) => (v / maxVal) * innerW;
  const minIdx = bars.reduce(
    (best, b, i) =>
      b.avgTurnaround < bars[best].avgTurnaround ? i : best,
    0
  );

  return (
    <g>
      <text
        x={QUEUE_X}
        y={QUEUE_Y - 4}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        AVG TURNAROUND — LOWER IS BETTER
      </text>
      <rect
        x={QUEUE_X}
        y={QUEUE_Y}
        width={QUEUE_W}
        height={QUEUE_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      {bars.map((b, i) => {
        const y = QUEUE_Y + 10 + i * (rowH + rowGap);
        const w = widthScale(b.avgTurnaround);
        const winner = i === minIdx;
        return (
          <g key={`bar-${b.algo}`}>
            <text
              x={QUEUE_X + 8}
              y={y + rowH - 3}
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fontWeight={700}
              fill={INK}
            >
              {b.algo}
            </text>
            <rect
              x={innerX}
              y={y}
              width={w}
              height={rowH}
              fill={winner ? ACCENT : INK}
              opacity={winner ? 1 : 0.6}
              stroke={INK}
              strokeWidth={1}
            />
            <text
              x={innerX + w + 6}
              y={y + rowH - 3}
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fontWeight={700}
              fill={INK}
            >
              {b.avgTurnaround.toFixed(2)}
            </text>
            {winner && (
              <text
                x={QUEUE_X + QUEUE_W - 8}
                y={y + rowH - 3}
                textAnchor="end"
                fontFamily={FONT_FAMILY}
                fontSize={9}
                fontWeight={700}
                fill={INK}
              >
                BEST
              </text>
            )}
          </g>
        );
      })}
    </g>
  );
}

// ---- Footer ----------------------------------------------------------------
function FooterMessage({ message }: { message: string }) {
  const maxCharsPerLine = 78;
  const words = message.split(" ");
  let line1 = "";
  let line2 = "";
  let onLine2 = false;
  for (const w of words) {
    if (!onLine2) {
      if (!line1.length || (line1.length + w.length + 1) <= maxCharsPerLine) {
        line1 = line1 ? `${line1} ${w}` : w;
        continue;
      }
      onLine2 = true;
    }
    if (!line2.length || (line2.length + w.length + 1) <= maxCharsPerLine) {
      line2 = line2 ? `${line2} ${w}` : w;
    } else {
      line2 = line2 + "…";
      break;
    }
  }
  // Append a single trailing space to line1 when line2 exists so concatenated
  // textContent reads correctly ("foo bar" not "foobar") for a11y / TTS.
  // Trailing whitespace at a wrap boundary is visually invisible.
  if (line2) {
    line1 = line1 + " ";
  }
  return (
    <AnimatePresence initial={false}>
      <motion.text
        key={message}
        x={16}
        y={MSG_Y - 12}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.3 }}
      >
        <tspan x={16} dy={0}>
          {line1}
        </tspan>
        {line2 && (
          <tspan x={16} dy={12}>
            {line2}
          </tspan>
        )}
      </motion.text>
    </AnimatePresence>
  );
}

export function SchedulerGantt(): ReactNode {
  return (
    <AlgoStepperShell<State>
      title="SO-2 · Scheduler comparison (FCFS / SJF / SRTF / RR / MLFQ)"
      desc="Same job batch run through five CPU schedulers. Each phase resets the Gantt timeline and ends with the average turnaround. Final frame shows side-by-side comparison."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="sched-gantt"
    />
  );
}
