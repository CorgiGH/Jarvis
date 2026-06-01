import { describe, it, expect } from 'vitest';
import { layoutTreeNodes, buildFibTrace } from '../RecursionTree';
import { assertNoSiblingOverlap } from './trace-match';

describe('RecursionTree layout correctness', () => {
  it('fib(5) full tree has zero sibling overlap', () => {
    const frames = buildFibTrace(5);
    const finalTree = frames[frames.length - 1].state.tree;
    const laid = layoutTreeNodes(finalTree);   // [{id,x,y,depth}]
    // assertNoSiblingOverlap expects id:string; coerce
    const nodes = laid.map(n => ({ ...n, id: String(n.id) }));
    expect(() => assertNoSiblingOverlap(nodes, TREE_NODE_R, 'fib(5)')).not.toThrow();
  });

  it('every non-root node sits strictly below its parent', () => {
    const frames = buildFibTrace(5);
    const finalTree = frames[frames.length - 1].state.tree;
    const laid = layoutTreeNodes(finalTree);
    const byId = new Map(laid.map(n => [n.id, n]));
    for (const n of finalTree) {
      if (n.parentId == null) continue;
      expect(byId.get(n.id)!.y).toBeGreaterThan(byId.get(n.parentId)!.y);
    }
  });
});

// Mirror the constant from RecursionTree so the test is self-describing
const TREE_NODE_R = 14;
