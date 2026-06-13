/**
 * Plan-V family 2 (INV-5.1 / INV-5.2) — an INDEPENDENT selection-sort reference, used by the
 * trace-match harness to recompute the expected per-step rendered state from the instance's SEED.
 * This is the ORACLE: the harness asserts the family's frames equal what this computes, so the
 * family cannot animate a step selection sort does not actually do, and the final array cannot drift
 * from the real sorted output.
 *
 * The input is derived from the instance's `values` (the seed encoded in data_json), NOT a pinned
 * literal, so this generalizes across instances.
 *
 * The canonical step sequence (the family's framesFromSeqArrayData echoes the SAME shape from the
 * instance's `steps`, and the content YAML is authored from this same logic, so all three agree by
 * construction):
 *   For each round i in [0, n-2]:
 *     - one "scan" frame for each j in [i, n-1] (j=i establishes the running min at the round start;
 *       each later j updates `min` if array[j] < array[min]). The array is UNCHANGED during a scan.
 *     - one "swap" frame: the min found by the scan is swapped into position i (array permutes; if
 *       min===i it is a no-op swap but the frame is still emitted so the step count is deterministic).
 *   sortedCount = i during round i's scan, and i+1 on that round's swap frame (position i is now
 *   finalized).
 * The last element falls into place automatically (sorted prefix of n-1 ⇒ whole array sorted), so
 * no round is emitted for i=n-1.
 */

export type SeqArrayRefStep = {
  array: number[];
  sortedCount: number;
  i: number;
  j: number;
  min: number;
  phase: "scan" | "swap";
};

/** Runs REAL selection sort, emitting the per-step expected state (contents + pointers + boundary). */
export function selectionSortReference(input: number[]): SeqArrayRefStep[] {
  const a = [...input];
  const n = a.length;
  const out: SeqArrayRefStep[] = [];

  for (let i = 0; i < n - 1; i++) {
    let min = i;
    // scan the suffix [i..n-1]; j=i is the first beat (establishes the running min).
    for (let j = i; j < n; j++) {
      if (a[j] < a[min]) min = j;
      out.push({ array: [...a], sortedCount: i, i, j, min, phase: "scan" });
    }
    // swap the found minimum into position i (a no-op when min===i, still a deterministic frame).
    const tmp = a[i];
    a[i] = a[min];
    a[min] = tmp;
    out.push({ array: [...a], sortedCount: i + 1, i, j: n - 1, min, phase: "swap" });
  }

  return out;
}

/**
 * Convenience: the final sorted array the reference produces (the final-state trace-match target).
 */
export function selectionSortOutput(input: number[]): number[] {
  return [...input].sort((x, y) => x - y);
}
