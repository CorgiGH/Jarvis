import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { createHash } from 'node:crypto';
import { describe, it, expect } from 'vitest';
import {
  decodePng,
  diffPng,
  classifyPair,
  MEANINGFUL_PX,
} from '../../../../tools/frame-conjunction-core.mjs';
import { MERGE_STEPS, stateDelta } from '../../../../tools/frame-conjunction-seed.mjs';

// Real PNG crops of two genuinely different figure frames. diffPng takes two raw PNG
// buffers (it decodes internally), so we hand it the bytes — but we also decodePng here
// first to PROVE the fixtures are valid PNGs this decoder can read (the gate's decoder).
//
// Anchor the fixture lookup on this file's own location via import.meta.url. Under the
// jsdom test environment Vite's `base: "/tutor/"` makes a relative `new URL(rel, meta.url)`
// resolve against the jsdom document base (an http:// URL), so we go through the file path
// of import.meta.url and join the fixtures dir with node:path — same "relative to this
// file" intent, but immune to jsdom's base-URL hijack.
const HERE = dirname(fileURLToPath(import.meta.url));
const figA = readFileSync(resolve(HERE, 'fixtures/frameConjunction/figA.png'));
const figB = readFileSync(resolve(HERE, 'fixtures/frameConjunction/figB.png'));
// figC: SAME crop size as figA (620×347) but byte-different (merge frame 7 vs frame 6).
// figB has a DIFFERENT crop height (333) so figA-vs-figB hits diffPng's reflow short-circuit
// (size mismatch ⇒ changedPx = w*h+1, a sentinel) and NEVER runs the per-pixel counting loop.
// figC is the same size as figA, so figA-vs-figC takes the per-pixel path — the only fixture
// that proves the counting loop produces a real, meaningful count (not the reflow sentinel).
const figC = readFileSync(resolve(HERE, 'fixtures/frameConjunction/figC.png'));

describe('frame-conjunction self-test (permanent liveness)', () => {
  it('fixtures are valid PNGs the gate decoder can read', () => {
    const a = decodePng(figA);
    const b = decodePng(figB);
    expect(a.width).toBeGreaterThan(0);
    expect(a.height).toBeGreaterThan(0);
    expect(b.width).toBeGreaterThan(0);
    expect(b.height).toBeGreaterThan(0);
  });

  it('LEG frozen=FAIL — a frozen figure over a state-changed pair is a hard fail', () => {
    // The state genuinely moved between step 0 and step 1...
    expect(stateDelta(MERGE_STEPS[0], MERGE_STEPS[1]).changed).toBe(true);
    // ...but the figure pixels did NOT move (figA vs figA ⇒ 0 changed pixels).
    const { changedPx } = diffPng(figA, figA);
    expect(changedPx).toBe(0);
    expect(changedPx).toBeLessThan(MEANINGFUL_PX);
    // state-changed + figure-frozen ⇒ the conjunction must bite.
    expect(classifyPair({ stateChanged: true, changedPx })).toBe('fail');
  });

  it('LEG moved=live — a genuinely moved figure over a state-changed pair is live', () => {
    const { changedPx } = diffPng(figA, figB);
    // Guard: if the two fixtures are too similar the gate is toothless. They must be
    // far apart (>= MEANINGFUL_PX) for the live leg to be a real proof.
    expect(changedPx).toBeGreaterThanOrEqual(MEANINGFUL_PX);
    expect(classifyPair({ stateChanged: true, changedPx })).toBe('live');
  });

  it('LEG per-pixel-count=live — same-size-but-different crop exercises the counting loop', () => {
    // COVERAGE GAP this leg closes: the figA-vs-figB "moved=live" leg compares crops of DIFFERENT
    // heights (347 vs 333), so diffPng takes its REFLOW short-circuit (size mismatch ⇒
    // changedPx = w*h+1, a sentinel) and the per-pixel changed-counting loop is NEVER exercised to
    // produce a meaningful count. The frozen leg (figA vs figA) runs the loop only to return 0.
    // figC is the SAME size as figA (620×347) but byte-different, so this pair takes the per-pixel
    // path and proves the counting loop emits a genuine, meaningful count.
    const a = decodePng(figA);
    const c = decodePng(figC);
    // figC is exactly figA's crop size (the precondition for taking the non-reflow path).
    expect(c.width).toBe(a.width);
    expect(c.height).toBe(a.height);

    const { changedPx, note } = diffPng(figA, figC);
    // (a) NON-reflow path: the note must NOT mention "reflow" (the size-mismatch short-circuit).
    expect(note).not.toContain('reflow');
    // (b) the per-pixel loop counted a meaningful number of changed pixels.
    expect(changedPx).toBeGreaterThanOrEqual(MEANINGFUL_PX);
    // (c) it is a REAL count, not the reflow sentinel: the sentinel is width*height+1, so a genuine
    //     per-pixel count is strictly below width*height. (Confirms the loop ran, not the fallback.)
    expect(changedPx).toBeLessThan(a.width * a.height);
    // (d) state changed + figure moved (via a real per-pixel count) ⇒ the conjunction verdict is live.
    expect(classifyPair({ stateChanged: true, changedPx })).toBe('live');
  });

  it('LEG hold=exempt — a frozen figure is allowed when the state did NOT move', () => {
    // Prove the conjunction: a frozen figure over an UNCHANGED state is a legitimate hold.
    // Search for an adjacent step pair whose typed state did not change...
    let holdPair: [number, number] | null = null;
    for (let i = 0; i < MERGE_STEPS.length - 1; i++) {
      if (!stateDelta(MERGE_STEPS[i], MERGE_STEPS[i + 1]).changed) {
        holdPair = [i, i + 1];
        break;
      }
    }
    if (holdPair) {
      const [i, j] = holdPair;
      expect(stateDelta(MERGE_STEPS[i], MERGE_STEPS[j]).changed).toBe(false);
    } else {
      // The merge seed has no adjacent state-unchanged pair (every step advances the
      // typed state), so a step compared against ITSELF is the canonical unchanged pair.
      expect(stateDelta(MERGE_STEPS[0], MERGE_STEPS[0]).changed).toBe(false);
    }
    // A frozen figure (0 changed pixels) is exempt when state did not move.
    expect(classifyPair({ stateChanged: false, changedPx: 0 })).toBe('hold');
  });

  it('pins the state-delta oracle (changed on a move, unchanged on identity)', () => {
    expect(stateDelta(MERGE_STEPS[0], MERGE_STEPS[1]).changed).toBe(true);
    expect(stateDelta(MERGE_STEPS[0], MERGE_STEPS[0]).changed).toBe(false);
  });

  it('SEED-SYNC smoke — MERGE_STEPS matches the renderer demo step count + content (anti ghost-green)', () => {
    // The viz-pa-mergesort-runs-001 data_json is defined INLINE (not exported) in
    // src/components/viz/families/MergeCompareDemo.tsx + LectieMergeSortDemo.tsx (and
    // mirrored in the shipped content YAML + traceMatchHarness). It is byte-identical to
    // the seed's MERGESORT_DATA_JSON. Since the renderer source is not cleanly importable
    // here, we pin the seed oracle by COUNT and by a CONTENT HASH. The length-only pin was
    // blind to a same-length content edit (council-1781570142 hardening); the hash reds loud
    // on ANY change to MERGE_STEPS. To regenerate after a deliberate seed change:
    //   node -e "import('./tutor-web/tools/frame-conjunction-seed.mjs').then(m=>console.log(require('crypto').createHash('sha256').update(JSON.stringify(m.MERGE_STEPS)).digest('hex')))"
    expect(MERGE_STEPS.length).toBe(30);
    const seedHash = createHash('sha256').update(JSON.stringify(MERGE_STEPS)).digest('hex');
    expect(seedHash).toBe('874f1329ab02564d5b3f564ff6f4f25e902f3184aacf08997a2aab0d6880eb03');
  });
});
