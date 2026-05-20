import type { ReactNode } from "react";
import { scaleLinear } from "@visx/scale";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, HATCH_LIGHT, INK, PAPER } from "./theme";
import {
  AnimatePresence,
  DrawLine,
  FadeText,
  PopIn,
  TweenText,
  motion,
} from "./motion-helpers";

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
      cwnd = ssthresh + 3;
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

const PLOT_X = 50;
const PLOT_Y = 30;
const PLOT_W = 280;
const PLOT_H = 200;

const MAX_CWND = 30;
const MAX_RTT_PLOT = 30;

const STATE_X = 350;
const STATE_Y = 30;
const STATE_W = 120;

const MSG_Y = 340;

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

  const lossRtts = history.filter((p) => p.event && p.event.includes("LOSS"));

  const segmentsInFlight = Math.min(cwnd, 24);
  const overflowCount = cwnd > 24 ? cwnd - 24 : 0;

  return (
    <>
      {/* Pane labels (static) */}
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

      {/* Axes (static) */}
      <line
        x1={PLOT_X}
        y1={PLOT_Y + PLOT_H}
        x2={PLOT_X + PLOT_W}
        y2={PLOT_Y + PLOT_H}
        stroke={INK}
        strokeWidth={1}
      />
      <line
        x1={PLOT_X}
        y1={PLOT_Y}
        x2={PLOT_X}
        y2={PLOT_Y + PLOT_H}
        stroke={INK}
        strokeWidth={1}
      />

      {/* Y-axis ticks (static) */}
      {[0, 10, 20, 30].map((v) => (
        <g key={`y-${v}`}>
          <line
            x1={PLOT_X - 3}
            y1={yScale(v)}
            x2={PLOT_X}
            y2={yScale(v)}
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

      {/* X-axis ticks (static) */}
      {[0, 10, 20, 30].map((v) => (
        <g key={`x-${v}`}>
          <line
            x1={xScale(v)}
            y1={PLOT_Y + PLOT_H}
            x2={xScale(v)}
            y2={PLOT_Y + PLOT_H + 3}
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

      {/* ssthresh horizontal dashed line — slides smoothly when ssthresh changes */}
      <motion.line
        x1={PLOT_X}
        x2={PLOT_X + PLOT_W}
        initial={false}
        animate={{ y1: yScale(ssthresh), y2: yScale(ssthresh) }}
        transition={{ duration: 0.5, ease: "easeInOut" }}
        stroke={INK}
        strokeWidth={1}
        strokeDasharray="3 3"
        opacity={0.5}
      />
      <motion.text
        x={PLOT_X + PLOT_W - 4}
        initial={false}
        animate={{ y: yScale(ssthresh) - 4 }}
        transition={{ type: "tween", duration: 0.5, ease: "easeInOut" }}
        textAnchor="end"
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        opacity={0.7}
      >
        <tspan>ssthresh = </tspan>
        <tspan>{ssthresh}</tspan>
      </motion.text>

      {/* Slow-start region hatch band — marks the x-range where mode===SLOW_START.
          This visually distinguishes the slow-start phase without using color. */}
      {(() => {
        const slowStartPoints = history.filter((p) => p.mode === "SLOW_START");
        if (slowStartPoints.length === 0) return null;
        const minRtt = slowStartPoints[0].rtt;
        const maxRtt = slowStartPoints[slowStartPoints.length - 1].rtt;
        const x1 = xScale(minRtt);
        const x2 = xScale(maxRtt);
        return (
          <rect
            x={x1}
            y={PLOT_Y}
            width={Math.max(0, x2 - x1)}
            height={PLOT_H}
            fill={HATCH_LIGHT}
            opacity={0.5}
          />
        );
      })()}

      {/* cwnd trajectory — one DrawLine per consecutive RTT pair, draws on as history grows.
          Uniformly INK end-to-end; mode is encoded by the hatch band above, not line color. */}
      {history.slice(1).map((p, i) => {
        const prev = history[i];
        return (
          <DrawLine
            key={`edge-${prev.rtt}-${p.rtt}`}
            x1={xScale(prev.rtt)}
            y1={yScale(prev.cwnd)}
            x2={xScale(p.rtt)}
            y2={yScale(p.cwnd)}
            stroke={INK}
            strokeWidth={2.5}
            durationMs={500}
          />
        );
      })}

      {/* Loss markers — pop in dramatically */}
      <AnimatePresence>
        {lossRtts.map((p) => (
          <PopIn key={`loss-${p.rtt}`} durationMs={500}>
            <line
              x1={xScale(p.rtt)}
              y1={PLOT_Y}
              x2={xScale(p.rtt)}
              y2={PLOT_Y + PLOT_H}
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
              fontSize={11}
              fontWeight={700}
              fill={INK}
            >
              ×
            </text>
          </PopIn>
        ))}
      </AnimatePresence>

      {/* Current data point — yellow dot, slides smoothly via framer-motion */}
      <motion.circle
        initial={false}
        animate={{ cx: xScale(rtt), cy: yScale(cwnd) }}
        transition={{ duration: 0.5, ease: "easeInOut" }}
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

      <TweenText
        x={STATE_X + 8}
        y={STATE_Y + 18}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        value={rtt}
        formatter={(n) => `RTT: ${Math.round(n)}`}
      />
      <TweenText
        x={STATE_X + 8}
        y={STATE_Y + 36}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        value={cwnd}
        formatter={(n) => `cwnd: ${Math.round(n)}`}
      />
      <TweenText
        x={STATE_X + 8}
        y={STATE_Y + 54}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fill={INK}
        value={ssthresh}
        formatter={(n) => `ssthresh: ${Math.round(n)}`}
      />
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
      {/* Mode panel box — hatched background for slow-start, plain PAPER for others.
          Category is encoded via hatch pattern, NOT via color. */}
      <rect
        x={STATE_X + 8}
        y={STATE_Y + 84}
        width={STATE_W - 16}
        height={20}
        fill={mode === "SLOW_START" ? HATCH_LIGHT : PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <FadeText
        x={STATE_X + 12}
        y={STATE_Y + 98}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
      >
        {mode.replace(/_/g, " ")}
      </FadeText>
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

      {/* Segments-in-flight count + animated bars */}
      <TweenText
        x={PLOT_X}
        y={PLOT_Y + PLOT_H + 35}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
        value={cwnd}
        formatter={(n) => `SEGMENTS IN FLIGHT (${Math.round(n)})`}
      />
      <g transform={`translate(${PLOT_X}, ${PLOT_Y + PLOT_H + 42})`}>
        <AnimatePresence>
          {Array.from({ length: segmentsInFlight }, (_, i) => (
            <PopIn key={`bar-${i}`} durationMs={400}>
              <rect
                x={i * 11}
                y={0}
                width={9}
                height={14}
                fill={INK}
                opacity={Math.max(0.2, 0.7 - i * 0.02)}
              />
            </PopIn>
          ))}
        </AnimatePresence>
        {overflowCount > 0 && (
          <TweenText
            x={24 * 11 + 4}
            y={11}
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fill={INK}
            value={overflowCount}
            formatter={(n) => `+${Math.round(n)}`}
          />
        )}
      </g>

      {/* Footer message — cross-fade on change */}
      <rect
        x={10}
        y={MSG_Y - 28}
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
  // tspan textContent concatenates without a separator — pad line1 so the
  // last word of line1 stays separated from the first word of line2.
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

export function TcpCwnd(): ReactNode {
  return (
    <AlgoStepperShell<TCPState>
      title="RC-3 · TCP congestion window (Reno)"
      desc="Slow-start → congestion avoidance → packet loss → fast recovery. AIMD pattern over time. Hatched band = slow-start region; yellow dot = current cwnd position."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="tcp-cwnd"
    />
  );
}
