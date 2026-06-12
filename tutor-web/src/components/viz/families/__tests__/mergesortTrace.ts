/**
 * Plan-3 Task 8 (INV-5.1 / INV-5.2) — an INDEPENDENT mergesort reference, used by the
 * trace-match test to recompute the expected per-step rendered state from the raw input
 * array. This is the ORACLE: the test asserts the family's frames equal what this computes,
 * so the family cannot animate something mergesort does not actually do.
 *
 * The model mirrors the demo's divide-then-merge sweep (lectie.html:167-173):
 *   - DIVIDE level d: every leaf value sits under its depth-min(d, leafDepth) ancestor,
 *     shown sorted iff leafDepth <= d.
 *   - PAUSE: same as DIVIDE at maxDepth (the "count the levels" beat).
 *   - MERGE level m: every value sits under its depth-min(m, leafDepth) ancestor, in SORTED
 *     order, sorted-styled.
 * A "node label" in the rendered figure is the space-joined values currently grouped at a
 * tree node; the family renders one group-box per occupied node. The reference returns, per
 * step, the set of node labels (sorted concat strings) and which are highlighted.
 */
export type RefStep = { kind: "divide" | "pause" | "merge" | "final"; labels: string[]; highlight: string[] };

function buildTree(a: number[]): { cells: number[]; sorted: number[]; children?: [any, any]; depth: number }[] {
  // flat list of nodes with depth; mirrors bld() in lectie.html:149
  const out: any[] = [];
  function bld(arr: number[], depth: number): any {
    const node: any = { cells: arr.slice(), sorted: arr.slice().sort((x, y) => x - y), depth };
    out.push(node);
    if (arr.length > 1) {
      const m = Math.floor(arr.length / 2);
      node.children = [bld(arr.slice(0, m), depth + 1), bld(arr.slice(m), depth + 1)];
    }
    return node;
  }
  bld(a, 0);
  return out;
}

/** All distinct groupings (node labels) present at a given divide/merge level. */
function groupsAtLevel(nodes: any[], level: number, sorted: boolean): string[] {
  const seen = new Set<string>();
  for (const n of nodes) {
    if (n.depth !== Math.min(level, n.depth)) continue;
    // a node is "occupied" at this level iff it is at exactly depth==min(level, its depth)
    if (n.depth !== level && n.children) continue; // only the cut frontier shows
    const vals = sorted ? n.sorted : n.cells;
    seen.add(vals.join(" "));
  }
  return [...seen];
}

export function mergesortReference(input: number[]): RefStep[] {
  const nodes = buildTree(input);
  const maxDepth = Math.max(...nodes.map((n) => n.depth));
  const steps: RefStep[] = [];
  // DIVIDE d=0..maxDepth (4 steps for [5,2,8,1,9,3], maxDepth=3).
  for (let d = 0; d <= maxDepth; d++) {
    const labels = frontierLabels(nodes, d, false);
    steps.push({ kind: "divide", labels, highlight: labels });
  }
  // PAUSE (count the levels) — same frontier as the deepest divide (1 step).
  steps.push({ kind: "pause", labels: frontierLabels(nodes, maxDepth, false), highlight: frontierLabels(nodes, maxDepth, false) });
  // MERGE m=maxDepth-1 .. 1 (2 steps for maxDepth=3: m=2, m=1). The m=0 ROOT merge is the FINAL
  // step below — NOT a separate merge — so the total is 8, matching the SHIPPED YAML (Step-6
  // reconciliation / PM-locked count). Emitting m=0 here too would re-create the redundant
  // 9th step that drifted from the YAML (consistency#3).
  for (let m = maxDepth - 1; m >= 1; m--) {
    const labels = frontierLabels(nodes, m, true);
    steps.push({ kind: "merge", labels, highlight: labels });
  }
  // FINAL = the m=0 root merge (the whole array, sorted). 4 + 1 + 2 + 1 = 8 steps total.
  const finalLabels = frontierLabels(nodes, 0, true);
  steps.push({ kind: "final", labels: finalLabels, highlight: finalLabels });
  return steps;
}

/** The visible frontier at a level: each leaf collapses to its depth-min(level, leafDepth) ancestor. */
function frontierLabels(nodes: any[], level: number, sorted: boolean): string[] {
  const leaves = nodes.filter((n) => !n.children);
  const seen = new Set<string>();
  const ordered: string[] = [];
  for (const leaf of leaves) {
    // walk up to the ancestor at depth == min(level, leaf.depth)
    let anc = leaf;
    while (anc.depth > Math.min(level, leaf.depth)) anc = parentOf(nodes, anc);
    const key = (sorted ? anc.sorted : anc.cells).join(" ");
    if (!seen.has(key)) { seen.add(key); ordered.push(key); }
  }
  return ordered;
}

function parentOf(nodes: any[], child: any): any {
  return nodes.find((n) => n.children && (n.children[0] === child || n.children[1] === child));
}
