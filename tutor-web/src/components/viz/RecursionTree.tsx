import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "./theme";
import {
  AnimatePresence,
  FadeText,
  PopIn,
  motion,
} from "./motion-helpers";

type CallStep = {
  kind: "CALL" | "RETURN";
  nodeId: number;
  n: number;
  value?: number;
};

type TreeNode = {
  id: number;
  n: number;
  parentId: number | null;
  depth: number;
  // children ordered left-to-right by creation order
  childIds: number[];
  value: number | null;
  status: "pending" | "returned";
};

type RecursionState = {
  step: CallStep;
  stack: number[];     // node ids
  tree: TreeNode[];    // all nodes created so far
};

/**
 * Build the fib(N) execution trace.
 * Returns one Frame per CALL or RETURN step.
 */
function buildFibTrace(N: number): Frame<RecursionState>[] {
  const frames: Frame<RecursionState>[] = [];
  const tree: TreeNode[] = [];
  const stack: number[] = [];
  let nextId = 0;

  function pushFrame(step: CallStep, aria: string) {
    frames.push({
      state: {
        step,
        stack: [...stack],
        tree: tree.map((n) => ({ ...n, childIds: [...n.childIds] })),
      },
      aria,
    });
  }

  function fib(n: number, parentId: number | null, depth: number): number {
    const id = nextId++;
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
    pushFrame(
      { kind: "CALL", nodeId: id, n },
      `Call fib(${n}). Stack depth ${stack.length}.`
    );

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
    pushFrame(
      { kind: "RETURN", nodeId: id, n, value: result },
      `Return fib(${n}) = ${result}. ${stack.length === 0 ? "Done." : `Stack depth ${stack.length}.`}`
    );

    return result;
  }

  fib(N, null, 0);
  return frames;
}

const FRAMES = buildFibTrace(5);

// Layout constants
const SVG_H = 360;

// Stack pane (left)
const STACK_X = 10;
const STACK_Y_TOP = 30;
const STACK_W = 130;
const STACK_FRAME_H = 28;
const STACK_FRAME_GAP = 4;
const STACK_LABEL_X = 20;
const STACK_LABEL_Y = 18;

// Tree pane (right)
const TREE_X = 160;
const TREE_Y_TOP = 40;
const TREE_X_END = 470;
const TREE_NODE_R = 14;
const TREE_LEVEL_GAP = 50;

function renderFrame(frame: Frame<RecursionState>): ReactNode {
  const { stack, tree } = frame.state;
  const currentId = frame.state.step.nodeId;

  // Layout: group nodes by depth, distribute evenly at each depth level
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

  return (
    <>
      {/* Pane labels (static) */}
      <text
        x={STACK_LABEL_X}
        y={STACK_LABEL_Y}
        fontFamily={FONT_FAMILY}
        fontSize={11}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        CALL STACK
      </text>
      <text
        x={TREE_X + 10}
        y={STACK_LABEL_Y}
        fontFamily={FONT_FAMILY}
        fontSize={11}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        RECURSION TREE
      </text>

      {/* Separator */}
      <line
        x1={TREE_X - 10}
        y1={10}
        x2={TREE_X - 10}
        y2={SVG_H - 10}
        stroke={INK}
        strokeWidth={1}
        opacity={0.3}
      />

      {/* Stack cards — newest at top of screen, slide as stack grows/shrinks.
          AnimatePresence handles fade-in on push + fade-out on pop. */}
      <AnimatePresence>
        {stack.map((nodeId, i) => {
          const node = tree.find((t) => t.id === nodeId);
          if (!node) return null;
          // i=0 oldest (bottom of stack), i=stack.length-1 newest (top)
          // Render newest at top of screen (smallest y)
          const idxFromTop = stack.length - 1 - i;
          const cardY = STACK_Y_TOP + idxFromTop * (STACK_FRAME_H + STACK_FRAME_GAP);
          const isCurrent = nodeId === currentId;
          return (
            <PopIn key={`stack-${nodeId}`}>
              <motion.g
                initial={false}
                animate={{ y: cardY }}
                transition={{ duration: 0.4, ease: "easeInOut" }}
              >
                <motion.rect
                  x={STACK_X}
                  y={0}
                  width={STACK_W}
                  height={STACK_FRAME_H}
                  initial={false}
                  animate={{
                    fill: isCurrent ? ACCENT : "#fff",
                    strokeWidth: isCurrent ? 2 : 1,
                  }}
                  transition={{ duration: 0.4 }}
                  stroke={INK}
                />
                <text
                  x={STACK_X + 8}
                  y={STACK_FRAME_H / 2 + 4}
                  fontFamily={FONT_FAMILY}
                  fontSize={11}
                  fontWeight={700}
                  fill={INK}
                >
                  fib({node.n})
                </text>
                <AnimatePresence>
                  {node.value !== null && (
                    <PopIn key={`stack-val-${nodeId}`}>
                      <FadeText
                        x={STACK_X + STACK_W - 8}
                        y={STACK_FRAME_H / 2 + 4}
                        textAnchor="end"
                        fontFamily={FONT_FAMILY}
                        fontSize={11}
                        fill={INK}
                        opacity={0.7}
                      >
                        {`= ${node.value}`}
                      </FadeText>
                    </PopIn>
                  )}
                </AnimatePresence>
              </motion.g>
            </PopIn>
          );
        })}
      </AnimatePresence>

      {/* Tree edges (parent -> child) — endpoints slide smoothly when nodes
          shift between frames (motion.line animates x1/y1/x2/y2 via framer-
          motion); new edges fade in via opacity; returned subtrees fade to 0.4. */}
      <AnimatePresence>
        {tree.flatMap((node) => {
          if (node.parentId === null) return [];
          const from = positions.get(node.parentId);
          const to = positions.get(node.id);
          if (!from || !to) return [];
          const targetOpacity = node.status === "returned" ? 0.4 : 0.8;
          const x1 = from.x;
          const y1 = from.y + TREE_NODE_R;
          const x2 = to.x;
          const y2 = to.y - TREE_NODE_R;
          return [
            <motion.line
              key={`edge-${node.parentId}-${node.id}`}
              initial={{ opacity: 0, x1, y1, x2, y2 }}
              animate={{ opacity: targetOpacity, x1, y1, x2, y2 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.5, ease: "easeInOut" }}
              stroke={INK}
              strokeWidth={1}
            />,
          ];
        })}
      </AnimatePresence>

      {/* Tree nodes — pop in on creation, slide smoothly when layout shifts.
          Whole node (circle + label + sub-text) wrapped in motion.g animating
          its translate so the label slides in lockstep with the circle. */}
      <AnimatePresence>
        {tree.map((node) => {
          const pos = positions.get(node.id);
          if (!pos) return null;
          const isCurrent = node.id === currentId;
          const returned = node.status === "returned";
          return (
            <PopIn key={`node-${node.id}`}>
              <motion.g
                initial={false}
                animate={{ x: pos.x, y: pos.y }}
                transition={{ duration: 0.5, ease: "easeInOut" }}
              >
                <motion.circle
                  cx={0}
                  cy={0}
                  initial={false}
                  animate={{
                    fill: isCurrent ? ACCENT : "#fff",
                    strokeWidth: isCurrent ? 2 : 1,
                    opacity: returned ? 1 : 0.85,
                  }}
                  transition={{ duration: 0.5, ease: "easeInOut" }}
                  r={TREE_NODE_R}
                  stroke={INK}
                />
                <FadeText
                  x={0}
                  y={4}
                  textAnchor="middle"
                  fontFamily={FONT_FAMILY}
                  fontSize={10}
                  fontWeight={700}
                  fill={INK}
                >
                  {String(node.value !== null ? node.value : node.n)}
                </FadeText>
                <AnimatePresence>
                  {node.value === null && (
                    <PopIn key={`node-sub-${node.id}`}>
                      <text
                        x={0}
                        y={TREE_NODE_R + 11}
                        textAnchor="middle"
                        fontFamily={FONT_FAMILY}
                        fontSize={8}
                        fill={INK}
                        opacity={0.6}
                      >
                        fib({node.n})
                      </text>
                    </PopIn>
                  )}
                </AnimatePresence>
              </motion.g>
            </PopIn>
          );
        })}
      </AnimatePresence>
    </>
  );
}

export function RecursionTree(): ReactNode {
  return (
    <AlgoStepperShell<RecursionState>
      title="PA-1 · Recursion tree (fib 5)"
      desc="Naïve recursive fibonacci. Left pane shows call stack growing/shrinking; right pane shows the recursion tree fanning out. Yellow node = current call. Greyed edges = returned subtrees."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="recursion-tree"
    />
  );
}

// Export frame count for tests
export const FRAME_COUNT = FRAMES.length;
