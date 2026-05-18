import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

type Literal = { var: number; negated: boolean };
type Node = {
  id: number;
  clauseIdx: number;
  literal: Literal;
  inClique: boolean;
};
type Edge = { from: number; to: number };

type NPState = {
  step: number;
  phase: string;
  nodes: Node[];
  edges: Edge[];
  showEdges: boolean;
  assignment: Record<number, boolean>;
  cliqueNodes: number[];
  message: string;
};

// φ = (x1 ∨ x2 ∨ ¬x3) ∧ (¬x1 ∨ x2 ∨ x3) ∧ (x1 ∨ ¬x2 ∨ ¬x3)
const CLAUSES: Literal[][] = [
  [{ var: 1, negated: false }, { var: 2, negated: false }, { var: 3, negated: true }],
  [{ var: 1, negated: true }, { var: 2, negated: false }, { var: 3, negated: false }],
  [{ var: 1, negated: false }, { var: 2, negated: true }, { var: 3, negated: true }],
];

function buildNodes(): Node[] {
  const nodes: Node[] = [];
  let id = 0;
  CLAUSES.forEach((clause, ci) => {
    clause.forEach((lit) => {
      nodes.push({ id: id++, clauseIdx: ci, literal: lit, inClique: false });
    });
  });
  return nodes;
}

function contradict(a: Literal, b: Literal): boolean {
  return a.var === b.var && a.negated !== b.negated;
}

function buildEdges(nodes: Node[]): Edge[] {
  const edges: Edge[] = [];
  for (let i = 0; i < nodes.length; i++) {
    for (let j = i + 1; j < nodes.length; j++) {
      if (nodes[i].clauseIdx === nodes[j].clauseIdx) continue;
      if (contradict(nodes[i].literal, nodes[j].literal)) continue;
      edges.push({ from: nodes[i].id, to: nodes[j].id });
    }
  }
  return edges;
}

function literalToStr(lit: Literal): string {
  return lit.negated ? `¬x${lit.var}` : `x${lit.var}`;
}

function evalLit(lit: Literal, asn: Record<number, boolean>): boolean {
  const v = asn[lit.var];
  return lit.negated ? !v : v;
}

function buildFrames(): Frame<NPState>[] {
  const nodes = buildNodes();
  const edges = buildEdges(nodes);
  const asn = { 1: true, 2: true, 3: false };  // satisfying assignment
  const frames: Frame<NPState>[] = [];

  const mk = (
    step: number,
    phase: string,
    showEdges: boolean,
    assignment: Record<number, boolean>,
    cliqueNodes: number[],
    message: string,
  ): Frame<NPState> => ({
    state: {
      step,
      phase,
      nodes: nodes.map((n) => ({ ...n, inClique: cliqueNodes.includes(n.id) })),
      edges,
      showEdges,
      assignment: { ...assignment },
      cliqueNodes: [...cliqueNodes],
      message,
    },
    aria: message,
  });

  // Pick TRUE literal per clause for the forward-direction clique
  const cliqueIds: number[] = [];
  CLAUSES.forEach((clause, ci) => {
    for (let li = 0; li < clause.length; li++) {
      if (evalLit(clause[li], asn)) {
        const found = nodes.find(
          (n) => n.clauseIdx === ci && n.literal.var === clause[li].var && n.literal.negated === clause[li].negated
        );
        if (found) {
          cliqueIds.push(found.id);
          break;
        }
      }
    }
  });

  frames.push(mk(0, "intro", false, {}, [], "3-SAT instance φ. 3 clauses, 3 variables. Goal: show φ satisfiable IFF G has 3-clique."));
  frames.push(mk(1, "build-graph", false, {}, [], "Build G: one node per literal-occurrence in φ. 9 nodes, grouped by clause."));
  frames.push(mk(2, "build-edges", true, {}, [], `Add edges: connect nodes in DIFFERENT clauses that DON'T contradict. ${edges.length} edges total.`));
  frames.push(mk(3, "forward", true, {}, [], "→ Forward: assume φ satisfied by some assignment α."));
  frames.push(mk(4, "forward", true, asn, [], "Pick assignment α = {x1=T, x2=T, x3=F}. Each clause has ≥1 TRUE literal."));
  frames.push(mk(5, "forward-clique", true, asn, cliqueIds, "Pick one TRUE literal per clause → 3 nodes selected."));
  frames.push(mk(6, "forward-clique", true, asn, cliqueIds, "These 3 are pairwise connected: different clauses (by construction) + non-contradicting (all evaluate TRUE under α) → 3-CLIQUE."));
  frames.push(mk(7, "reverse", true, {}, cliqueIds, "← Reverse: assume G has 3-clique C = {c1, c2, c3}."));
  frames.push(mk(8, "reverse-assignment", true, asn, cliqueIds, "Clique edges → different clauses + non-contradicting → read assignment from the literals."));
  frames.push(mk(9, "verify", true, asn, cliqueIds, "Verify: each clause has a TRUE literal in the clique → φ satisfied under that assignment."));
  frames.push(mk(10, "conclude", true, asn, cliqueIds, "Both directions: φ ∈ 3-SAT ⟺ G has k-clique. The reduction is polynomial: O(n) nodes, O(n²) edges for n clauses."));
  frames.push(mk(11, "conclude", true, asn, cliqueIds, "∴ 3-SAT ≤ₚ CLIQUE. Since 3-SAT is NP-hard, CLIQUE is NP-hard."));

  return frames;
}

const FRAMES = buildFrames();

export const FRAME_COUNT = FRAMES.length;

// Layout 480x360
const FORMULA_X = 10;
const FORMULA_Y = 30;
const FORMULA_W = 200;
const FORMULA_H = 220;

const GRAPH_X = 230;
const GRAPH_Y = 30;
const GRAPH_W = 240;
const GRAPH_H = 220;

const MSG_Y = 340;

// 3 clusters at fixed positions in graph pane
function clusterCenter(clauseIdx: number): { x: number; y: number } {
  const cx = GRAPH_X + GRAPH_W / 2;
  const cy = GRAPH_Y + GRAPH_H / 2;
  const radius = 70;
  const angle = (-Math.PI / 2) + clauseIdx * (2 * Math.PI / 3);
  return { x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) };
}

function nodePosition(node: Node): { x: number; y: number } {
  const center = clusterCenter(node.clauseIdx);
  // 3 nodes per cluster, arranged in small triangle
  const offsetIdx = node.id % 3;
  const offsetRadius = 18;
  const offsetAngle = -Math.PI / 2 + offsetIdx * (2 * Math.PI / 3);
  return {
    x: center.x + offsetRadius * Math.cos(offsetAngle),
    y: center.y + offsetRadius * Math.sin(offsetAngle),
  };
}

function renderFrame(frame: Frame<NPState>): ReactNode {
  const { phase, nodes, edges, showEdges, assignment, cliqueNodes, message } = frame.state;

  // Determine which clauses are "satisfied" under current assignment
  const clauseSat = CLAUSES.map((c) =>
    Object.keys(assignment).length > 0 && c.some((lit) => evalLit(lit, assignment))
  );

  return (
    <>
      {/* Pane labels */}
      <text x={FORMULA_X} y={FORMULA_Y - 12} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK} opacity={0.7}>3-SAT φ</text>
      <text x={GRAPH_X} y={GRAPH_Y - 12} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK} opacity={0.7}>GRAPH G — find k-CLIQUE (k = 3)</text>

      {/* Formula pane */}
      <rect x={FORMULA_X} y={FORMULA_Y} width={FORMULA_W} height={FORMULA_H} fill={PAPER} stroke={INK} strokeWidth={1} />
      {CLAUSES.map((clause, ci) => {
        const y = FORMULA_Y + 20 + ci * 50;
        const sat = clauseSat[ci];
        const hasAsn = Object.keys(assignment).length > 0;
        return (
          <g key={`clause-${ci}`}>
            <text x={FORMULA_X + 8} y={y} fontFamily={FONT_FAMILY} fontSize={11} fontWeight={700} fill={INK}>
              C{ci + 1}: ({clause.map(literalToStr).join(" ∨ ")})
            </text>
            {hasAsn && (
              <text x={FORMULA_X + 8} y={y + 14} fontFamily={FONT_FAMILY} fontSize={9} fill={INK} opacity={0.7}>
                = {clause.map((lit) => (evalLit(lit, assignment) ? "T" : "F")).join(" ∨ ")} = {sat ? "T" : "F"}
              </text>
            )}
          </g>
        );
      })}
      {Object.keys(assignment).length > 0 && (
        <text x={FORMULA_X + 8} y={FORMULA_Y + FORMULA_H - 10} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
          α: x1={assignment[1] ? "T" : "F"}, x2={assignment[2] ? "T" : "F"}, x3={assignment[3] ? "T" : "F"}
        </text>
      )}

      {/* Graph pane */}
      <rect x={GRAPH_X} y={GRAPH_Y} width={GRAPH_W} height={GRAPH_H} fill={PAPER} stroke={INK} strokeWidth={1} />
      {/* Cluster outlines */}
      {[0, 1, 2].map((ci) => {
        const c = clusterCenter(ci);
        return (
          <circle
            key={`cluster-${ci}`}
            cx={c.x}
            cy={c.y}
            r={32}
            fill="none"
            stroke={INK}
            strokeWidth={1}
            strokeDasharray="3 3"
            opacity={0.4}
          />
        );
      })}
      {/* Cluster labels */}
      {[0, 1, 2].map((ci) => {
        const c = clusterCenter(ci);
        return (
          <text
            key={`clbl-${ci}`}
            x={c.x}
            y={c.y - 38}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            fill={INK}
            opacity={0.6}
          >
            C{ci + 1}
          </text>
        );
      })}
      {/* Edges */}
      {showEdges &&
        edges.map((e) => {
          const a = nodes.find((n) => n.id === e.from)!;
          const b = nodes.find((n) => n.id === e.to)!;
          const pa = nodePosition(a);
          const pb = nodePosition(b);
          const inCliqueEdge = cliqueNodes.includes(a.id) && cliqueNodes.includes(b.id);
          return (
            <line
              key={`edge-${e.from}-${e.to}`}
              x1={pa.x}
              y1={pa.y}
              x2={pb.x}
              y2={pb.y}
              stroke={inCliqueEdge ? ACCENT : INK}
              strokeWidth={inCliqueEdge ? 2.5 : 0.6}
              opacity={inCliqueEdge ? 1 : 0.3}
            />
          );
        })}
      {/* Nodes */}
      {nodes.map((node) => {
        const pos = nodePosition(node);
        const isClique = cliqueNodes.includes(node.id);
        return (
          <g key={`node-${node.id}`}>
            <circle
              cx={pos.x}
              cy={pos.y}
              r={11}
              fill={isClique ? ACCENT : "#fff"}
              stroke={INK}
              strokeWidth={isClique ? 2 : 1}
            />
            <text
              x={pos.x}
              y={pos.y + 3}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={8}
              fontWeight={700}
              fill={INK}
            >
              {literalToStr(node.literal)}
            </text>
          </g>
        );
      })}

      {/* Phase indicator above message */}
      <text x={FORMULA_X} y={MSG_Y - 16} fontFamily={FONT_FAMILY} fontSize={9} fill={INK} opacity={0.5}>
        Phase: {phase}
      </text>
      {/* Message */}
      <text x={FORMULA_X} y={MSG_Y} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
        {message}
      </text>

      {/* Bounds anchor */}
      <rect x={0} y={0} width={480} height={360} fill="none" stroke="none" />
    </>
  );
}

export function NPGadget(): ReactNode {
  return (
    <AlgoStepperShell<NPState>
      title="PA-7 · 3-SAT → CLIQUE reduction ⭐"
      desc="Bidirectional iff reduction. 3-SAT formula transforms to graph G; φ satisfiable IFF G has k-clique."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="np-gadget"
    />
  );
}
