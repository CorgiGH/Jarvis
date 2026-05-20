import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";
import {
  AnimatePresence,
  FadeText,
  PopIn,
  TweenText,
  motion,
} from "./motion-helpers";

type ThreadName = "T1" | "T2";
type Thread = {
  name: ThreadName;
  pc: number; // 0..3 — 0 = before line 1, 3 = done
  x: number | null;
  blocked: boolean;
};
type MutexState = "FREE" | "LOCKED";
type Phase = "RACE" | "MUTEX" | "SUMMARY";
type State = {
  step: number;
  phase: Phase;
  counter: number;
  raceCounterFinal?: number;
  t1: Thread;
  t2: Thread;
  mutex: { state: MutexState; owner: ThreadName | null };
  highlightOp?: { thread: ThreadName; opIdx: 0 | 1 | 2 };
  lostUpdateFlash?: boolean;
  mutexAcquireFlash?: ThreadName;
  message: string;
};

function clone<T>(x: T): T {
  return JSON.parse(JSON.stringify(x));
}

function buildFrames(): Frame<State>[] {
  const frames: Frame<State>[] = [];
  const push = (s: State, aria?: string) =>
    frames.push({ state: clone(s), aria: aria ?? s.message });

  const initialThread = (name: ThreadName): Thread => ({
    name,
    pc: 0,
    x: null,
    blocked: false,
  });

  // ===== PHASE 1: RACE =====
  let s: State = {
    step: 0,
    phase: "RACE",
    counter: 0,
    t1: initialThread("T1"),
    t2: initialThread("T2"),
    mutex: { state: "FREE", owner: null },
    message: "BOTH threads will increment counter. Watch the interleaving.",
  };
  push(s);

  // F1: T1 op1 (load) — x = counter → T1.x = 0; T1.PC = 1
  s = {
    ...s,
    step: 1,
    t1: { ...s.t1, pc: 1, x: s.counter },
    highlightOp: { thread: "T1", opIdx: 0 },
    message: "T1 reads counter: x ← 0",
  };
  push(s);

  // F2: T2 op1 (load) — x = counter → T2.x = 0; T2.PC = 1. RACE SEEDED.
  s = {
    ...s,
    step: 2,
    t2: { ...s.t2, pc: 1, x: s.counter },
    highlightOp: { thread: "T2", opIdx: 0 },
    message: "T2 reads counter: x ← 0. RACE SEEDED — both loaded 0!",
  };
  push(s);

  // F3: T1 op2 (compute) — x = x + 1 → T1.x = 1; T1.PC = 2
  s = {
    ...s,
    step: 3,
    t1: { ...s.t1, pc: 2, x: 1 },
    highlightOp: { thread: "T1", opIdx: 1 },
    message: "T1 computes: x ← x + 1 = 1",
  };
  push(s);

  // F4: T2 op2 (compute) — x = x + 1 → T2.x = 1; T2.PC = 2
  s = {
    ...s,
    step: 4,
    t2: { ...s.t2, pc: 2, x: 1 },
    highlightOp: { thread: "T2", opIdx: 1 },
    message: "T2 computes: x ← x + 1 = 1",
  };
  push(s);

  // F5: T1 op3 (store) — counter = x → counter = 1; T1 done.
  s = {
    ...s,
    step: 5,
    counter: 1,
    t1: { ...s.t1, pc: 3 },
    highlightOp: { thread: "T1", opIdx: 2 },
    message: "T1 stores: counter ← 1. T1 done.",
  };
  push(s);

  // F6: T2 op3 (store) — counter = x → counter = 1; T2 done.
  // Focal action: T2's store line. lostUpdateFlash moves to F7 so that no
  // single frame has two simultaneous ACCENT triggers (V12 invariant).
  s = {
    ...s,
    step: 6,
    counter: 1, // still 1 — that's the bug
    t2: { ...s.t2, pc: 3 },
    highlightOp: { thread: "T2", opIdx: 2 },
    lostUpdateFlash: false,
    message: "T2 stores: counter ← 1. T2 done — but counter should be 2!",
  };
  push(s);

  // F7: result — counter = 1 (expected 2). RACE! Flash the counter here
  // (aftermath frame, no code-line highlight) so lost-update is shown without
  // two ACCENT elements at once.
  s = {
    ...s,
    step: 7,
    highlightOp: undefined,
    lostUpdateFlash: true,
    raceCounterFinal: 1,
    message:
      "RACE result: counter = 1, expected 2. LOST UPDATE — both threads clobbered the same write.",
  };
  push(s);

  // ===== PHASE 2: MUTEX =====
  // F8: reset
  s = {
    step: 8,
    phase: "MUTEX",
    counter: 0,
    raceCounterFinal: 1,
    t1: initialThread("T1"),
    t2: initialThread("T2"),
    mutex: { state: "FREE", owner: null },
    message: "Now with mutex(M). Lock guards counter — only one thread inside.",
  };
  push(s);

  // F9: T1 lock(M) — M = LOCKED by T1. T1.PC = 1 (about to read)
  s = {
    ...s,
    step: 9,
    t1: { ...s.t1, pc: 1 },
    mutex: { state: "LOCKED", owner: "T1" },
    mutexAcquireFlash: "T1",
    message: "T1 lock(M): mutex now LOCKED by T1. T1 enters critical section.",
  };
  push(s);

  // F10: T1 op1 — T1.x = 0
  s = {
    ...s,
    step: 10,
    t1: { ...s.t1, x: 0 },
    mutexAcquireFlash: undefined,
    highlightOp: { thread: "T1", opIdx: 0 },
    message: "T1 reads counter inside CS: x ← 0",
  };
  push(s);

  // F11: T2 lock(M) attempt — T2 BLOCKED on M
  s = {
    ...s,
    step: 11,
    t2: { ...s.t2, blocked: true },
    highlightOp: undefined,
    message: "T2 calls lock(M) but M is held by T1 — T2 BLOCKED.",
  };
  push(s);

  // F12: T1 op2 — T1.x = 1
  s = {
    ...s,
    step: 12,
    t1: { ...s.t1, pc: 2, x: 1 },
    highlightOp: { thread: "T1", opIdx: 1 },
    message: "T1 computes inside CS: x ← x + 1 = 1 (T2 still blocked)",
  };
  push(s);

  // F13: T1 op3 (store) + T1 unlock(M) — counter = 1; M unlocked; T2 unblocks
  s = {
    ...s,
    step: 13,
    counter: 1,
    t1: { ...s.t1, pc: 3 },
    t2: { ...s.t2, blocked: false },
    mutex: { state: "FREE", owner: null },
    highlightOp: { thread: "T1", opIdx: 2 },
    message:
      "T1 stores counter=1, then unlock(M). T2 unblocks and tries to acquire.",
  };
  push(s);

  // F14: T2 lock(M) acquires — M = LOCKED by T2
  s = {
    ...s,
    step: 14,
    t2: { ...s.t2, pc: 1 },
    mutex: { state: "LOCKED", owner: "T2" },
    mutexAcquireFlash: "T2",
    highlightOp: undefined,
    message: "T2 acquires lock(M). Mutex now LOCKED by T2.",
  };
  push(s);

  // F15: T2 op1 → x=1, op2 → x=2, op3 → counter=2; T2 unlock(M)
  s = {
    ...s,
    step: 15,
    counter: 2,
    t2: { ...s.t2, pc: 3, x: 2 },
    mutex: { state: "FREE", owner: null },
    mutexAcquireFlash: undefined,
    highlightOp: { thread: "T2", opIdx: 2 },
    message:
      "T2 inside CS: reads 1, computes 2, stores counter=2. unlock(M). T2 done.",
  };
  push(s);

  // F16: result — counter = 2 (correct)
  s = {
    ...s,
    step: 16,
    highlightOp: undefined,
    message:
      "MUTEX result: counter = 2 (correct). The mutex serialized the critical sections.",
  };
  push(s);

  // ===== PHASE 3: SUMMARY =====
  s = {
    ...s,
    step: 17,
    phase: "SUMMARY",
    message:
      "RACE clobbers writes (counter = 1). MUTEX serializes critical sections (counter = 2). Lock the shared variable!",
  };
  push(s);

  return frames;
}

export const FRAMES = buildFrames();
export const FRAME_COUNT = FRAMES.length;

// ===== Layout constants — viewBox 480 × 360 =====

const T1_X = 10;
const T2_X = 310;
const THREAD_W = 150;
const THREAD_Y = 10;
const CODE_LINE_H = 14;
const CODE_TOP_Y = 30; // top of first code line within thread pane
const THREAD_H = 115; // pane height

const HEAP_Y = 130;
const HEAP_H = 50;
const COUNTER_X = 80;
const COUNTER_W = 130;
const MUTEX_X = 270;
const MUTEX_W = 130;

const FLOW_Y_TOP = 180;
const FLOW_Y_BOTTOM = 240;

const COMPARE_Y = 250;
const COMPARE_H = 55;

const FOOTER_Y = 312;
const FOOTER_H = 36;

const MSG_Y_BASELINE = FOOTER_Y + 14;

const CODE_LINES = ["1: x = counter", "2: x = x + 1", "3: counter = x"];

function threadPaneX(t: ThreadName): number {
  return t === "T1" ? T1_X : T2_X;
}

function renderFrame(frame: Frame<State>): ReactNode {
  const {
    step,
    phase,
    counter,
    raceCounterFinal,
    t1,
    t2,
    mutex,
    highlightOp,
    lostUpdateFlash,
    mutexAcquireFlash,
    message,
  } = frame.state;

  // Active code line y for highlight rect, per thread.
  // pc=0 → no highlight; pc=1..3 → line index (pc-1)
  // Highlight y is still driven by pc (where the current line IS), but
  // the highlight only *shows* on the thread that owns this frame's focal op.
  const t1HighlightY =
    t1.pc >= 1 && t1.pc <= 3
      ? CODE_TOP_Y + (t1.pc - 1) * CODE_LINE_H
      : CODE_TOP_Y - 100; // off-screen
  const t2HighlightY =
    t2.pc >= 1 && t2.pc <= 3
      ? CODE_TOP_Y + (t2.pc - 1) * CODE_LINE_H
      : CODE_TOP_Y - 100;

  const showMutex = phase !== "RACE";

  return (
    <>
      <defs>
        <marker
          id="rm-arrow-ink"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="6"
          markerHeight="6"
          orient="auto-start-reverse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill={INK} />
        </marker>
        <marker
          id="rm-arrow-accent"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="6"
          markerHeight="6"
          orient="auto-start-reverse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill={INK} />
        </marker>
      </defs>

      {/* Phase indicator (top center) — sits in the 150px gap between thread
          panes (T1 ends at x=160, T2 starts at x=310). Parentheticals were
          dropped so the centered text doesn't clip the thread boxes. */}
      <FadeText
        x={240}
        y={20}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        {phase === "RACE"
          ? "PHASE 1 · RACE"
          : phase === "MUTEX"
          ? "PHASE 2 · MUTEX"
          : "PHASE 3 · COMPARE"}
      </FadeText>

      {/* ===== T1 PANE ===== */}
      {/* showHighlight: only when this thread is the focal op of the frame.
          pc >= 1 positions the bar correctly but doesn't light it up on its
          own — prevents two yellow bars painting simultaneously when both
          threads have advanced their PCs. */}
      <ThreadPane
        x={T1_X}
        y={THREAD_Y}
        w={THREAD_W}
        h={THREAD_H}
        label="THREAD T1"
        thread={t1}
        highlightY={t1HighlightY}
        showHighlight={highlightOp?.thread === "T1"}
      />

      {/* ===== T2 PANE ===== */}
      <ThreadPane
        x={T2_X}
        y={THREAD_Y}
        w={THREAD_W}
        h={THREAD_H}
        label="THREAD T2"
        thread={t2}
        highlightY={t2HighlightY}
        showHighlight={highlightOp?.thread === "T2"}
      />

      {/* ===== SHARED HEAP ===== */}
      <text
        x={240}
        y={HEAP_Y - 4}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        SHARED HEAP
      </text>

      {/* counter cell — flashes on lost-update */}
      <motion.rect
        x={COUNTER_X}
        y={HEAP_Y}
        width={COUNTER_W}
        height={HEAP_H}
        initial={false}
        animate={{
          fill: lostUpdateFlash ? ACCENT : "#fff",
        }}
        transition={{ duration: 0.8, ease: "easeInOut" }}
        stroke={INK}
        strokeWidth={1}
      />
      <text
        x={COUNTER_X + 8}
        y={HEAP_Y + 16}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        counter
      </text>
      <TweenText
        x={COUNTER_X + COUNTER_W / 2}
        y={HEAP_Y + 38}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={20}
        fontWeight={700}
        fill={INK}
        value={counter}
        formatter={(n) => `= ${Math.round(n)}`}
      />

      {/* mutex cell — only visible during MUTEX / SUMMARY phases */}
      <AnimatePresence>
        {showMutex && (
          <PopIn key="mutex-cell" durationMs={300}>
            <motion.rect
              x={MUTEX_X}
              y={HEAP_Y}
              width={MUTEX_W}
              height={HEAP_H}
              initial={false}
              animate={{
                fill: mutex.state === "LOCKED" ? INK : PAPER,
              }}
              transition={{ duration: 0.4, ease: "easeInOut" }}
              stroke={INK}
              strokeWidth={mutexAcquireFlash ? 2 : 1}
            />
            {/* Mutex label: PAPER on the dark INK rect when LOCKED (contrast),
                INK with reduced opacity when FREE.  Never ACCENT — LOCKED is a
                persistent state, not the single focal action of a frame. */}
            <text
              x={MUTEX_X + 8}
              y={HEAP_Y + 16}
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fontWeight={700}
              fill={mutex.state === "LOCKED" ? PAPER : INK}
              opacity={mutex.state === "LOCKED" ? 1 : 0.7}
            >
              mutex M
            </text>
            {/* Mutex state text: glyph (🔒/🔓) + label conveys LOCKED/FREE
                without using yellow.  PAPER text sits legibly on the dark
                INK rect fill; INK text on the light PAPER rect when FREE. */}
            <FadeText
              x={MUTEX_X + MUTEX_W / 2}
              y={HEAP_Y + 38}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={12}
              fontWeight={700}
              fill={mutex.state === "LOCKED" ? PAPER : INK}
            >
              {mutex.state === "LOCKED"
                ? `🔒 LOCKED by ${mutex.owner ?? "?"}`
                : "🔓 FREE"}
            </FadeText>
          </PopIn>
        )}
      </AnimatePresence>

      {/* ===== Data-flow arrow band ===== */}
      <DataFlowArrow
        op={highlightOp}
        t1Pc={t1.pc}
        t2Pc={t2.pc}
      />

      {/* ===== Comparison strip ===== */}
      <rect
        x={10}
        y={COMPARE_Y}
        width={460}
        height={COMPARE_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <ComparisonStrip
        phase={phase}
        step={step}
        counter={counter}
        raceCounterFinal={raceCounterFinal}
      />

      {/* ===== Footer ===== */}
      <rect
        x={10}
        y={FOOTER_Y}
        width={460}
        height={FOOTER_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <FooterMessage message={message} />

      {/* Bounds anchor */}
      <rect x={0} y={0} width={480} height={360} fill="none" stroke="none" />
    </>
  );
}

function ThreadPane(props: {
  x: number;
  y: number;
  w: number;
  h: number;
  label: string;
  thread: Thread;
  highlightY: number;
  showHighlight: boolean;
}) {
  const { x, y, w, h, label, thread, highlightY, showHighlight } = props;

  return (
    <g>
      {/* pane border */}
      <rect
        x={x}
        y={y}
        width={w}
        height={h}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      {/* label */}
      <text
        x={x + 6}
        y={y + 12}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        {label}
      </text>

      {/* active code line highlight — slides between line y-positions.
          fill is moved into animate so JSDOM/test environments see the
          correct value: ACCENT only when this thread is focal (showHighlight),
          "none" otherwise — prevents two yellow rects in the DOM at once. */}
      <motion.rect
        x={x + 4}
        width={w - 8}
        height={CODE_LINE_H}
        initial={false}
        animate={{
          y: highlightY - 11,
          opacity: showHighlight ? 1 : 0,
          fill: showHighlight ? ACCENT : "none",
        }}
        transition={{ type: "tween", duration: 0.4, ease: "easeInOut" }}
        stroke="none"
      />

      {/* code listing — 3 lines */}
      {CODE_LINES.map((line, i) => (
        <text
          key={i}
          x={x + 8}
          y={CODE_TOP_Y + i * CODE_LINE_H}
          fontFamily={FONT_FAMILY}
          fontSize={9}
          fill={INK}
        >
          {line}
        </text>
      ))}

      {/* x value */}
      <text
        x={x + 8}
        y={y + 84}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        {thread.name}.x =
      </text>
      {thread.x === null ? (
        <FadeText
          x={x + 56}
          y={y + 84}
          fontFamily={FONT_FAMILY}
          fontSize={11}
          fontWeight={700}
          fill={INK}
          opacity={0.5}
        >
          ?
        </FadeText>
      ) : (
        <TweenText
          x={x + 56}
          y={y + 84}
          fontFamily={FONT_FAMILY}
          fontSize={11}
          fontWeight={700}
          fill={INK}
          value={thread.x}
          formatter={(n) => `${Math.round(n)}`}
        />
      )}

      {/* status indicator — state conveyed by glyph/label, not by yellow.
          "blocked" is a multi-frame state, not the single focal action.
          A pulsing opacity draws attention without colour violation. */}
      <motion.text
        x={x + 8}
        y={y + 102}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        initial={false}
        animate={
          thread.blocked
            ? { opacity: [0.9, 0.4, 0.9] }
            : { opacity: 0.8 }
        }
        transition={
          thread.blocked
            ? { duration: 1.0, repeat: Infinity, ease: "easeInOut" }
            : { duration: 0.3 }
        }
      >
        {thread.blocked
          ? "⊘ BLOCKED ON M"
          : thread.pc === 3
          ? "DONE"
          : thread.pc === 0
          ? "READY"
          : "RUNNING"}
      </motion.text>
    </g>
  );
}

function DataFlowArrow(props: {
  op: { thread: ThreadName; opIdx: 0 | 1 | 2 } | undefined;
  t1Pc: number;
  t2Pc: number;
}) {
  const { op } = props;
  if (!op) return null;

  // op 0 = load (counter → T<n>.x), op 2 = store (T<n>.x → counter)
  // op 1 = local compute — show no arrow (it's local)
  if (op.opIdx === 1) {
    return (
      <FadeText
        x={240}
        y={FLOW_Y_TOP + 24}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        {`${op.thread}: local compute (x = x + 1)`}
      </FadeText>
    );
  }

  const threadX = threadPaneX(op.thread);
  const threadCenterX = threadX + THREAD_W / 2;
  const counterCenterX = COUNTER_X + COUNTER_W / 2;
  const isLoad = op.opIdx === 0;

  // Arrow endpoints
  const srcX = isLoad ? counterCenterX : threadCenterX;
  const dstX = isLoad ? threadCenterX : counterCenterX;
  const arrowY = FLOW_Y_TOP + 24;

  const labelText = isLoad
    ? `${op.thread}: load counter → ${op.thread}.x`
    : `${op.thread}: store ${op.thread}.x → counter`;

  return (
    <g key={`${op.thread}-${op.opIdx}`}>
      <FadeText
        x={240}
        y={FLOW_Y_TOP + 8}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        {labelText}
      </FadeText>
      <motion.line
        x1={srcX}
        y1={arrowY}
        initial={{ x2: srcX, y2: arrowY, opacity: 0 }}
        animate={{ x2: dstX, y2: arrowY, opacity: 1 }}
        transition={{ type: "tween", duration: 0.5, ease: "easeInOut" }}
        stroke={INK}
        strokeWidth={2}
        markerEnd="url(#rm-arrow-ink)"
      />
      {/* Vertical connectors from arrow line to source / dest cells (decorative) */}
      <motion.line
        x1={srcX}
        y1={isLoad ? HEAP_Y + HEAP_H : THREAD_Y + THREAD_H}
        x2={srcX}
        y2={arrowY}
        initial={{ opacity: 0 }}
        animate={{ opacity: 0.5 }}
        transition={{ duration: 0.3 }}
        stroke={INK}
        strokeWidth={1}
        strokeDasharray="2 2"
      />
      <motion.line
        x1={dstX}
        y1={isLoad ? THREAD_Y + THREAD_H : HEAP_Y + HEAP_H}
        x2={dstX}
        y2={arrowY}
        initial={{ opacity: 0 }}
        animate={{ opacity: 0.5 }}
        transition={{ duration: 0.3 }}
        stroke={INK}
        strokeWidth={1}
        strokeDasharray="2 2"
      />
    </g>
  );
}

// Last frame of each phase — the verdict is only honest at end-of-phase.
// PHASE 1 (RACE) runs F0..F7, so step 7 is the result frame.
// PHASE 2 (MUTEX) runs F8..F16, so step 16 is the result frame.
const RACE_RESULT_STEP = 7;
const MUTEX_RESULT_STEP = 16;

function ComparisonStrip(props: {
  phase: Phase;
  step: number;
  counter: number;
  raceCounterFinal: number | undefined;
}) {
  const { phase, step, counter, raceCounterFinal } = props;

  // Three layouts: RACE shows just race line. MUTEX shows just mutex line.
  // SUMMARY shows both side by side. During mid-phase execution we show a
  // neutral "computing…" placeholder instead of a lie — the verdict only
  // becomes truthful at the phase's last frame.
  if (phase === "RACE") {
    const revealed = step >= RACE_RESULT_STEP;
    return (
      <>
        <text
          x={20}
          y={COMPARE_Y + 18}
          fontFamily={FONT_FAMILY}
          fontSize={9}
          fontWeight={700}
          fill={INK}
          opacity={0.7}
        >
          RACE (no mutex)
        </text>
        {revealed ? (
          <text
            x={20}
            y={COMPARE_Y + 42}
            fontFamily={FONT_FAMILY}
            fontSize={14}
            fontWeight={700}
            fill={INK}
          >
            {`counter ends at ${counter}  ≠  2 expected  (LOST UPDATE)`}
          </text>
        ) : (
          <text
            x={20}
            y={COMPARE_Y + 42}
            fontFamily={FONT_FAMILY}
            fontSize={12}
            fontWeight={700}
            fill={INK}
            opacity={0.55}
          >
            watch ops execute — verdict at end of phase
          </text>
        )}
      </>
    );
  }
  if (phase === "MUTEX") {
    const revealed = step >= MUTEX_RESULT_STEP;
    // Operator + tag depend on outcome: ≠ if the mutex still failed (counter
    // didn't reach 2), = if it succeeded. Defensive — current frame ladder
    // always lands on counter=2, but the code shouldn't lie if that changes.
    const correct = counter === 2;
    const op = correct ? "=" : "≠";
    const tag = correct ? "CORRECT" : "STILL WRONG";
    return (
      <>
        <text
          x={20}
          y={COMPARE_Y + 18}
          fontFamily={FONT_FAMILY}
          fontSize={9}
          fontWeight={700}
          fill={INK}
          opacity={0.7}
        >
          MUTEX (lock guards counter)
        </text>
        {revealed ? (
          <text
            x={20}
            y={COMPARE_Y + 42}
            fontFamily={FONT_FAMILY}
            fontSize={14}
            fontWeight={700}
            fill={INK}
          >
            {`counter ends at ${counter}  ${op}  2 expected  (${tag})`}
          </text>
        ) : (
          <text
            x={20}
            y={COMPARE_Y + 42}
            fontFamily={FONT_FAMILY}
            fontSize={12}
            fontWeight={700}
            fill={INK}
            opacity={0.55}
          >
            watch ops execute — verdict at end of phase
          </text>
        )}
      </>
    );
  }
  // SUMMARY
  return (
    <>
      <text
        x={20}
        y={COMPARE_Y + 18}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        RACE
      </text>
      <text
        x={20}
        y={COMPARE_Y + 42}
        fontFamily={FONT_FAMILY}
        fontSize={14}
        fontWeight={700}
        fill={INK}
      >
        {`counter = ${raceCounterFinal ?? "?"}  ≠  2  (LOST UPDATE)`}
      </text>
      <line
        x1={240}
        y1={COMPARE_Y + 6}
        x2={240}
        y2={COMPARE_Y + COMPARE_H - 6}
        stroke={INK}
        strokeWidth={1}
        opacity={0.3}
      />
      <text
        x={252}
        y={COMPARE_Y + 18}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        MUTEX
      </text>
      <text
        x={252}
        y={COMPARE_Y + 42}
        fontFamily={FONT_FAMILY}
        fontSize={14}
        fontWeight={700}
        fill={INK}
      >
        {`counter = ${counter}  =  2  (CORRECT)`}
      </text>
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
  // Restore the joining space between line1 and line2 in the flat textContent
  // (SVG <tspan> elements concatenate without whitespace). Without this, words
  // straddling the wrap boundary fuse together when read via .textContent.
  if (line2) {
    line1 = line1 + " ";
  }
  return (
    <AnimatePresence initial={false}>
      <motion.text
        key={message}
        x={16}
        y={MSG_Y_BASELINE}
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

export function RaceMutex(): ReactNode {
  return (
    <AlgoStepperShell<State>
      title="SO-3 · Race condition + mutex"
      desc="Two threads in the same process share the heap (and counter). Without a mutex, interleaved load/compute/store ops clobber each other. With mutex(M), the critical section is serialized."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="race-mutex"
    />
  );
}
