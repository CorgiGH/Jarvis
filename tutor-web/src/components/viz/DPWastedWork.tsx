import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";
import {
  AnimatePresence,
  DrawLine,
  DrawPath,
  FadeText,
  PopIn,
  motion,
} from "./motion-helpers";

const N = 5;

type DPCell = { idx: number; value: number | null };
type DPTable = DPCell[];
type TreeNode = {
  id: number;
  n: number;
  parentId: number | null;
  depth: number;
  childIds: number[];
  value: number | null;
  status: "pending" | "returned";
};
type DuplicateMap = Record<number, number>;
type DPWastedState = {
  phase: "DP" | "NAIVE";
  dpTable: DPTable;
  currentDpIdx: number | null;
  tree: TreeNode[];
  stack: number[];
  currentTreeNodeId: number | null;
  duplicates: DuplicateMap;
};

function buildFrames(): Frame<DPWastedState>[] {
  const frames: Frame<DPWastedState>[] = [];

  // Initial: empty DP table, no tree
  const initialDp: DPTable = Array.from({ length: N + 1 }, (_, i) => ({
    idx: i,
    value: null,
  }));

  // PHASE 1 — DP fill: frames 0..5 (6 frames)
  const dpAfter: DPTable = initialDp.map((c) => ({ ...c }));
  for (let i = 0; i <= N; i++) {
    if (i === 0) dpAfter[0].value = 0;
    else if (i === 1) dpAfter[1].value = 1;
    else dpAfter[i].value = (dpAfter[i - 1].value ?? 0) + (dpAfter[i - 2].value ?? 0);

    frames.push({
      state: {
        phase: "DP",
        dpTable: dpAfter.map((c) => ({ ...c })),
        currentDpIdx: i,
        tree: [],
        stack: [],
        currentTreeNodeId: null,
        duplicates: {},
      },
      aria: `DP phase: fill dp[${i}] = ${dpAfter[i].value}`,
    });
  }

  // PHASE 2 — naive fib(5) trace: frames 6..35 (30 frames)
  const tree: TreeNode[] = [];
  const stack: number[] = [];
  const duplicates: DuplicateMap = {};
  let nextId = 0;

  function pushNaiveFrame(currentTreeNodeId: number | null, aria: string) {
    frames.push({
      state: {
        phase: "NAIVE",
        dpTable: dpAfter.map((c) => ({ ...c })),
        currentDpIdx: null,
        tree: tree.map((n) => ({ ...n, childIds: [...n.childIds] })),
        stack: [...stack],
        currentTreeNodeId,
        duplicates: { ...duplicates },
      },
      aria,
    });
  }

  function fib(n: number, parentId: number | null, depth: number): number {
    const id = nextId++;
    duplicates[n] = (duplicates[n] ?? 0) + 1;
    const node: TreeNode = {
      id,
      n,
      parentId,
      depth,
      childIds: [],
      value: null,
      status: "pending",
    };
    tree.push(node);
    if (parentId !== null) {
      const parent = tree.find((t) => t.id === parentId);
      if (parent) parent.childIds.push(id);
    }
    stack.push(id);
    pushNaiveFrame(id, `Naïve call fib(${n}). Called ${duplicates[n]}× so far.`);

    let result: number;
    if (n <= 1) {
      result = n;
    } else {
      const left = fib(n - 1, id, depth + 1);
      const right = fib(n - 2, id, depth + 1);
      result = left + right;
    }

    const self = tree.find((t) => t.id === id)!;
    self.value = result;
    self.status = "returned";
    stack.pop();
    const wastedEntries = Object.entries(duplicates)
      .filter(([, v]) => v >= 2)
      .map(([k, v]) => `fib(${k})×${v}`)
      .join(", ");
    pushNaiveFrame(
      id,
      `Return fib(${n}) = ${result}.${wastedEntries ? ` Wasted: ${wastedEntries}.` : ""}`
    );

    return result;
  }

  fib(N, null, 0);
  return frames;
}

const FRAMES = buildFrames();

// Export frame count for tests
export const FRAME_COUNT = FRAMES.length;

// Layout constants — must fit within AlgoStepperShell's fixed viewBox="0 0 480 360"
const SVG_W = 480;
const SVG_H = 360;

// LEFT — DP table pane (x 0..175)
const DP_X = 8;
const DP_Y = 52;
const DP_CELL_W = 25;
const DP_CELL_H = 25;
const DP_GAP = 2;
const DP_LABEL_Y = 18;

// RIGHT — naive tree pane (x 185..475)
const TREE_X = 188;
const TREE_X_END = 475;
const TREE_Y_TOP = 52;
const TREE_NODE_R = 11;
const TREE_LEVEL_GAP = 44;

// BOTTOM — wasted-work tally
const TALLY_Y = SVG_H - 30;

function dupShade(count: number): { fill: string; fillOpacity: number } {
  // 1 call = white; 2+ calls → increasingly yellow (ACCENT)
  if (count <= 1) return { fill: "#fff", fillOpacity: 1 };
  // max observable for fib(5): fib(1) called 8 times, fib(2) 5x, fib(3) 3x
  const opacity = Math.min(1, 0.25 + (count - 1) * 0.18);
  return { fill: ACCENT, fillOpacity: opacity };
}

function renderFrame(frame: Frame<DPWastedState>): ReactNode {
  const {
    phase,
    dpTable,
    currentDpIdx,
    tree,
    currentTreeNodeId,
    duplicates,
  } = frame.state;

  // --- Layout tree by depth ---
  const nodesByDepth = new Map<number, TreeNode[]>();
  tree.forEach((n) => {
    const list = nodesByDepth.get(n.depth) ?? [];
    list.push(n);
    nodesByDepth.set(n.depth, list);
  });
  const positions = new Map<number, { x: number; y: number }>();
  nodesByDepth.forEach((list, depth) => {
    const usableW = TREE_X_END - TREE_X;
    const step = usableW / Math.max(list.length, 1);
    list.forEach((node, i) => {
      const x = TREE_X + step * (i + 0.5);
      const y = TREE_Y_TOP + depth * TREE_LEVEL_GAP;
      positions.set(node.id, { x, y });
    });
  });

  // --- Divider x — split between DP pane (left) and tree pane (right) ---
  const DIVIDER_X = 182;

  // --- Wasted-work entries ---
  const wastedEntries = Object.entries(duplicates)
    .filter(([, count]) => count >= 2)
    .sort(([a], [b]) => Number(a) - Number(b));

  return (
    <>
      {/* Pane separator */}
      <line
        x1={DIVIDER_X}
        y1={8}
        x2={DIVIDER_X}
        y2={SVG_H - 8}
        stroke={INK}
        strokeWidth={1}
        opacity={0.25}
      />

      {/* Pane labels */}
      <text
        x={DP_X}
        y={DP_LABEL_Y}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.75}
      >
        DP TABLE (linear)
      </text>
      <text
        x={TREE_X}
        y={DP_LABEL_Y}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.75}
      >
        NAÏVE RECURSION (exponential)
      </text>

      {/* Phase indicator */}
      <text
        x={DP_X}
        y={DP_LABEL_Y + 13}
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        opacity={0.5}
      >
        Phase: {phase}
      </text>

      {/* ---- DP TABLE ---- */}
      {dpTable.map((cell) => {
        const x = DP_X + cell.idx * (DP_CELL_W + DP_GAP);
        const isCurrent = cell.idx === currentDpIdx;
        const isFilled = cell.value !== null;
        const targetFill = isCurrent ? ACCENT : isFilled ? "#fff" : PAPER;
        return (
          <g key={`dp-${cell.idx}`}>
            <motion.rect
              x={x}
              y={DP_Y}
              width={DP_CELL_W}
              height={DP_CELL_H}
              stroke={INK}
              initial={false}
              animate={{ fill: targetFill, strokeWidth: isCurrent ? 2 : 1 }}
              transition={{ duration: 0.4, ease: "easeInOut" }}
            />
            <AnimatePresence>
              {isFilled ? (
                <PopIn key={`dp-val-${cell.idx}`} durationMs={300}>
                  <FadeText
                    x={x + DP_CELL_W / 2}
                    y={DP_Y + DP_CELL_H / 2 + 4}
                    textAnchor="middle"
                    fontFamily={FONT_FAMILY}
                    fontSize={11}
                    fontWeight={700}
                    fill={INK}
                  >
                    {String(cell.value ?? 0)}
                  </FadeText>
                </PopIn>
              ) : (
                <text
                  key={`dp-dot-${cell.idx}`}
                  x={x + DP_CELL_W / 2}
                  y={DP_Y + DP_CELL_H / 2 + 4}
                  textAnchor="middle"
                  fontFamily={FONT_FAMILY}
                  fontSize={11}
                  fontWeight={700}
                  fill={INK}
                >
                  ·
                </text>
              )}
            </AnimatePresence>
            <text
              x={x + DP_CELL_W / 2}
              y={DP_Y + DP_CELL_H + 11}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={8}
              fill={INK}
              opacity={0.6}
            >
              [{cell.idx}]
            </text>
          </g>
        );
      })}

      {/* DP dependency arrows: dp[k] ← dp[k-1] + dp[k-2] for filled k ≥ 2 */}
      <AnimatePresence>
        {dpTable
          .filter((c) => c.idx >= 2 && c.value !== null)
          .map((cell) => {
            const k = cell.idx;
            const xCenter = (i: number) =>
              DP_X + i * (DP_CELL_W + DP_GAP) + DP_CELL_W / 2;
            const yTop = DP_Y - 3;
            return (
              <PopIn key={`arrow-${k}`} durationMs={300}>
                {/* dp[k-1] → dp[k] */}
                <DrawPath
                  d={`M ${xCenter(k - 1)} ${yTop} C ${xCenter(k - 1)} ${yTop - 8}, ${xCenter(k)} ${yTop - 8}, ${xCenter(k)} ${yTop}`}
                  fill="none"
                  stroke={INK}
                  strokeWidth={1}
                  opacity={0.35}
                  durationMs={400}
                />
                {/* dp[k-2] → dp[k] (bigger arc) */}
                <DrawPath
                  d={`M ${xCenter(k - 2)} ${yTop} C ${xCenter(k - 2)} ${yTop - 16}, ${xCenter(k)} ${yTop - 16}, ${xCenter(k)} ${yTop}`}
                  fill="none"
                  stroke={INK}
                  strokeWidth={1}
                  opacity={0.25}
                  durationMs={400}
                  delayMs={150}
                />
              </PopIn>
            );
          })}
      </AnimatePresence>

      {/* ---- NAIVE TREE ---- */}

      {/* Tree edges */}
      <AnimatePresence>
        {tree.flatMap((node) => {
          if (node.parentId === null) return [];
          const from = positions.get(node.parentId);
          const to = positions.get(node.id);
          if (!from || !to) return [];
          return [
            <DrawLine
              key={`edge-${node.parentId}-${node.id}`}
              x1={from.x}
              y1={from.y + TREE_NODE_R}
              x2={to.x}
              y2={to.y - TREE_NODE_R}
              stroke={INK}
              strokeWidth={1}
              opacity={node.status === "returned" ? 0.35 : 0.75}
              durationMs={350}
            />,
          ];
        })}
      </AnimatePresence>

      {/* Tree nodes — shaded by duplicate count */}
      <AnimatePresence>
        {tree.map((node) => {
          const pos = positions.get(node.id);
          if (!pos) return null;
          const isCurrent = node.id === currentTreeNodeId;
          const dupCount = duplicates[node.n] ?? 1;
          const { fill, fillOpacity } = dupShade(dupCount);
          const targetFill = isCurrent ? ACCENT : fill;
          const targetFillOpacity = isCurrent ? 1 : fillOpacity;
          return (
            <PopIn key={`node-${node.id}`} durationMs={300}>
              <motion.circle
                cx={pos.x}
                cy={pos.y}
                r={TREE_NODE_R}
                stroke={INK}
                initial={false}
                animate={{
                  fill: targetFill,
                  fillOpacity: targetFillOpacity,
                  strokeWidth: isCurrent ? 2 : 1,
                }}
                transition={{ duration: 0.4, ease: "easeInOut" }}
              />
              <FadeText
                x={pos.x}
                y={pos.y + 4}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={9}
                fontWeight={700}
                fill={INK}
              >
                {node.value !== null ? String(node.value) : String(node.n)}
              </FadeText>
            </PopIn>
          );
        })}
      </AnimatePresence>

      {/* ---- WASTED-WORK TALLY ---- */}
      <FadeText
        x={DP_X}
        y={TALLY_Y}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.75}
      >
        {`WASTED WORK: ${
          wastedEntries.length > 0
            ? wastedEntries.map(([n, count]) => `fib(${n})×${count}`).join("  ")
            : "(none yet)"
        }`}
      </FadeText>
      <FadeText
        x={DP_X}
        y={TALLY_Y + 14}
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        opacity={0.5}
      >
        {`DP: ${N + 1} cells computed. ${
          tree.length > 0
            ? `Naïve: ${tree.length} calls so far.`
            : "Naïve phase not started."
        }`}
      </FadeText>

      {/* Bounds anchor — keeps coords within the shell's 480×360 viewBox */}
      <rect x={0} y={0} width={480} height={360} fill="none" stroke="none" />
    </>
  );
}

export function DPWastedWork(): ReactNode {
  return (
    <AlgoStepperShell<DPWastedState>
      title="PA-3 · DP vs naïve recursion — wasted-work overlay"
      desc="Fib(5) two ways. Left: DP table fills in linear time, O(N). Right: naïve recursion tree, with duplicate subproblems shaded — same nodes get recomputed exponentially."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="dp-wasted-work"
    />
  );
}
