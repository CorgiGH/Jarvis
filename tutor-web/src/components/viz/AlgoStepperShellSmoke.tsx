import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "./theme";
import { FadeText, motion } from "./motion-helpers";

type CounterState = { n: number };

const frames: Frame<CounterState>[] = Array.from({ length: 8 }, (_, i) => ({
  state: { n: i },
  aria: `Counter at ${i}`,
}));

function renderCounter(f: Frame<CounterState>): ReactNode {
  return (
    <>
      <text
        x={240}
        y={150}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={11}
        fontWeight={700}
        fill={INK}
        opacity={0.6}
      >
        COUNTER
      </text>
      <FadeText
        x={240}
        y={210}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={84}
        fontWeight={900}
        fill={INK}
      >
        {String(f.state.n)}
      </FadeText>
      <motion.rect
        x={140}
        y={250}
        height={12}
        initial={false}
        animate={{ width: (f.state.n / 7) * 200 }}
        transition={{ type: "tween", duration: 0.5, ease: "easeInOut" }}
        fill={ACCENT}
        stroke={INK}
        strokeWidth={1}
      />
      <rect
        x={140}
        y={250}
        width={200}
        height={12}
        fill="none"
        stroke={INK}
        strokeWidth={1}
      />
    </>
  );
}

export function AlgoStepperShellSmoke() {
  return (
    <AlgoStepperShell
      title="AlgoStepperShell smoke — counter"
      desc="Subclass demo: counter 0..7 with brutalist progress bar"
      frames={frames}
      renderFrame={renderCounter}
      testIdPrefix="stepper-smoke"
    />
  );
}
