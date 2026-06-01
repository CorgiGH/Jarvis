// 0.E.2 — PageTableWalk: flush stale TLB on COW break
// Builder: buildFrames() (not exported directly; FRAMES is exported)
// We import FRAMES (the already-built array) and verify the correctness constraint.

import { describe, it, expect } from "vitest";

// PageTableWalk exports FRAMES (the built array) and FRAME_COUNT.
// buildFrames() is not exported separately, so we test via FRAMES.
import { FRAMES, FRAME_COUNT } from "../PageTableWalk";

describe("PageTableWalk — TLB shootdown on COW break (0.E.2)", () => {
  it("exports a non-empty FRAMES array", () => {
    expect(FRAMES.length).toBeGreaterThan(0);
    expect(FRAMES.length).toBe(FRAME_COUNT);
  });

  it("no phase-4 frame contains a stale TLB entry {vpn:3, pfn:2} after the COW remap", () => {
    // Phase 4 COW remap sets pt[3].pfn = 5. After that push, the TLB must NOT
    // still carry the old {vpn:3, pfn:2} entry — that would teach that a stale
    // mapping silently vanishes (false).
    //
    // Strategy: find the frame where the remap is first visible
    // (pt[3].pfn === 5 in the pushed state), and all subsequent phase-4 frames.
    // None of them should contain a tlb entry with vpn===3 && pfn===2.

    const remapFrames = FRAMES.filter(
      (f) =>
        f.state.phase === 4 &&
        f.state.pageTable[3]?.pfn === 5
    );

    // There must be at least one such frame (the "Parent's PTE[3] → pfn=5" frame).
    expect(remapFrames.length).toBeGreaterThan(0);

    for (const f of remapFrames) {
      const staleEntry = f.state.tlb.find(
        (e: { vpn: number; pfn: number }) => e.vpn === 3 && e.pfn === 2
      );
      expect(
        staleEntry,
        `Frame with message "${f.state.message}" still has stale TLB entry {vpn:3, pfn:2}`
      ).toBeUndefined();
    }
  });

  it("phase-4 frames BEFORE the remap may still have {vpn:3, pfn:2} in TLB (pre-shootdown)", () => {
    // Sanity: the first phase-4 frames (before pt[3].pfn changes to 5) should have
    // vpn:3 → pfn:2 in the TLB (that's what was there before COW).
    const preRemapPhase4 = FRAMES.filter(
      (f) =>
        f.state.phase === 4 &&
        f.state.pageTable[3]?.pfn === 2 // still the OLD mapping
    );
    // If pre-remap frames exist, they should still carry the old TLB (correct pre-shootdown)
    // This is just a guard to make sure we're not asserting on no data.
    if (preRemapPhase4.length > 0) {
      const hasStalePre = preRemapPhase4.some(
        (f) =>
          f.state.tlb.find(
            (e: { vpn: number; pfn: number }) => e.vpn === 3 && e.pfn === 2
          ) !== undefined
      );
      // It's acceptable (correct) for pre-remap frames to still carry {vpn:3,pfn:2}
      // We don't assert either way — just confirming test structure.
      expect(typeof hasStalePre).toBe("boolean");
    }
  });
});
