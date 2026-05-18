import type { ReactNode } from "react";
import { Group } from "@visx/group";
import { scaleLinear } from "@visx/scale";
import { Line, LinePath } from "@visx/shape";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

// Suppress unused import lint for Group (kept for pattern consistency)
void Group;

type Mode = "SLOW_START" | "CONG_AVOIDANCE" | "FAST_RECOVERY";
type CwndPoint = { rtt: number; cwnd: number; mode: Mode; event?: string };
type TCPState = {
  step: number;
  rtt: number;
  cwnd: number;
  ssthresh: number;
  mode: Mode;
  history: CwndPoint[];
  message: string;
};

function buildFrames(): Frame<TCPState>[] {
  const frames: Frame<TCPState>[] = [];
  const history: CwndPoint[] = [];
  let cwnd = 1;
  let ssthresh = 16;
  let mode: Mode = "SLOW_START";

  const MAX_RTT = 29;
  const LOSS_RTTS = new Set([12, 24]);

  for (let rtt = 0; rtt <= MAX_RTT; rtt++) {
    let event: string | undefined;
    if (rtt === 0) {
      event = "INIT — cwnd=1, ssthresh=16";
    } else if (LOSS_RTTS.has(rtt)) {
      ssthresh = Math.floor(cwnd / 2);
      cwnd = ssthresh + 3; // Reno fast-recovery
      mode = "CONG_AVOIDANCE";
      event = `PACKET LOSS! ssthresh = cwnd/2 = ${ssthresh}, cwnd = ssthresh+3 = ${cwnd}`;
    } else if (mode === "SLOW_START") {
      cwnd = cwnd * 2;
      if (cwnd >= ssthresh) {
        cwnd = ssthresh;
        mode = "CONG_AVOIDANCE";
        event = `cwnd reached ssthresh — switch to congestion avoidance`;
      }
    } else if (mode === "CONG_AVOIDANCE") {
      cwnd = cwnd + 1;
    }

    history.push({ rtt, cwnd, mode, event });

    const msg = event
      ? event
      : mode === "SLOW_START"
      ? `RTT ${rtt}: slow-start, cwnd doubled to ${cwnd}`
      : `RTT ${rtt}: cong-avoidance, cwnd += 1 to ${cwnd}`;

    frames.push({
      state: {
        step: rtt,
        rtt,
        cwnd,
        ssthresh,
        mode,
        history: history.map((h) => ({ ...h })),
        message: msg,
      },
      aria: `RTT ${rtt}, cwnd ${cwnd}, mode ${mode}.`,
    });
  }

  // Final summary frame
  frames.push({
    state: {
      step: MAX_RTT + 1,
      rtt: MAX_RTT,
      cwnd,
      ssthresh,
      mode,
      history: history.map((h) => ({ ...h })),
      message:
        "TCP Reno: AIMD pattern. CUBIC: more aggressive, cwnd grows as cubic function of time since last loss.",
    },
    aria: "Summary: AIMD pattern over time.",
  });

  return frames;
}

export const FRAMES = buildFrames();
export const FRAME_COUNT = FRAMES.length;

const SVG_W = 480;
const SVG_H = 360;
void SVG_W;
void SVG_H;

// Plot pane
const PLOT_X = 50;
const PLOT_Y = 30;
const PLOT_W = 280;
const PLOT_H = 200;

const MAX_CWND = 30;
const MAX_RTT_PLOT = 30;

// State pane
const STATE_X = 350;
const STATE_Y = 30;
const STATE_W = 120;

const MSG_Y = 340;

function modeColor(mode: Mode): string {
  if (mode === "SLOW_START") return ACCENT;
  return INK;
}

function renderFrame(frame: Frame<TCPState>): ReactNode {
  const { rtt, cwnd, ssthresh, mode, history, message } = frame.state;

  const xScale = scaleLinear<number>({
    domain: [0, MAX_RTT_PLOT],
    range: [PLOT_X, PLOT_X + PLOT_W],
  });
  const yScale = scaleLinear<number>({
    domain: [0, MAX_CWND],
    range: [PLOT_Y + PLOT_H, PLOT_Y],
  });

  // Build segments split by mode change for color coding
  const segments: CwndPoint[][] = [];
  let cur: CwndPoint[] = [];
  history.forEach((p, i) => {
    if (i === 0 || history[i - 1].mode === p.mode) {
      cur.push(p);
    } else {
      if (cur.length > 0) segments.push(cur);
      cur = [history[i - 1], p];
    }
  });
  if (cur.length > 0) segments.push(cur);

  return (
    <>
      {/* Pane labels */}
      <text
        x={PLOT_X - 30}
        y={PLOT_Y - 12}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        cwnd (MSS) over time
      </text>
      <text
        x={STATE_X}
        y={STATE_Y - 12}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        STATE
      </text>

      {/* Plot pane background */}
      <rect
        x={PLOT_X}
        y={PLOT_Y}
        width={PLOT_W}
        height={PLOT_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />

      {/* Axes */}
      <Line
        from={{ x: PLOT_X, y: PLOT_Y + PLOT_H }}
        to={{ x: PLOT_X + PLOT_W, y: PLOT_Y + PLOT_H }}
        stroke={INK}
        strokeWidth={1}
      />
      <Line
        from={{ x: PLOT_X, y: PLOT_Y }}
        to={{ x: PLOT_X, y: PLOT_Y + PLOT_H }}
        stroke={INK}
        strokeWidth={1}
      />

      {/* Y-axis ticks */}
      {[0, 10, 20, 30].map((v) => (
        <g key={`y-${v}`}>
          <Line
            from={{ x: PLOT_X - 3, y: yScale(v) }}
            to={{ x: PLOT_X, y: yScale(v) }}
            stroke={INK}
            strokeWidth={1}
          />
          <text
            x={PLOT_X - 6}
            y={yScale(v) + 3}
            textAnchor="end"
            fontFamily={FONT_FAMILY}
            fontSize={8}
            fill={INK}
            opacity={0.6}
          >
            {v}
          </text>
        </g>
      ))}

      {/* X-axis ticks */}
      {[0, 10, 20, 30].map((v) => (
        <g key={`x-${v}`}>
          <Line
            from={{ x: xScale(v), y: PLOT_Y + PLOT_H }}
            to={{ x: xScale(v), y: PLOT_Y + PLOT_H + 3 }}
            stroke={INK}
            strokeWidth={1}
          />
          <text
            x={xScale(v)}
            y={PLOT_Y + PLOT_H + 13}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={8}
            fill={INK}
            opacity={0.6}
          >
            {v}
          </text>
        </g>
      ))}

      {/* ssthresh horizontal dashed line */}
      <Line
        from={{ x: PLOT_X, y: yScale(ssthresh) }}
        to={{ x: PLOT_X + PLOT_W, y: yScale(ssthresh) }}
        stroke={INK}
        strokeWidth={1}
        strokeDasharray="3 3"
        opacity={0.5}
      />
      <text
        x={PLOT_X + PLOT_W - 4}
        y={yScale(ssthresh) - 4}
        textAnchor="end"
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        opacity={0.7}
      >
        ssthresh = {ssthresh}
      </text>

      {/* cwnd trajectory — colored by mode */}
      {segments.map((seg, i) => (
        <LinePath
          key={`seg-${i}`}
          data={seg}
          x={(d) => xScale(d.rtt)}
          y={(d) => yScale(d.cwnd)}
          stroke={modeColor(seg[0]?.mode ?? "SLOW_START")}
          strokeWidth={2.5}
          fill="none"
        />
      ))}

      {/* Loss markers — vertical dashed lines + X glyph */}
      {history
        .filter((p) => p.event && p.event.includes("LOSS"))
        .map((p) => (
          <g key={`loss-${p.rtt}`}>
            <Line
              from={{ x: xScale(p.rtt), y: PLOT_Y }}
              to={{ x: xScale(p.rtt), y: PLOT_Y + PLOT_H }}
              stroke={INK}
              strokeWidth={1}
              strokeDasharray="2 2"
              opacity={0.5}
            />
            <text
              x={xScale(p.rtt)}
              y={PLOT_Y + 12}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fontWeight={700}
              fill={INK}
            >
              X
            </text>
          </g>
        ))}

      {/* Current point — yellow dot */}
      <circle
        cx={xScale(rtt)}
        cy={yScale(cwnd)}
        r={5}
        fill={ACCENT}
        stroke={INK}
        strokeWidth={2}
      />

      {/* State panel */}
      <rect
        x={STATE_X}
        y={STATE_Y}
        width={STATE_W}
        height={150}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <text
        x={STATE_X + 8}
        y={STATE_Y + 18}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
      >
        RTT: {rtt}
      </text>
      <text
        x={STATE_X + 8}
        y={STATE_Y + 36}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
      >
        cwnd: {cwnd}
      </text>
      <text
        x={STATE_X + 8}
        y={STATE_Y + 54}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fill={INK}
      >
        ssthresh: {ssthresh}
      </text>
      <text
        x={STATE_X + 8}
        y={STATE_Y + 78}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        MODE
      </text>
      <rect
        x={STATE_X + 8}
        y={STATE_Y + 84}
        width={STATE_W - 16}
        height={20}
        fill={mode === "SLOW_START" ? ACCENT : "#fff"}
        stroke={INK}
        strokeWidth={1}
      />
      <text
        x={STATE_X + 12}
        y={STATE_Y + 98}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
      >
        {mode.replace(/_/g, " ")}
      </text>
      <text
        x={STATE_X + 8}
        y={STATE_Y + 124}
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        opacity={0.6}
      >
        AIMD: Add. Increase,
      </text>
      <text
        x={STATE_X + 8}
        y={STATE_Y + 136}
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        opacity={0.6}
      >
        Mult. Decrease
      </text>

      {/* Segment pipe — visualize cwnd segments in flight */}
      <text
        x={PLOT_X}
        y={PLOT_Y + PLOT_H + 35}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        SEGMENTS IN FLIGHT ({cwnd})
      </text>
      <g transform={`translate(${PLOT_X}, ${PLOT_Y + PLOT_H + 42})`}>
        {Array.from({ length: Math.min(cwnd, 24) }, (_, i) => (
          <rect
            key={`seg-${i}`}
            x={i * 11}
            y={0}
            width={9}
            height={14}
            fill={INK}
            opacity={Math.max(0.2, 0.7 - i * 0.02)}
          />
        ))}
        {cwnd > 24 && (
          <text
            x={24 * 11 + 4}
            y={11}
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fill={INK}
          >
            +{cwnd - 24}
          </text>
        )}
      </g>

      {/* Footer message (2-line wrap) */}
      <rect x={10} y={MSG_Y - 28} width={460} height={36} fill={PAPER} stroke={INK} strokeWidth={1} />
      {(() => {
        const maxCharsPerLine = 78;
        const words = message.split(' ');
        let line1 = '';
        let line2 = '';
        for (const w of words) {
          if (!line1.length || (line1.length + w.length + 1) <= maxCharsPerLine) {
            line1 = line1 ? `${line1} ${w}` : w;
          } else if (!line2.length || (line2.length + w.length + 1) <= maxCharsPerLine) {
            line2 = line2 ? `${line2} ${w}` : w;
          } else {
            line2 = line2 + '…';
            break;
          }
        }
        return (
          <text x={16} y={MSG_Y - 12} fontFamily={FONT_FAMILY} fontSize={9} fontWeight={700} fill={INK}>
            <tspan x={16} dy={0}>{line1}</tspan>
            {line2 && <tspan x={16} dy={12}>{line2}</tspan>}
          </text>
        );
      })()}
    </>
  );
}

export function TcpCwnd(): ReactNode {
  return (
    <AlgoStepperShell<TCPState>
      title="RC-3 · TCP congestion window (Reno)"
      desc="Slow-start → congestion avoidance → packet loss → fast recovery. AIMD pattern over time. Yellow = slow-start; black = congestion avoidance."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="tcp-cwnd"
    />
  );
}
