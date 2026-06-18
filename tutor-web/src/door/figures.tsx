import type { ReactNode } from "react";
import type {
  FigureSpec,
  MergeSpec,
  ProbTreeSpec,
  TimelineSpec,
  TreeRowSpec,
  TreeSpec,
} from "./concept";

// Skin-agnostic door figures. Every figure reads the same `--fig-*` CSS vars,
// which each door root maps to its own palette (brutalist → accent/white/mono,
// warm → coral/ink/serif). So one figure renderer works in both skins and any
// theme. Geometry is computed here from the spec — never baked coordinates.

const ACCENT = "var(--fig-accent)";
const ACCENT_INK = "var(--fig-accent-ink)";
const INK = "var(--fig-ink)";
const LINE = "var(--fig-line)";
const RAIL = "var(--fig-rail)";
const NODE_FILL = "var(--fig-node-fill)";
const NODE_INK = "var(--fig-node-ink)";
const MUTED = "var(--fig-muted)";
const FONT = "var(--fig-font)";

function label(
  x: number,
  y: number,
  text: ReactNode,
  size = 10.5,
  fill = MUTED,
  anchor: "start" | "middle" | "end" = "start",
): ReactNode {
  return (
    <text
      x={x}
      y={y}
      textAnchor={anchor}
      style={{ fontFamily: FONT, fontSize: size, fill, letterSpacing: "0.08em" }}
    >
      {text}
    </text>
  );
}

// ── recurrence tree ─────────────────────────────────────────────────────────

function TreeFig({ rows }: TreeSpec): ReactNode {
  const W = 480,
    H = 360;
  const top = 70,
    bottom = H - 70;
  const span = rows.length > 1 ? (bottom - top) / (rows.length - 1) : 0;
  const leftPad = 90,
    rightPad = 36;
  const rowY = (i: number) => top + i * span;
  const nodeX = (count: number, idx: number) =>
    leftPad + ((W - leftPad - rightPad) * (idx + 0.5)) / count;

  const nodes: { x: number; y: number; r: number; label: string; accent: boolean }[] = [];
  const edges: { x1: number; y1: number; x2: number; y2: number }[] = [];
  const rails: { y: number; text: string; work: string }[] = [];

  rows.forEach((row: TreeRowSpec, i) => {
    const y = rowY(i);
    const r = Math.max(14, 26 - i * 4);
    rails.push({ y: y - r - 18, text: `ROW ${i}`, work: row.work });
    for (let k = 0; k < row.nodes; k++) {
      const x = nodeX(row.nodes, k);
      nodes.push({ x, y, r, label: row.nodeLabel, accent: i === 0 });
      const next = rows[i + 1];
      if (next) {
        const ny = rowY(i + 1);
        [2 * k, 2 * k + 1].forEach((ci) => {
          if (ci < next.nodes)
            edges.push({ x1: x, y1: y, x2: nodeX(next.nodes, ci), y2: ny });
        });
      }
    }
  });

  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" data-testid="door-figure" style={fig}>
      {rails.map((r, i) => (
        <line key={`rl${i}`} x1={leftPad - 6} y1={r.y} x2={W - 20} y2={r.y} stroke={RAIL} strokeWidth={1} strokeDasharray="2 5" />
      ))}
      {edges.map((e, i) => (
        <line key={`e${i}`} x1={e.x1} y1={e.y1} x2={e.x2} y2={e.y2} stroke={LINE} strokeWidth={1.5} />
      ))}
      {nodes.map((n, i) => (
        <g key={`n${i}`}>
          <circle cx={n.x} cy={n.y} r={n.r} fill={n.accent ? ACCENT : NODE_FILL} stroke={n.accent ? ACCENT : INK} strokeWidth={1.6} />
          <text x={n.x} y={n.y} textAnchor="middle" dominantBaseline="central" style={{ fontFamily: FONT, fontWeight: 700, fontSize: n.r > 20 ? 15 : 12.5, fill: n.accent ? ACCENT_INK : NODE_INK }}>
            {n.label}
          </text>
        </g>
      ))}
      {rails.map((r, i) => (
        <text key={`rlab${i}`} x={14} y={r.y + 3} style={{ fontFamily: FONT, fontSize: 10.5, fill: MUTED, letterSpacing: "0.1em" }}>
          {r.text} · <tspan fill={ACCENT}>{r.work}</tspan>
        </text>
      ))}
    </svg>
  );
}

// ── merge (two sorted runs → one) ─────────────────────────────────────────────

function MergeFig({ left, right, leftConsumed, rightConsumed, output }: MergeSpec): ReactNode {
  const W = 480,
    H = 300;
  const cw = 46,
    ch = 40,
    gap = 6;
  const runW = (arr: number[]) => arr.length * cw + (arr.length - 1) * gap;
  const leftX0 = 40,
    rightX0 = W - 40 - runW(right);
  const runY = 74;
  const outW = (left.length + right.length) * cw + (left.length + right.length - 1) * gap;
  const outX0 = (W - outW) / 2,
    outY = 212;
  const cellX = (x0: number, i: number) => x0 + i * (cw + gap);
  const headLeftX = cellX(leftX0, leftConsumed) + cw / 2;
  const headRightX = cellX(rightX0, rightConsumed) + cw / 2;
  const nextSlotX = cellX(outX0, output.length) + cw / 2;

  const cell = (
    x: number,
    y: number,
    val: string,
    state: "head" | "done" | "idle" | "slot",
  ): ReactNode => (
    <g>
      <rect
        x={x}
        y={y}
        width={cw}
        height={ch}
        fill={state === "head" ? ACCENT : state === "done" ? NODE_FILL : "none"}
        stroke={state === "slot" ? ACCENT : state === "idle" || state === "done" ? INK : ACCENT}
        strokeWidth={state === "head" || state === "slot" ? 2 : 1.4}
        strokeDasharray={state === "slot" ? "3 3" : undefined}
        opacity={state === "done" ? 0.45 : 1}
      />
      {val !== "" && (
        <text x={x + cw / 2} y={y + ch / 2} textAnchor="middle" dominantBaseline="central" style={{ fontFamily: FONT, fontWeight: 700, fontSize: 16, fill: state === "head" ? ACCENT_INK : state === "done" ? MUTED : INK }}>
          {val}
        </text>
      )}
    </g>
  );

  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" data-testid="door-figure" style={fig}>
      {/* compare lines from the two heads to the next output slot */}
      <line x1={headLeftX} y1={runY + ch} x2={nextSlotX} y2={outY} stroke={ACCENT} strokeWidth={1.4} strokeDasharray="3 4" />
      <line x1={headRightX} y1={runY + ch} x2={nextSlotX} y2={outY} stroke={ACCENT} strokeWidth={1.4} strokeDasharray="3 4" />

      {label(leftX0, runY - 12, "LEFT RUN")}
      {label(rightX0, runY - 12, "RIGHT RUN")}
      {label(outX0, outY - 12, "MERGED")}

      {left.map((v, i) =>
        <g key={`l${i}`}>{cell(cellX(leftX0, i), runY, String(v), i < leftConsumed ? "done" : i === leftConsumed ? "head" : "idle")}</g>,
      )}
      {right.map((v, i) =>
        <g key={`r${i}`}>{cell(cellX(rightX0, i), runY, String(v), i < rightConsumed ? "done" : i === rightConsumed ? "head" : "idle")}</g>,
      )}
      {Array.from({ length: left.length + right.length }, (_, i) =>
        <g key={`o${i}`}>{cell(cellX(outX0, i), outY, i < output.length ? String(output[i]) : "", i < output.length ? "idle" : i === output.length ? "slot" : "idle")}</g>,
      )}
    </svg>
  );
}

// ── round-robin timeline ──────────────────────────────────────────────────────

function TimelineFig({ slices, current, axisLabel }: TimelineSpec): ReactNode {
  const W = 480,
    H = 210;
  const x0 = 40,
    x1 = 440,
    y = 76,
    h = 58;
  const sw = (x1 - x0) / slices.length;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" data-testid="door-figure" style={fig}>
      {slices.map((s, i) => {
        const x = x0 + i * sw;
        const hot = i === current;
        return (
          <g key={`s${i}`}>
            <rect x={x} y={y} width={sw} height={h} fill={hot ? ACCENT : NODE_FILL} stroke={hot ? ACCENT : INK} strokeWidth={hot ? 2 : 1.4} />
            <text x={x + sw / 2} y={y + h / 2} textAnchor="middle" dominantBaseline="central" style={{ fontFamily: FONT, fontWeight: 700, fontSize: 16, fill: hot ? ACCENT_INK : NODE_INK }}>
              {s}
            </text>
          </g>
        );
      })}
      {/* tick axis */}
      <line x1={x0} y1={y + h + 12} x2={x1} y2={y + h + 12} stroke={LINE} strokeWidth={1.4} />
      {Array.from({ length: slices.length + 1 }, (_, i) => {
        const x = x0 + i * sw;
        return (
          <g key={`t${i}`}>
            <line x1={x} y1={y + h + 7} x2={x} y2={y + h + 17} stroke={LINE} strokeWidth={1.2} />
            <text x={x} y={y + h + 32} textAnchor="middle" style={{ fontFamily: FONT, fontSize: 11, fill: MUTED }}>{i}</text>
          </g>
        );
      })}
      {label(x1, y - 14, axisLabel, 10.5, MUTED, "end")}
      {label(x0, y - 14, "QUANTA", 10.5, MUTED, "start")}
    </svg>
  );
}

// ── Bayes probability tree ────────────────────────────────────────────────────

function ProbTreeFig({ rootLabel, branches }: ProbTreeSpec): ReactNode {
  const W = 480,
    H = 320;
  const rootC = { x: W / 2, y: 44 };
  const branchY = 150;
  const leafY = 264;
  const bx = branches.map((_, i) => 120 + i * 240); // 120, 360
  const leafX = (bi: number, ci: number) => bx[bi] + (ci === 0 ? -64 : 64);

  const node = (x: number, y: number, w: number, hh: number, text: string, hot: boolean): ReactNode => (
    <g>
      <rect x={x - w / 2} y={y - hh / 2} width={w} height={hh} fill={hot ? ACCENT : NODE_FILL} stroke={hot ? ACCENT : INK} strokeWidth={hot ? 2 : 1.4} />
      <text x={x} y={y} textAnchor="middle" dominantBaseline="central" style={{ fontFamily: FONT, fontWeight: 700, fontSize: 14, fill: hot ? ACCENT_INK : NODE_INK }}>{text}</text>
    </g>
  );

  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" data-testid="door-figure" style={fig}>
      {/* edges + edge labels (priors / conditionals) */}
      {branches.map((b, bi) => (
        <g key={`be${bi}`}>
          <line x1={rootC.x} y1={rootC.y + 16} x2={bx[bi]} y2={branchY - 16} stroke={LINE} strokeWidth={1.5} />
          {label((rootC.x + bx[bi]) / 2 + (bi === 0 ? -12 : 12), (rootC.y + branchY) / 2, b.p, 11, MUTED, bi === 0 ? "end" : "start")}
          {b.children.map((c, ci) => (
            <g key={`le${ci}`}>
              <line x1={bx[bi]} y1={branchY + 16} x2={leafX(bi, ci)} y2={leafY - 15} stroke={LINE} strokeWidth={1.5} />
              {label((bx[bi] + leafX(bi, ci)) / 2 + (ci === 0 ? -10 : 10), (branchY + leafY) / 2, c.p, 10, MUTED, ci === 0 ? "end" : "start")}
            </g>
          ))}
        </g>
      ))}
      {/* root */}
      {node(rootC.x, rootC.y, 58, 30, rootLabel, false)}
      {/* branches + leaves */}
      {branches.map((b, bi) => (
        <g key={`bn${bi}`}>
          {node(bx[bi], branchY, 78, 34, b.label, false)}
          {b.children.map((c, ci) => (
            <g key={`ln${ci}`}>
              <circle cx={leafX(bi, ci)} cy={leafY} r={16} fill={c.hot ? ACCENT : NODE_FILL} stroke={c.hot ? ACCENT : INK} strokeWidth={c.hot ? 2 : 1.4} />
              <text x={leafX(bi, ci)} y={leafY} textAnchor="middle" dominantBaseline="central" style={{ fontFamily: FONT, fontWeight: 700, fontSize: 15, fill: c.hot ? ACCENT_INK : NODE_INK }}>{c.label}</text>
            </g>
          ))}
        </g>
      ))}
    </svg>
  );
}

const fig: React.CSSProperties = { width: "100%", height: "auto", display: "block" };

export function Figure({ spec }: { spec: FigureSpec }): ReactNode {
  switch (spec.kind) {
    case "tree":
      return <TreeFig {...spec} />;
    case "merge":
      return <MergeFig {...spec} />;
    case "timeline":
      return <TimelineFig {...spec} />;
    case "prob-tree":
      return <ProbTreeFig {...spec} />;
  }
}
