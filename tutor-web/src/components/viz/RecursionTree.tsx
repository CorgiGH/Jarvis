import { useEffect, useMemo, useRef, type ReactNode } from "react";
import {
  animate,
  motionValue,
  useTransform,
  type MotionValue,
} from "motion/react";
import { AlgoStepperShell, type Frame, type ShellLayout } from "./AlgoStepperShell";
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

// Per-node shared MotionValues for x/y. Both the circle's motion.g translate
// AND the edges' x1/y1/x2/y2 read from the SAME MotionValue so they're
// physically incapable of drifting out of sync.
type NodeMV = { x: MotionValue<number>; y: MotionValue<number> };

function useNodePositionMVs(
  tree: TreeNode[],
  positions: Map<number, { x: number; y: number }>,
): Map<number, NodeMV> {
  const mvMapRef = useRef<Map<number, NodeMV>>(new Map());

  // Lazy-create MotionValues for any new node, initialised to the current
  // target so first paint is at the right spot.
  tree.forEach((node) => {
    if (!mvMapRef.current.has(node.id)) {
      const pos = positions.get(node.id);
      if (pos) {
        mvMapRef.current.set(node.id, {
          x: motionValue(pos.x),
          y: motionValue(pos.y),
        });
      }
    }
  });

  // Retarget every node's MotionValue to its current frame's position.
  // Runs after render so motion-driven values catch up to the latest layout.
  useEffect(() => {
    const controls: Array<{ stop: () => void }> = [];
    tree.forEach((node) => {
      const pos = positions.get(node.id);
      const mv = mvMapRef.current.get(node.id);
      if (!pos || !mv) return;
      controls.push(
        animate(mv.x, pos.x, { duration: 0.5, ease: "easeInOut" }),
      );
      controls.push(
        animate(mv.y, pos.y, { duration: 0.5, ease: "easeInOut" }),
      );
    });
    return () => controls.forEach((c) => c.stop());
  });

  return mvMapRef.current;
}

function TreeEdge({
  parentMv,
  childMv,
  status,
}: {
  parentMv: NodeMV;
  childMv: NodeMV;
  status: "pending" | "returned";
}) {
  // Derive line endpoints from the SAME MotionValues the circles use. As
  // motion's RAF tick updates each MV, the line endpoints update in the
  // same tick — guaranteed lockstep with the circles at the ends.
  const y1 = useTransform(parentMv.y, (v) => v + TREE_NODE_R);
  const y2 = useTransform(childMv.y, (v) => v - TREE_NODE_R);
  const targetOpacity = status === "returned" ? 0.4 : 0.8;
  return (
    <motion.line
      x1={parentMv.x}
      y1={y1}
      x2={childMv.x}
      y2={y2}
      initial={{ opacity: 0 }}
      animate={{ opacity: targetOpacity }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.5, ease: "easeInOut" }}
      stroke={INK}
      strokeWidth={1}
    />
  );
}

function TreeNodeGlyph({
  node,
  mv,
  isCurrent,
}: {
  node: TreeNode;
  mv: NodeMV;
  isCurrent: boolean;
}) {
  // Every visible element of the node binds its SVG attribute directly to
  // the same MotionValues the edge endpoints read from. No motion.g style
  // transforms involved — every element uses the same setAttribute path,
  // so the browser paints them in lockstep at every RAF tick.
  const labelY = useTransform(mv.y, (v) => v + 4);
  const subLabelY = useTransform(mv.y, (v) => v + TREE_NODE_R + 11);
  const returned = node.status === "returned";
  return (
    <>
      {/* No animate/transition prop -- those route through motion's
          animation system and were giving cx/cy slightly different paint
          timing than the line endpoints bound to the same MotionValue.
          fill/strokeWidth/opacity now snap as plain SVG attrs; cx/cy is
          a pure MotionValue subscription, same setAttribute path as the
          line endpoints. */}
      <motion.circle
        cx={mv.x}
        cy={mv.y}
        r={TREE_NODE_R}
        fill={isCurrent ? ACCENT : "#fff"}
        stroke={INK}
        strokeWidth={isCurrent ? 2 : 1}
        opacity={returned ? 1 : 0.85}
      />
      <FadeText
        x={mv.x as unknown as number}
        y={labelY as unknown as number}
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
            <motion.text
              x={mv.x}
              y={subLabelY}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={8}
              fill={INK}
              opacity={0.6}
            >
              fib({node.n})
            </motion.text>
          </PopIn>
        )}
      </AnimatePresence>
    </>
  );
}

function TreePane({
  tree,
  currentId,
  positions,
}: {
  tree: TreeNode[];
  currentId: number;
  positions: Map<number, { x: number; y: number }>;
}) {
  const mvMap = useNodePositionMVs(tree, positions);

  return (
    <>
      <AnimatePresence>
        {tree.flatMap((node) => {
          if (node.parentId === null) return [];
          const parentMv = mvMap.get(node.parentId);
          const childMv = mvMap.get(node.id);
          if (!parentMv || !childMv) return [];
          return [
            <TreeEdge
              key={`edge-${node.parentId}-${node.id}`}
              parentMv={parentMv}
              childMv={childMv}
              status={node.status}
            />,
          ];
        })}
      </AnimatePresence>

      <AnimatePresence>
        {tree.map((node) => {
          const mv = mvMap.get(node.id);
          if (!mv) return null;
          const isCurrent = node.id === currentId;
          return (
            <PopIn key={`node-${node.id}`}>
              <TreeNodeGlyph node={node} mv={mv} isCurrent={isCurrent} />
            </PopIn>
          );
        })}
      </AnimatePresence>
    </>
  );
}

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
          const idxFromTop = stack.length - 1 - i;
          const cardY = STACK_Y_TOP + idxFromTop * (STACK_FRAME_H + STACK_FRAME_GAP);
          const isCurrent = nodeId === currentId;
          return (
            <PopIn key={`stack-${nodeId}`}>
              <motion.g
                initial={false}
                animate={{ y: cardY }}
                transition={{ type: "tween", duration: 0.4, ease: "easeInOut" }}
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

      <TreePane tree={tree} currentId={currentId} positions={positions} />
    </>
  );
}

export function RecursionTree({ n = 5, layout }: { n?: number; layout?: ShellLayout } = {}): ReactNode {
  // Parametric: data comes from a prop, not a hardcoded buildFibTrace(5).
  // Default n=5 preserves the original zero-prop registry behaviour.
  const frames = useMemo(() => (n === 5 ? FRAMES : buildFibTrace(n)), [n]);
  return (
    <AlgoStepperShell<RecursionState>
      title={`PA-1 · Recursion tree (fib ${n})`}
      desc="Naïve recursive fibonacci. Left pane shows call stack growing/shrinking; right pane shows the recursion tree fanning out. Yellow node = current call. Greyed edges = returned subtrees."
      frames={frames}
      renderFrame={renderFrame}
      testIdPrefix="recursion-tree"
      layout={layout}
    />
  );
}

// Export frame count for tests
export const FRAME_COUNT = FRAMES.length;
