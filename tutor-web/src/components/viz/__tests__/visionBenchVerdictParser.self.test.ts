/**
 * visionBenchVerdictParser.self.test.ts — pure unit tests for the vision-judge verdict parser
 * (spec §3.0 #7, T10). Mirrors frameConjunction.self.test.ts: NO browser, NO `claude` subprocess —
 * the parser is a pure stdout→{verdict,checks,raw} function, so every contract rule is provable here.
 *
 * Lives under src/ because vitest's include glob is src/**\/*.{test,spec}.{ts,tsx} (vite.config.ts).
 * Imports the .mjs parser the same way frameConjunction.self.test.ts imports frame-conjunction-core.mjs.
 */
import { describe, it, expect } from 'vitest';
import { parseVerdict, reduceMajority } from '../../../../tools/vision-bench/verdict-parser.mjs';

describe('vision-bench verdict-parser (pure)', () => {
  it('clean PASS — VERDICT PASS with all CHECKs PASS', () => {
    const out = [
      'VERDICT: PASS',
      'CHECK axes-present: PASS — both x and y axes drawn, y-ticks at 0 and max',
      'CHECK curve-monotone: PASS — single left-to-right curve, no duplicate points',
    ].join('\n');
    const r = parseVerdict(out);
    expect(r.verdict).toBe('PASS');
    expect(r.checks).toHaveLength(2);
    expect(r.checks.every((c) => c.status === 'PASS')).toBe(true);
  });

  it('clean FAIL — VERDICT FAIL', () => {
    const out = [
      'VERDICT: FAIL',
      'CHECK shade-on-baseline: FAIL — shaded region floats ~40px above the x-axis baseline',
    ].join('\n');
    const r = parseVerdict(out);
    expect(r.verdict).toBe('FAIL');
    expect(r.checks[0].status).toBe('FAIL');
  });

  it('LOAD-BEARING — any CHECK FAIL downgrades a model-written VERDICT PASS to FAIL', () => {
    // The model contradicted itself: headline PASS but a CHECK reads FAIL. The "any-CHECK-FAIL ⇒
    // FAIL" rule is enforced in the parser, never trusted from the LLM. A single failed
    // positive-spec check must fail the figure.
    const out = [
      'VERDICT: PASS',
      'CHECK values-distinct: PASS — all visible digits 5,2,8,1,9 distinct',
      'CHECK active-cell-single: FAIL — two cells carry the accent highlight, expected exactly one',
    ].join('\n');
    const r = parseVerdict(out);
    expect(r.verdict).toBe('FAIL');
    expect(r.checks.find((c) => c.status === 'FAIL')).toBeTruthy();
  });

  it('UNPARSEABLE is DISTINCT from FAIL — no VERDICT line at all', () => {
    const out = [
      "I cannot open the image — the file path was not accessible to my Read tool.",
      'Sorry, I am unable to render a verdict.',
    ].join('\n');
    const r = parseVerdict(out);
    expect(r.verdict).toBe('UNPARSEABLE');
    expect(r.verdict).not.toBe('FAIL');
  });

  it('UNPARSEABLE even when CHECK lines exist but the headline VERDICT is missing', () => {
    // A reply missing its headline verdict is malformed against the contract — abstain, don't guess.
    const out = [
      'CHECK axes-present: PASS — axes drawn',
      'CHECK curve-present: PASS — curve drawn',
    ].join('\n');
    const r = parseVerdict(out);
    expect(r.verdict).toBe('UNPARSEABLE');
    expect(r.checks).toHaveLength(2); // checks still captured, but no headline ⇒ abstain
  });

  it('empty / whitespace / null stdout ⇒ UNPARSEABLE (never throws)', () => {
    expect(parseVerdict('').verdict).toBe('UNPARSEABLE');
    expect(parseVerdict('   \n  \n').verdict).toBe('UNPARSEABLE');
    expect(parseVerdict(undefined as unknown as string).verdict).toBe('UNPARSEABLE');
    expect(parseVerdict(null as unknown as string).verdict).toBe('UNPARSEABLE');
  });

  it('tolerates markdown/bold/bullet noise around the contract lines', () => {
    const out = [
      "I'll open and analyze the figure now.",
      '```',
      '**VERDICT:** FAIL',
      '- **CHECK 1** - PASS: axes present',
      '* CHECK 2 — FAIL : the right-most box is clipped at the viewport edge',
      '```',
    ].join('\n');
    const r = parseVerdict(out);
    expect(r.verdict).toBe('FAIL');
    expect(r.checks).toHaveLength(2);
    expect(r.checks[1].status).toBe('FAIL');
  });

  it('first VERDICT line wins when the model repeats itself', () => {
    const out = ['VERDICT: FAIL', 'some prose', 'VERDICT: PASS'].join('\n');
    // first headline is FAIL; later restatement does not override (and no FAIL checks needed)
    expect(parseVerdict(out).verdict).toBe('FAIL');
  });

  it('case-insensitive verdict + check tokens', () => {
    const out = ['verdict = pass', 'check foo: pass — ok'].join('\n');
    expect(parseVerdict(out).verdict).toBe('PASS');
  });
});

describe('vision-bench reduceMajority (pure, K-runs §6.3)', () => {
  it('K=3 unanimous PASS ⇒ PASS, unanimous=true', () => {
    const r = reduceMajority([{ verdict: 'PASS' }, { verdict: 'PASS' }, { verdict: 'PASS' }]);
    expect(r.decision).toBe('PASS');
    expect(r.kPass).toBe(3);
    expect(r.unanimous).toBe(true);
  });

  it('K=3 majority FAIL (2 FAIL, 1 PASS) ⇒ FAIL, not unanimous', () => {
    const r = reduceMajority([{ verdict: 'FAIL' }, { verdict: 'PASS' }, { verdict: 'FAIL' }]);
    expect(r.decision).toBe('FAIL');
    expect(r.kFail).toBe(2);
    expect(r.kPass).toBe(1);
    expect(r.unanimous).toBe(false);
  });

  it('UNPARSEABLE counts as a FAIL vote (conservative) and stays in the denominator', () => {
    // 2 PASS + 1 UNPARSEABLE ⇒ PASS still wins (2 vs 1). 1 PASS + 2 UNPARSEABLE ⇒ FAIL.
    expect(reduceMajority([{ verdict: 'PASS' }, { verdict: 'PASS' }, { verdict: 'UNPARSEABLE' }]).decision).toBe('PASS');
    const r = reduceMajority([{ verdict: 'PASS' }, { verdict: 'UNPARSEABLE' }, { verdict: 'UNPARSEABLE' }]);
    expect(r.decision).toBe('FAIL');
    expect(r.kUnparseable).toBe(2);
    expect(r.failVotes).toBe(2); // 0 real FAIL + 2 UNPARSEABLE
  });

  it('tie (even K) ⇒ FAIL (safety-biased, same direction as UNPARSEABLE rule)', () => {
    expect(reduceMajority([{ verdict: 'PASS' }, { verdict: 'FAIL' }]).decision).toBe('FAIL');
  });

  it('empty input ⇒ FAIL, not unanimous (no votes ⇒ failVotes>=passVotes)', () => {
    const r = reduceMajority([]);
    expect(r.decision).toBe('FAIL');
    expect(r.unanimous).toBe(false);
  });
});
