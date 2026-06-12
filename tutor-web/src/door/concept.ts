// Page-aware concept-door data model.
//
// A door is driven entirely by a DoorConcept. The `figure` is a discriminated
// union: swap the concept and the door renders a DIFFERENT diagram (tree, merge,
// timeline, probability tree…), not the same shape recolored. Geometry is
// computed at render time in figures.tsx — nothing here is baked coordinates.

// ── figure specs ──────────────────────────────────────────────────────────

export interface TreeRowSpec {
  nodes: number; // 1, 2, 4, … (binary recurrence)
  nodeLabel: string; // label inside every node on this row
  work: string; // work across the whole row, e.g. "n"
}
export interface TreeSpec {
  kind: "tree";
  rows: TreeRowSpec[];
}

export interface MergeSpec {
  kind: "merge";
  left: number[];
  right: number[];
  leftConsumed: number; // how many of each run already merged
  rightConsumed: number;
  output: number[]; // values already placed in the merged run
}

export interface TimelineSpec {
  kind: "timeline";
  slices: string[]; // ordered slice labels, e.g. P1 P2 P3 P1 P2
  current: number; // index of the active slice (accent)
  axisLabel: string;
}

export interface ProbLeaf {
  label: string;
  p: string;
  hot?: boolean; // highlighted leaf
}
export interface ProbBranch {
  label: string;
  p: string;
  children: ProbLeaf[];
}
export interface ProbTreeSpec {
  kind: "prob-tree";
  rootLabel: string;
  branches: ProbBranch[];
}

export type FigureSpec = TreeSpec | MergeSpec | TimelineSpec | ProbTreeSpec;

// ── the door concept ────────────────────────────────────────────────────────

export interface DoorConcept {
  id: string;
  subject: string;
  track: string;
  familiarity: string;
  progress: { current: number; total: number };
  titleTop: string;
  titleAccent: string;
  equation: string;
  gistLead: string; // full sentence; `gistVerb` inside it is highlighted
  gistVerb: string;
  figureLabel: string;
  figure: FigureSpec;
  caption: string;
  footer: string[];
}

// ── concepts (grounded in the doors-lab M1 variants) ────────────────────────

export const RECURRENCE: DoorConcept = {
  id: "recurrence",
  subject: "PA",
  track: "Divide & Conquer",
  familiarity: "First time",
  progress: { current: 4, total: 9 },
  titleTop: "Recurrence",
  titleAccent: "Relations",
  equation: "T(n) = 2T(n/2) + n",
  gistLead:
    "A function that calls itself — the cost of a problem that splits in two. We solve it by gently unrolling it, one level at a time.",
  gistVerb: "unrolling",
  figureLabel: "one level, then two, then four",
  figure: {
    kind: "tree",
    rows: [
      { nodes: 1, nodeLabel: "n", work: "n" },
      { nodes: 2, nodeLabel: "n/2", work: "n" },
      { nodes: 4, nodeLabel: "n/4", work: "n" },
    ],
  },
  caption:
    "Each row of the tree does n work; there are log₂n rows. Unroll, sum the rows — that is the whole trick.",
  footer: ["Master Method", "Case 2", "Θ(n log n)"],
};

export const MERGESORT: DoorConcept = {
  id: "mergesort",
  subject: "PA",
  track: "Divide & Conquer",
  familiarity: "First time",
  progress: { current: 5, total: 9 },
  titleTop: "Merge",
  titleAccent: "Sort",
  equation: "take min(headₗ, headᵣ)",
  gistLead:
    "Two already-sorted runs become one. Keep a finger on each head and always take the smaller — that is merging.",
  gistVerb: "merging",
  figureLabel: "two runs, one output",
  figure: {
    kind: "merge",
    left: [2, 5, 8],
    right: [1, 4, 9],
    leftConsumed: 1,
    rightConsumed: 1,
    output: [1, 2],
  },
  caption:
    "Compare the two heads, take the smaller, advance that finger. Every element is touched once — Θ(n) per level.",
  footer: ["Stable", "Θ(n log n)", "extra O(n)"],
};

export const ROUNDROBIN: DoorConcept = {
  id: "roundrobin",
  subject: "OS",
  track: "Scheduling",
  familiarity: "First time",
  progress: { current: 3, total: 8 },
  titleTop: "Round",
  titleAccent: "Robin",
  equation: "run q, then rotate",
  gistLead:
    "Everyone gets an equal slice, then goes to the back of the line — we just rotate. No one waits forever.",
  gistVerb: "rotate",
  figureLabel: "equal slices along time",
  figure: {
    kind: "timeline",
    slices: ["P1", "P2", "P3", "P1", "P2"],
    current: 2,
    axisLabel: "TIME →",
  },
  caption:
    "Each process runs for one quantum, then yields to the next. Short jobs finish fast; long ones come back around.",
  footer: ["Quantum q", "Preemptive", "Fair share"],
};

export const BAYES: DoorConcept = {
  id: "bayes",
  subject: "ML",
  track: "Probability",
  familiarity: "First time",
  progress: { current: 2, total: 9 },
  titleTop: "Bayes’",
  titleAccent: "Theorem",
  equation: "P(A|B) = P(B|A)·P(A) / P(B)",
  gistLead:
    "A positive test rarely means sick. Split the crowd by the truth, then by the test — and watch where the positives really come from.",
  gistVerb: "Split",
  figureLabel: "prior, then the test",
  figure: {
    kind: "prob-tree",
    rootLabel: "100%",
    branches: [
      {
        label: "Sick",
        p: "1%",
        children: [
          { label: "+", p: "99%", hot: true },
          { label: "−", p: "1%" },
        ],
      },
      {
        label: "Healthy",
        p: "99%",
        children: [
          { label: "+", p: "5%", hot: true },
          { label: "−", p: "95%" },
        ],
      },
    ],
  },
  caption:
    "1% are sick. But 5% of the 99% healthy also test positive — so most “+” results are false alarms.",
  footer: ["Prior 1%", "FPR 5%", "P(sick|+) ≈ 17%"],
};

export const CONCEPTS: DoorConcept[] = [
  RECURRENCE,
  MERGESORT,
  ROUNDROBIN,
  BAYES,
];

export function getConcept(id: string | null): DoorConcept {
  return CONCEPTS.find((c) => c.id === id) ?? RECURRENCE;
}
