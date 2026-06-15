/**
 * Plan-V family (sort-merge) — an INDEPENDENT merge-sort reference that emits ARRAY-STATE frames,
 * used by the trace-match harness to recompute the expected per-step rendered state from the seed.
 * This is the ORACLE: the harness asserts the family's frames equal what THIS computes, so the family
 * cannot animate a step merge sort does not actually do, and the final array cannot drift from the
 * real sorted output.
 *
 * ── Why a NEW file (NOT mergesortTrace.ts) ─────────────────────────────────────────────────────────
 * `mergesortTrace.ts` serves the LEGACY graph-tree DIVIDE-TREE figure: it emits node-LABEL set frames
 * (`{kind, labels, highlight}`) and is structurally unable to drive an array-SORTING figure — it never
 * emits per-element positions, front pointers, or output state. It stays intact (the tree harness uses
 * it). THIS oracle emits array-STATE frames (mirroring `selectionSortTrace.ts`): the FULL array
 * contents every frame, the active run spans, the front pointers, the output fill count, the finalized
 * span. (Named `arrayMergeTrace` — NOT `mergeSortTrace` — because Windows' case-insensitive filesystem
 * would alias a `mergeSortTrace.ts` onto the existing `mergesortTrace.ts` and silently clobber it.)
 *
 * ── The model (recursive top-down merge sort; frames emitted at the MERGE) ─────────────────────────
 * Merge sort does NOT permute on the way DOWN (divide), so divide is a brief set-up: the `array` is
 * unchanged, only the `runs` grouping shrinks (whole → halves → … → singletons). The INSIGHT is the
 * MERGE: two adjacent sorted runs converge into one sorted run, element by element. Per merge we emit:
 *   - "compare": both run fronts highlighted (frontOf points at each run's head); output unchanged.
 *   - "take" (one per element): the smaller front is written into the next output slot, outFilled++,
 *     the winning run's front advances. `tookFrom` = the array index the token was pulled FROM.
 *   - "drain": once one run empties, each remaining tail element is taken in order (phase="drain").
 * On a merge's completion `array[lo..hi)` holds the sorted run; that span is recorded as `sortedSpan`.
 * The ROOT merge ends on a single "final" frame whose `array` === sorted(seed) — the hard requirement
 * the legacy graph-tree figure fails.
 *
 * ── How `array` encodes the in-progress merge ──────────────────────────────────────────────────────
 * `array` is always a permutation of the seed, length n. During a merge of [lo,hi) we lay the region
 * out as [committed-output-prefix ++ left-tail ++ right-tail]; after every take we re-lay so the
 * un-consumed run tails stay contiguous, and `frontOf[k]` is then the EXACT array index of run k's
 * current head. `values` are distinct (parse enforces it) so value↔seed-index is a clean bijection and
 * the renderer can carry ONE motion node per number across the whole trace (stable token identity).
 */

export type MergeRun = { lo: number; hi: number };

export type ArrayMergeRefStep = {
  /** the FULL array contents this frame (a permutation of the seed). final step === sorted(seed). */
  array: number[];
  /** disjoint half-open spans being merged THIS frame, left→right. e.g. [{lo:0,hi:1},{lo:1,hi:2}]. */
  runs: MergeRun[];
  /** front pointer (an index into `array`) on each active run; runs[k] consumes from frontOf[k]. */
  frontOf: number[];
  /** output band: how many output slots are filled this frame (the locked sorted prefix of the merge). */
  outFilled: number;
  /** which array index was JUST taken into the output this frame (glow/slide highlight); -1 otherwise. */
  tookFrom: number;
  /** the array span FINALIZED-sorted at the END of this frame ([0,0) when nothing finalized this frame). */
  sortedSpan: MergeRun;
  /** the beat that drives layout + callout anchoring. */
  phase: "divide" | "compare" | "take" | "drain" | "final";
};

/**
 * Runs REAL top-down merge sort, emitting the per-step expected state. The seed is mutated in place
 * (`a`) as merges commit, so `a` is always the figure's `array` field for the frame being pushed.
 */
export function arrayMergeReference(input: number[]): ArrayMergeRefStep[] {
  const a = [...input];
  const n = a.length;
  const steps: ArrayMergeRefStep[] = [];

  // ── DIVIDE set-up (brief) — array UNCHANGED; only the grouping shrinks: whole → halves → … →
  //    singletons. One frame per level. No element moves; divide is not the insight. ──
  for (const runs of divideLevels(n)) {
    steps.push({
      array: [...a],
      runs,
      frontOf: runs.map((r) => r.lo),
      outFilled: 0,
      tookFrom: -1,
      sortedSpan: { lo: 0, hi: 0 },
      phase: "divide",
    });
  }

  // ── MERGE (the core) — post-order recursion: sort both halves, then merge them. ──
  function sort(lo: number, hi: number): void {
    if (hi - lo <= 1) return; // a singleton run is already sorted — no merge, no frame
    const mid = lo + Math.floor((hi - lo) / 2);
    sort(lo, mid);
    sort(mid, hi);
    mergeAndEmit(lo, mid, hi);
  }

  function mergeAndEmit(lo: number, mid: number, hi: number): void {
    const left = a.slice(lo, mid); // already sorted (child merge committed it)
    const right = a.slice(mid, hi);
    const isRoot = lo === 0 && hi === n;

    let li = 0; // consumed count from left
    let ri = 0; // consumed count from right
    let outFilled = 0;
    const committed: number[] = [];

    // run spans (as array indices) given the current committed-prefix + consumed counts.
    const runsFull = (): MergeRun[] => {
      const leftRem = left.length - li;
      const rightRem = right.length - ri;
      const leftLo = lo + outFilled;
      const out: MergeRun[] = [];
      if (leftRem > 0) out.push({ lo: leftLo, hi: leftLo + leftRem });
      if (rightRem > 0) {
        const rightLo = leftLo + leftRem;
        out.push({ lo: rightLo, hi: rightLo + rightRem });
      }
      return out;
    };
    const frontsFull = (): number[] => {
      const leftRem = left.length - li;
      const rightRem = right.length - ri;
      const leftLo = lo + outFilled;
      const fronts: number[] = [];
      if (leftRem > 0) fronts.push(leftLo);
      if (rightRem > 0) fronts.push(leftLo + leftRem);
      return fronts;
    };
    // Re-lay a[lo..hi) = [committed ++ left-tail ++ right-tail] so the run tails stay contiguous and
    // the front pointers above are exact array indices.
    const relayout = (): void => {
      const region = [...committed, ...left.slice(li), ...right.slice(ri)];
      for (let k = 0; k < region.length; k++) a[lo + k] = region[k];
    };

    const emitCompare = (): void => {
      steps.push({
        array: [...a],
        runs: runsFull(),
        frontOf: frontsFull(),
        outFilled,
        tookFrom: -1,
        sortedSpan: { lo: 0, hi: 0 },
        phase: "compare",
      });
    };

    const emitTake = (drain: boolean): void => {
      let takeLeft: boolean;
      if (li >= left.length) takeLeft = false;
      else if (ri >= right.length) takeLeft = true;
      else takeLeft = left[li] <= right[ri];
      const taken = takeLeft ? left[li] : right[ri];
      const tookFrom = lo + outFilled; // the chosen run's head always sits at the first tail slot
      if (takeLeft) li++;
      else ri++;
      committed.push(taken);
      outFilled++;
      relayout();
      steps.push({
        array: [...a],
        runs: runsFull(),
        frontOf: frontsFull(),
        outFilled,
        tookFrom,
        sortedSpan: { lo: 0, hi: 0 },
        phase: drain ? "drain" : "take",
      });
    };

    // While BOTH runs have a head: compare, then take the smaller. Then drain the remaining run.
    while (li < left.length && ri < right.length) {
      emitCompare();
      emitTake(false);
    }
    while (li < left.length || ri < right.length) {
      emitTake(true);
    }

    // Merge complete: a[lo..hi) holds the sorted run. Mark the finalized span on the LAST frame; the
    // ROOT merge's last frame is upgraded to "final" with sortedSpan = whole array.
    const lastFrame = steps[steps.length - 1];
    if (isRoot) {
      lastFrame.phase = "final";
      lastFrame.sortedSpan = { lo: 0, hi: n };
    } else {
      lastFrame.sortedSpan = { lo, hi };
    }
  }

  sort(0, n);

  // Defensive: n <= 1 → no merges; still guarantee a final frame whose array is sorted.
  if (steps.length === 0 || steps[steps.length - 1].phase !== "final") {
    steps.push({
      array: [...a],
      runs: [{ lo: 0, hi: Math.max(n, 1) }],
      frontOf: [0],
      outFilled: n,
      tookFrom: -1,
      sortedSpan: { lo: 0, hi: n },
      phase: "final",
    });
  }

  return steps;
}

/**
 * The divide levels for an array of length n: level 0 = the whole array; each next level halves every
 * span (the SAME mid the recursion uses), down to all-singletons. Drives ONLY the brief divide
 * set-up frames (the array does not move during divide). One run-list per level.
 */
function divideLevels(n: number): MergeRun[][] {
  if (n <= 1) return [[{ lo: 0, hi: Math.max(n, 1) }]];
  const levels: MergeRun[][] = [];
  let current: MergeRun[] = [{ lo: 0, hi: n }];
  levels.push(current);
  while (current.some((r) => r.hi - r.lo > 1)) {
    const next: MergeRun[] = [];
    for (const r of current) {
      if (r.hi - r.lo <= 1) {
        next.push(r);
      } else {
        const mid = r.lo + Math.floor((r.hi - r.lo) / 2);
        next.push({ lo: r.lo, hi: mid });
        next.push({ lo: mid, hi: r.hi });
      }
    }
    levels.push(next);
    current = next;
  }
  return levels;
}

/** Convenience: the final sorted array the reference produces (the final-state trace-match target). */
export function arrayMergeOutput(input: number[]): number[] {
  return [...input].sort((x, y) => x - y);
}
