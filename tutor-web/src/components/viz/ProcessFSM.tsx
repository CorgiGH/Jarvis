import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";
import { AnimatePresence, FadeText, motion } from "./motion-helpers";

// ─── Types ──────────────────────────────────────────────────────────────────
type State_t =
  | "NEW"
  | "READY"
  | "RUNNING"
  | "WAITING"
  | "ZOMBIE"
  | "TERMINATED";

type Process_t = {
  pid: number;
  ppid: number;
  name: string;
  state: State_t;
  ioReason?: string;
};

type FSMState = {
  step: number;
  processes: Process_t[];
  focusedPid: number | null;
  activeTransition?: { from: State_t; to: State_t; pid: number };
  message: string;
};

// ─── Frame construction ────────────────────────────────────────────────────
function clone<T>(x: T): T {
  return JSON.parse(JSON.stringify(x));
}

function buildFrames(): Frame<FSMState>[] {
  const frames: Frame<FSMState>[] = [];

  // Mutable working state we copy into each frame.
  let processes: Process_t[] = [];

  const push = (
    msg: string,
    focusedPid: number | null,
    activeTransition?: { from: State_t; to: State_t; pid: number }
  ) => {
    const step = frames.length;
    frames.push({
      state: {
        step,
        processes: clone(processes),
        focusedPid,
        activeTransition,
        message: msg,
      },
      aria: msg,
    });
  };

  // F0 — parent P1 spawned, state=NEW.
  processes = [{ pid: 100, ppid: 1, name: "shell", state: "NEW" }];
  push(
    "P1 (PID 100) created by init. state=NEW — PCB allocated, not yet runnable.",
    100
  );

  // F1 — P1 → READY.
  processes[0].state = "READY";
  push("P1 admitted to run queue. state=READY — waiting for CPU.", 100, {
    from: "NEW",
    to: "READY",
    pid: 100,
  });

  // F2 — P1 → RUNNING.
  processes[0].state = "RUNNING";
  push(
    "Scheduler picks P1. state=RUNNING — executing on the CPU.",
    100,
    { from: "READY", to: "RUNNING", pid: 100 }
  );

  // F3 — P1 fork() → P2 spawned (NEW).
  processes = [
    { pid: 100, ppid: 1, name: "shell", state: "RUNNING" },
    { pid: 101, ppid: 100, name: "child", state: "NEW" },
  ];
  push(
    "P1 calls fork(). Kernel creates P2 (PID 101, PPID 100). P2 state=NEW.",
    101
  );

  // F4 — P2 → READY.
  processes[1].state = "READY";
  push(
    "P2 admitted to run queue. state=READY — sibling of P1, both could run.",
    101,
    { from: "NEW", to: "READY", pid: 101 }
  );

  // F5 — P1 wait() → P1 WAITING, P2 → RUNNING.
  processes[0].state = "WAITING";
  processes[0].ioReason = "wait(child)";
  processes[1].state = "RUNNING";
  push(
    "P1 calls wait() — blocks on child. P1=WAITING. Scheduler runs P2.",
    100,
    { from: "RUNNING", to: "WAITING", pid: 100 }
  );

  // F6 — P2 does I/O → WAITING.
  processes[1].state = "WAITING";
  processes[1].ioReason = "read(disk)";
  push(
    "P2 issues read() syscall. P2=WAITING on disk I/O. P1 still waiting on child.",
    101,
    { from: "RUNNING", to: "WAITING", pid: 101 }
  );

  // F7 — P2 I/O completes → READY. (P1 still WAITING on child.)
  processes[1].state = "READY";
  processes[1].ioReason = undefined;
  push(
    "Disk interrupt fires. P2 unblocks: WAITING → READY (back in run queue). P1 still WAITING on child.",
    101,
    { from: "WAITING", to: "READY", pid: 101 }
  );

  // F8 — P2 → RUNNING.
  processes[1].state = "RUNNING";
  push("Scheduler picks P2. state=RUNNING.", 101, {
    from: "READY",
    to: "RUNNING",
    pid: 101,
  });

  // F9 — P2 exit(0) → ZOMBIE.
  processes[1].state = "ZOMBIE";
  processes[1].ioReason = "exit=0";
  push(
    "P2 calls exit(0). State=ZOMBIE: PCB lingers with exit status, waiting to be reaped by parent.",
    101,
    { from: "RUNNING", to: "ZOMBIE", pid: 101 }
  );

  // F10 — SIGCHLD wakes P1; P1 → RUNNING.
  processes[0].state = "RUNNING";
  processes[0].ioReason = undefined;
  push(
    "Kernel sends SIGCHLD to P1. wait() returns with P2's exit status. P1=RUNNING.",
    100,
    { from: "WAITING", to: "RUNNING", pid: 100 }
  );

  // F11 — P2 reaped → TERMINATED.
  processes[1].state = "TERMINATED";
  processes[1].ioReason = undefined;
  push(
    "P1's wait() collected P2's status. Kernel reaps P2's PCB. P2=TERMINATED.",
    101,
    { from: "ZOMBIE", to: "TERMINATED", pid: 101 }
  );

  // F12 — P1 keeps RUNNING.
  push(
    "P1 resumes user code after wait(). state=RUNNING — child cleanup complete.",
    100
  );

  // F13 — P1 exit() → ZOMBIE.
  processes[0].state = "ZOMBIE";
  processes[0].ioReason = "exit=0";
  push(
    "P1 calls exit(). state=ZOMBIE — awaiting reap by init (PID 1), the grandparent.",
    100,
    { from: "RUNNING", to: "ZOMBIE", pid: 100 }
  );

  // F14 — init reaps P1 → TERMINATED + summary.
  processes[0].state = "TERMINATED";
  processes[0].ioReason = undefined;
  push(
    "init (PID 1) reaps P1. state=TERMINATED. ZOMBIE = the unavoidable gap between exit and reap.",
    100,
    { from: "ZOMBIE", to: "TERMINATED", pid: 100 }
  );

  return frames;
}

export const FRAMES = buildFrames();
export const FRAME_COUNT = FRAMES.length;

// ─── Layout constants ──────────────────────────────────────────────────────
const MSG_Y = 340;

// FSM diagram (top half).
const NODE_R = 22;
type NodePos = { cx: number; cy: number };
const NODE_POS: Record<State_t, NodePos> = {
  NEW: { cx: 60, cy: 50 },
  READY: { cx: 170, cy: 50 },
  RUNNING: { cx: 280, cy: 50 },
  WAITING: { cx: 390, cy: 50 },
  ZOMBIE: { cx: 280, cy: 150 },
  TERMINATED: { cx: 390, cy: 150 },
};

// Transition edges (drawn as straight lines or quadratic curves to avoid
// overlap on the two-direction edges between RUNNING and READY).
type Edge = {
  from: State_t;
  to: State_t;
  // Path: 'L' (straight) or quadratic — we provide bend offset.
  bend?: number; // y-shift of midpoint perpendicular to the line
  // Label for the edge.
  label?: string;
};
const EDGES: Edge[] = [
  { from: "NEW", to: "READY", label: "admit" },
  { from: "READY", to: "RUNNING", bend: -14, label: "dispatch" },
  { from: "RUNNING", to: "READY", bend: 14, label: "preempt" },
  { from: "RUNNING", to: "WAITING", label: "block" },
  { from: "WAITING", to: "READY", bend: -22, label: "unblock" },
  { from: "RUNNING", to: "ZOMBIE", label: "exit" },
  { from: "ZOMBIE", to: "TERMINATED", label: "reap" },
];

// Process table (bottom half).
const TABLE_X = 10;
const TABLE_Y = 228;
const TABLE_W = 460;
const ROW_H = 22;
// Reserved data rows below the header (max processes alive concurrently = 2).
// Originally 3 rows reserved, but we shave to 2 to make room for the chip strip
// above the table without colliding with the footer below.
const TABLE_DATA_ROWS = 2;
const TABLE_INNER_PAD = 4;
const TABLE_FULL_H = ROW_H + ROW_H * TABLE_DATA_ROWS + TABLE_INNER_PAD;
type ColSpec = { key: keyof Process_t | "pid" | "ppid" | "name" | "state" | "ioReason"; label: string; x: number; w: number };
const COLS: ColSpec[] = [
  { key: "pid", label: "PID", x: TABLE_X + 8, w: 50 },
  { key: "ppid", label: "PPID", x: TABLE_X + 64, w: 50 },
  { key: "name", label: "NAME", x: TABLE_X + 120, w: 90 },
  { key: "state", label: "STATE", x: TABLE_X + 216, w: 110 },
  { key: "ioReason", label: "WAIT", x: TABLE_X + 332, w: 120 },
];

// ─── Helpers ───────────────────────────────────────────────────────────────
function edgeMidpoint(
  from: State_t,
  to: State_t,
  bend: number
): { mx: number; my: number } {
  const a = NODE_POS[from];
  const b = NODE_POS[to];
  const mx = (a.cx + b.cx) / 2;
  const my = (a.cy + b.cy) / 2;
  // Perpendicular bend (we just push the control point straight along Y for
  // simplicity — looks fine for the geometry we use).
  return { mx, my: my + bend };
}

function edgeEndpoints(
  from: State_t,
  to: State_t,
  bend: number
): { x1: number; y1: number; x2: number; y2: number } {
  const a = NODE_POS[from];
  const b = NODE_POS[to];
  // Compute the control midpoint (with bend), then aim endpoints from the
  // circle perimeter along the tangent toward that midpoint.
  const mid = edgeMidpoint(from, to, bend);
  // Vector from a → mid.
  const va = { x: mid.mx - a.cx, y: mid.my - a.cy };
  const da = Math.hypot(va.x, va.y) || 1;
  // Vector from b ← mid.
  const vb = { x: mid.mx - b.cx, y: mid.my - b.cy };
  const db = Math.hypot(vb.x, vb.y) || 1;
  const x1 = a.cx + (va.x / da) * NODE_R;
  const y1 = a.cy + (va.y / da) * NODE_R;
  const x2 = b.cx + (vb.x / db) * (NODE_R + 2); // +2 so arrowhead sits just outside
  const y2 = b.cy + (vb.y / db) * (NODE_R + 2);
  return { x1, y1, x2, y2 };
}

function edgePathD(from: State_t, to: State_t, bend: number): string {
  const { x1, y1, x2, y2 } = edgeEndpoints(from, to, bend);
  if (bend === 0 || bend === undefined) {
    return `M ${x1} ${y1} L ${x2} ${y2}`;
  }
  const mid = edgeMidpoint(from, to, bend);
  return `M ${x1} ${y1} Q ${mid.mx} ${mid.my} ${x2} ${y2}`;
}

function edgeLabelPos(
  from: State_t,
  to: State_t,
  bend: number
): { x: number; y: number; anchor: "start" | "middle" | "end" } {
  const mid = edgeMidpoint(from, to, bend);
  const a = NODE_POS[from];
  const b = NODE_POS[to];
  // Detect vertical edge (same cx) so we can offset label sideways rather than
  // placing it on top of the arrow body.
  if (a.cx === b.cx) {
    // Vertical edge — offset to the right of the line.
    return { x: mid.mx + 10, y: mid.my + 3, anchor: "start" };
  }
  // Horizontal-ish edge — offset perpendicular (along Y) far enough that the
  // text ascenders clear the (possibly active stroke=3) arrow body.
  return {
    x: mid.mx,
    y: mid.my + (bend >= 0 ? 16 : -8),
    anchor: "middle",
  };
}

function statesOccupied(processes: Process_t[]): Set<State_t> {
  return new Set(processes.map((p) => p.state));
}

function isActiveEdge(
  edge: Edge,
  active?: { from: State_t; to: State_t; pid: number }
): boolean {
  if (!active) return false;
  return edge.from === active.from && edge.to === active.to;
}

function rowOpacityForState(s: State_t): number {
  return s === "TERMINATED" ? 0.4 : 1;
}

// ─── renderFrame ───────────────────────────────────────────────────────────
function renderFrame(frame: Frame<FSMState>): ReactNode {
  const { processes, focusedPid, activeTransition, message } = frame.state;
  const occupied = statesOccupied(processes);
  const focusedProc = processes.find((p) => p.pid === focusedPid) ?? null;

  return (
    <>
      <defs>
        <marker
          id="fsm-arrow-ink"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="6"
          markerHeight="6"
          orient="auto-start-reverse"
        >
          <path d="M0,0 L10,5 L0,10 Z" fill={INK} />
        </marker>
        <marker
          id="fsm-arrow-accent"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="6"
          markerHeight="6"
          orient="auto-start-reverse"
        >
          <path d="M0,0 L10,5 L0,10 Z" fill={ACCENT} stroke={INK} strokeWidth="0.5" />
        </marker>
        <pattern
          id="fsm-zombie-hatch"
          width="6"
          height="6"
          patternUnits="userSpaceOnUse"
          patternTransform="rotate(45)"
        >
          <rect width="6" height="6" fill={PAPER} />
          <line x1="0" y1="0" x2="0" y2="6" stroke={INK} strokeWidth={1} />
        </pattern>
      </defs>

      {/* ── Pane label: FSM ──────────────────────────────────────────── */}
      <text
        x={12}
        y={20}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        STATE MACHINE
      </text>

      {/* ── Edges ────────────────────────────────────────────────────── */}
      {EDGES.map((e) => {
        const active = isActiveEdge(e, activeTransition);
        const d = edgePathD(e.from, e.to, e.bend ?? 0);
        const lp = edgeLabelPos(e.from, e.to, e.bend ?? 0);
        return (
          <g key={`edge-${e.from}-${e.to}`}>
            <path
              d={d}
              fill="none"
              stroke={active ? ACCENT : INK}
              strokeWidth={active ? 3 : 1.25}
              markerEnd={`url(#${active ? "fsm-arrow-accent" : "fsm-arrow-ink"})`}
              opacity={active ? 1 : 0.85}
            />
            {e.label && (
              <text
                x={lp.x}
                y={lp.y}
                textAnchor={lp.anchor}
                fontFamily={FONT_FAMILY}
                fontSize={8}
                fontWeight={active ? 700 : 400}
                fill={INK}
                opacity={active ? 1 : 0.65}
              >
                {e.label}
              </text>
            )}
          </g>
        );
      })}

      {/* ── State nodes ──────────────────────────────────────────────── */}
      {(Object.keys(NODE_POS) as State_t[]).map((s) => {
        const pos = NODE_POS[s];
        const isOccupied = occupied.has(s);
        const isZombie = s === "ZOMBIE";
        const isTerm = s === "TERMINATED";
        const isActiveTarget = activeTransition?.to === s;

        // Fill priorities:
        // - currently the transition target → ACCENT
        // - zombie & occupied → hatched pattern
        // - occupied → PAPER (lit)
        // - empty → white
        const baseFill = isActiveTarget
          ? ACCENT
          : isOccupied && isZombie
          ? "url(#fsm-zombie-hatch)"
          : isOccupied
          ? PAPER
          : "#ffffff";

        return (
          <g key={`node-${s}`}>
            <motion.circle
              cx={pos.cx}
              cy={pos.cy}
              r={NODE_R}
              initial={false}
              animate={{
                stroke: isActiveTarget ? INK : INK,
                strokeWidth: isOccupied || isActiveTarget ? 2.5 : 1,
              }}
              transition={{ duration: 0.4, ease: "easeInOut" }}
              fill={baseFill}
              opacity={isTerm && !isOccupied ? 0.5 : 1}
            />
            <text
              x={pos.cx}
              y={pos.cy + 3}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={8}
              fontWeight={700}
              fill={INK}
            >
              {/* Abbreviate TERMINATED inside the 22px-radius circle (10 chars
                  otherwise overflow the rim). Full word surfaced via <title>
                  tooltip + the activeTransition chip + footer narration. */}
              {s === "TERMINATED" && <title>TERMINATED</title>}
              {s === "TERMINATED" ? "TERM" : s}
            </text>
            {/* Occupancy dot — small pid chip below the node */}
            {processes
              .filter((p) => p.state === s)
              .map((p, i) => (
                <g key={`pid-${p.pid}`}>
                  <rect
                    x={pos.cx - 14 + i * 16}
                    y={pos.cy + NODE_R + 4}
                    width={14}
                    height={11}
                    fill={INK}
                  />
                  <text
                    x={pos.cx - 14 + i * 16 + 7}
                    y={pos.cy + NODE_R + 13}
                    textAnchor="middle"
                    fontFamily={FONT_FAMILY}
                    fontSize={8}
                    fontWeight={700}
                    fill={PAPER}
                  >
                    P{p.pid - 99}
                  </text>
                </g>
              ))}
          </g>
        );
      })}

      {/* ── Focus highlight chip on bottom-left of FSM ───────────────── */}
      {focusedProc && (
        <g>
          <rect
            x={TABLE_X}
            y={194}
            width={210}
            height={16}
            fill={PAPER}
            stroke={INK}
            strokeWidth={1}
          />
          <FadeText
            x={TABLE_X + 6}
            y={206}
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            fill={INK}
          >
            {`FOCUS: P${focusedProc.pid - 99} (PID ${focusedProc.pid}) · ${focusedProc.state}`}
          </FadeText>
        </g>
      )}

      {/* ── Active transition chip on bottom-right of FSM ────────────── */}
      {activeTransition && (
        <g>
          <rect
            x={TABLE_X + 240}
            y={194}
            width={220}
            height={16}
            fill={ACCENT}
            stroke={INK}
            strokeWidth={1}
          />
          <FadeText
            x={TABLE_X + 246}
            y={206}
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            fill={INK}
          >
            {`P${activeTransition.pid - 99}: ${activeTransition.from} → ${activeTransition.to}`}
          </FadeText>
        </g>
      )}

      {/* ── Pane label: process table ────────────────────────────────── */}
      <text
        x={TABLE_X + 2}
        y={TABLE_Y - 6}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        PROCESSES
      </text>

      {/* ── Process table ────────────────────────────────────────────── */}
      <rect
        x={TABLE_X}
        y={TABLE_Y}
        width={TABLE_W}
        height={TABLE_FULL_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />

      {/* Header */}
      <g>
        <rect
          x={TABLE_X}
          y={TABLE_Y}
          width={TABLE_W}
          height={ROW_H}
          fill={INK}
        />
        {COLS.map((c) => (
          <text
            key={`hdr-${c.label}`}
            x={c.x}
            y={TABLE_Y + 15}
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            fill={PAPER}
          >
            {c.label}
          </text>
        ))}
      </g>

      {/* Rows */}
      <AnimatePresence initial={false}>
        {processes.map((p, i) => {
          const rowY = TABLE_Y + ROW_H + i * ROW_H + 2;
          const isFocused = p.pid === focusedPid;
          const isZ = p.state === "ZOMBIE";
          const op = rowOpacityForState(p.state);
          return (
            <motion.g
              key={`row-${p.pid}`}
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: op, y: 0 }}
              exit={{ opacity: 0, y: 6 }}
              transition={{ duration: 0.35, ease: "easeInOut" }}
            >
              <rect
                x={TABLE_X + 1}
                y={rowY}
                width={TABLE_W - 2}
                height={ROW_H - 2}
                fill={isZ ? "url(#fsm-zombie-hatch)" : PAPER}
                stroke={isFocused ? INK : INK}
                strokeWidth={isFocused ? 2 : 0.5}
                opacity={isZ ? 0.85 : 1}
              />
              {/* PID */}
              <text
                x={COLS[0].x}
                y={rowY + 14}
                fontFamily={FONT_FAMILY}
                fontSize={10}
                fontWeight={700}
                fill={INK}
              >
                {p.pid}
              </text>
              {/* PPID */}
              <text
                x={COLS[1].x}
                y={rowY + 14}
                fontFamily={FONT_FAMILY}
                fontSize={10}
                fill={INK}
              >
                {p.ppid}
              </text>
              {/* NAME */}
              <text
                x={COLS[2].x}
                y={rowY + 14}
                fontFamily={FONT_FAMILY}
                fontSize={10}
                fill={INK}
              >
                {p.name}
              </text>
              {/* STATE */}
              <FadeText
                x={COLS[3].x}
                y={rowY + 14}
                fontFamily={FONT_FAMILY}
                fontSize={10}
                fontWeight={700}
                fill={INK}
              >
                {p.state}
              </FadeText>
              {/* WAIT / reason */}
              <FadeText
                x={COLS[4].x}
                y={rowY + 14}
                fontFamily={FONT_FAMILY}
                fontSize={9}
                fill={INK}
                opacity={0.75}
              >
                {p.ioReason ?? "—"}
              </FadeText>
            </motion.g>
          );
        })}
      </AnimatePresence>

      {/* ── Footer message ──────────────────────────────────────────── */}
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

// ─── Footer (verbatim 2-line word-wrap from TcpCwnd) ───────────────────────
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
  // Preserve the joining space between line1's tail word and line2's lead word
  // so screen readers / TTS / textContent reads "the grandparent" rather than
  // "thegrandparent". The trailing space is invisible at the wrap boundary.
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

// ─── Exported wrapper ──────────────────────────────────────────────────────
export function ProcessFSM(): ReactNode {
  return (
    <AlgoStepperShell<FSMState>
      title="SO-1 · Process lifecycle FSM (with zombie)"
      desc="Fork / wait scenario walking a parent and child through NEW → READY → RUNNING → WAITING → ZOMBIE → TERMINATED. Hatched node = ZOMBIE: process has exited but its PCB lingers until the parent reaps the exit status."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="process-fsm"
    />
  );
}
