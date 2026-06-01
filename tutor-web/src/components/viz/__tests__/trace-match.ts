// Pedagogical-correctness harness (playbook §3.4 + §3.6): assert a viz's frames
// match a reference trace AND obey spatial invariants. A viz must export its
// frame builder for this to work (see each fix task).

/** Assert two ordered traces are element-wise deep-equal; pinpoint the first divergence. */
export function assertTraceMatches<T>(actual: T[], reference: T[], label: string): void {
  if (actual.length !== reference.length)
    throw new Error(`${label}: trace length ${actual.length} ≠ reference ${reference.length}`);
  for (let i = 0; i < reference.length; i++) {
    const a = JSON.stringify(actual[i]), r = JSON.stringify(reference[i]);
    if (a !== r) throw new Error(`${label}: frame ${i} diverges\n  actual:    ${a}\n  reference: ${r}`);
  }
}

/** No two laid-out nodes at the same depth overlap horizontally (min gap = 2*r). */
export function assertNoSiblingOverlap(
  nodes: { id: string; x: number; y: number; depth: number }[], r: number, label: string,
): void {
  const byDepth = new Map<number, { id: string; x: number }[]>();
  for (const n of nodes) {
    const row = byDepth.get(n.depth) ?? [];
    row.push({ id: n.id, x: n.x });
    byDepth.set(n.depth, row);
  }
  for (const [depth, row] of byDepth) {
    const sorted = [...row].sort((a, b) => a.x - b.x);
    for (let i = 1; i < sorted.length; i++)
      if (sorted[i].x - sorted[i - 1].x < 2 * r)
        throw new Error(`${label}: nodes ${sorted[i - 1].id},${sorted[i].id} overlap at depth ${depth} (gap ${(sorted[i].x - sorted[i - 1].x).toFixed(1)} < ${2 * r})`);
  }
}
