import { describe, it, expect } from 'vitest';
import { assertTraceMatches, assertNoSiblingOverlap } from './trace-match';

describe('assertTraceMatches', () => {
  it('passes on equal arrays', () => {
    assertTraceMatches([{ a: 1 }, { b: 2 }], [{ a: 1 }, { b: 2 }], 'test');
  });

  it('throws on a divergent element', () => {
    expect(() =>
      assertTraceMatches([{ a: 1 }, { b: 99 }], [{ a: 1 }, { b: 2 }], 'label')
    ).toThrow('label: frame 1 diverges');
  });

  it('throws on length mismatch', () => {
    expect(() =>
      assertTraceMatches([{ a: 1 }], [{ a: 1 }, { b: 2 }], 'label')
    ).toThrow('label: trace length 1 ≠ reference 2');
  });
});

describe('assertNoSiblingOverlap', () => {
  it('passes on well-spaced nodes', () => {
    assertNoSiblingOverlap(
      [
        { id: 'a', x: 0,  y: 0, depth: 0 },
        { id: 'b', x: 50, y: 0, depth: 0 },
        { id: 'c', x: 25, y: 80, depth: 1 },
      ],
      20,
      'tree',
    );
  });

  it('throws when two nodes at the same depth are closer than 2*r', () => {
    expect(() =>
      assertNoSiblingOverlap(
        [
          { id: 'a', x: 0,  y: 0, depth: 0 },
          { id: 'b', x: 30, y: 0, depth: 0 }, // gap=30 < 2*20=40
        ],
        20,
        'tree',
      )
    ).toThrow('tree: nodes a,b overlap at depth 0');
  });
});
